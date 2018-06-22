package org.protectplayanow.api.gaslevel.repository;

import lombok.extern.slf4j.Slf4j;
import org.protectplayanow.api.config.RestApiConsts;
import org.protectplayanow.api.gaslevel.GasLevelRepo;
import org.protectplayanow.api.gaslevel.Reading;
import org.protectplayanow.api.gaslevel.ReadingForRestPOST;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by vladpopescu on 12/17/17.
 */
@Slf4j
public class GasLevelAuroraRepo implements GasLevelRepo {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public List<Reading> getGasReadings(String gasName, Date startDateTime, Date endDateTime, String sensorType, String deviceId) {

        List<Reading> readings = new ArrayList<>();

        jdbcTemplate.query(
                "SELECT * from reading WHERE " +
                        (gasName.equals(RestApiConsts.all) ? "" : " gasName in " + AuroraDbUtils.getInClauseList(gasName) + " and ") +
                        " deviceId = '" + deviceId + "' and " +
                        " sensorType = '" + sensorType + "' and " +
                        " instant >= '" + AuroraDbUtils.getDate(startDateTime) +
                        " ' and instant <= '" + AuroraDbUtils.getDate(endDateTime) + "'" +
                        " order by instant desc",
                new Object[] { },
                (rs, rowNum) -> Reading.builder()
                        .instant(new Date(rs.getTimestamp("instant").getTime()))
                        .deviceId(rs.getString("deviceId"))
                        .gasName(rs.getString("gasName"))
                        .reading(rs.getDouble("reading"))
                        .unitOfReading(rs.getString("unitOfReading"))
                        .latitude(rs.getDouble("latitude"))
                        .longitude(rs.getDouble("longitude"))
                        .sensorType(rs.getString("sensorType"))
                        .build()
        ).forEach(reading -> {
            //log.debug(reading.toString());
            readings.add(reading);
        });

        return readings;
    }

    @Override
    public List<Device> getDevices() {

        List<Device> devices = new ArrayList<>();

        jdbcTemplate.query(
                "select distinct deviceId, sensorType, latitude, longitude from reading " +
                        " order by deviceId asc",
                new Object[] { },
                (rs, rowNum) ->
                        Device.builder()
                        .deviceId(rs.getString("deviceId"))
                        .sensorType(rs.getString("sensorType"))
                        .latitude(rs.getDouble("latitude"))
                        .longitude(rs.getDouble("longitude"))
                        .build()
        ).forEach(device -> {
            log.debug(device.toString());
            devices.add(device);
        });

        return devices;
    }

    @Override
    public void saveGasReadings(String deviceId, Date instant, double latitude, double longitude, List<ReadingForRestPOST> readings) {
/*
INSERT INTO reading
(instant, deviceId, gasName, reading, unitOfReading, latitude, longitude)
VALUES
('2016-12-17 14:01:04', 'id1', 'Methane', 0, 'ppm', 0.3, 0.4),
('2016-12-17 14:01:04', 'id1', 'Benzene', 0, 'ppm', 0.3, 0.4);
 */
        String q = " INSERT INTO reading " +
                " (instant, deviceId, gasName, reading, unitOfReading, latitude, longitude) " +
                " VALUES ";
        StringBuffer sb = new StringBuffer(q);

        for(int i = 0; i < readings.size(); i++){

            ReadingForRestPOST r = readings.get(i);

            sb.append("('" + AuroraDbUtils.getDate(instant) +
                    "', '" + deviceId +
                    "', '" + r.getGasName() +
                    "', " + r.getReading() +
                    ", '" + r.getUnitOfReading() +
                    "', " + latitude +
                    ", " + longitude +
                    "),");

        }

        sb.deleteCharAt(sb.length()-1);

        jdbcTemplate.execute(sb.toString());
    }

    @Override
    public void saveGasReadings(List<Reading> readings) {

        String q = " INSERT INTO reading " +
                " (instant, deviceId, gasName, reading, unitOfReading, latitude, longitude, sensorType) " +
                " VALUES ";

        StringBuffer sb = new StringBuffer(q);

        for(int i = 0; i < readings.size(); i++){

            Reading r = readings.get(i);

            sb.append("('" + AuroraDbUtils.getDate(r.getInstant()) +
                    "', '" + r.getDeviceId() +
                    "', '" + r.getGasName() +
                    "', " + r.getReading() +
                    ", '" + r.getUnitOfReading() +
                    "', " + r.getLatitude() +
                    ", " + r.getLongitude() +
                    ", '" + r.getSensorType() + "'" +
                    "),");

        }

        sb.deleteCharAt(sb.length()-1);

        log.info(sb.toString());

        jdbcTemplate.execute(sb.toString());
    }

}
