#!/usr/bin/env python
"""
Script to periodically read gas concentration measurements from sensors and
optionally upload them to a Web service.
"""
import argparse
import logging
import sys
import time

import requests
import spidev

from config import REPEAT_DELAY_SECONDS, BASE_URL, DEVICE_ID, LAT, LON, \
    SENSOR_TYPE_TO_PIN_NUM


parser = argparse.ArgumentParser(
    description='Read gas sensors ' + ', '.join(SENSOR_TYPE_TO_PIN_NUM)
    + '\nTo configure, edit file config.py.'
)
parser.add_argument('-c', '--calibrate', action='store_true',
                    help='Calibrate sensors before reading')
parser.add_argument('-u', '--upload', action='store_true',
                    help='Upload readings to configured Web site: ' + BASE_URL)


spi = spidev.SpiDev()
spi.open(0, 0)


def read_adc(sensor_type):
    """
    :return SPI data from the MCP3008 analogue-to-digital converter (ADC),
    from 8 channels in total.
    """
    adc_num = SENSOR_TYPE_TO_PIN_NUM[sensor_type]
    if not 0 <= adc_num <= 7:
        return -1
    r = spi.xfer2([1, 8 + adc_num << 4, 0])
    return ((r[1] & 3) << 8) + r[2]


def calibrate(sensor_type):
    """Calibrate sensor.
    :return Ro, the calculated sensor resistance for the given sensor type

    Ro as the sensor resistance at a specific calibration gas. It was defined by
    sensor manufacturer (see the data sheet linked from the README document).
    But because we do not have pure gases available, we approximate Ro using Rs,
    the resistance at various concentrations of gases, and the ratio Rs/Ro which
    is almost constant for pure air and is given by the manufacturer.
    """
    print('Calibrate ' + sensor_type)
    logging.info('Calibrate ' + sensor_type)

    val = 0.0
    for _ in range(50):
        val += read_adc(sensor_type)
    val /= 50

    if val == 0:
        val += 0.0000001

    ro = (1023 / val - 1) * 5 / 9.48

    print('Val:', val, 'Ro:', ro)
    logging.info('Ro %g' % ro)

    return ro


def upload(sensor_type, reading, ro=None):
    params = dict(
        device_id=DEVICE_ID,
        instant='now',
        latitude=LAT,
        longitude=LON,
        sensorType=sensor_type,
        reading=reading,
    )
    if ro is not None:
        params['ro'] = ro
    response = requests.get(BASE_URL, params=params)
    response.raise_for_status()


def run(should_calibrate, should_upload):
    try:
        print('Press Ctrl+C to abort')
        logging.info('Program started')

        ros = [calibrate(t) if should_calibrate else None
               for t in SENSOR_TYPE_TO_PIN_NUM]

        print('\n\nRead sensors every %s seconds...' % REPEAT_DELAY_SECONDS)
        while True:
            sys.stdout.write('\r\033[K')
            for sensor_type, ro in zip(SENSOR_TYPE_TO_PIN_NUM, ros):
                val = read_adc(sensor_type)
                sys.stdout.write('%s:%g ' % (sensor_type, val))
                sys.stdout.flush()
                if should_upload:
                    upload(val, ro)
                time.sleep(REPEAT_DELAY_SECONDS)

    except KeyboardInterrupt:
        print('\nAbort by user')
        logging.info('Abort by user')


if __name__ == '__main__':
    args = parser.parse_args()
    run(args.calibrate, args.upload)