package de.fhg.ise.gateway.interfaces.hedera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import de.fhg.ise.gateway.Context;
import de.fhg.ise.gateway.HederaException;
import de.fhg.ise.gateway.configuration.Settings;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ScheduleApi;
import io.swagger.client.auth.OAuth;
import io.swagger.client.model.DateTimeInterval;
import io.swagger.client.model.Period;
import io.swagger.client.model.Point;
import io.swagger.client.model.Quantity;
import io.swagger.client.model.RegisteredInterTie;
import io.swagger.client.model.Schedule.AtTypeEnum;
import io.swagger.client.model.ScheduleGetResponse;
import io.swagger.client.model.SchedulePostResponse;
import io.swagger.client.model.ScheduleRequest;
import io.swagger.client.model.TimeSeries;
import io.swagger.client.model.UnitMultiplier;
import io.swagger.client.model.UnitSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneId;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

/**
 * Wrapper class to conveniently access the HEDERA server using the autogenerated code and some custom written code for
 * authentication.
 */
public class HederaApi {

    private static final String ACCEPT_HEADER = "application/vnd.hedera.v1+json";

    private static final Logger log = LoggerFactory.getLogger(HederaApi.class);
    private final ScheduleApi api;
    private String lastLoginBearer;

    public HederaApi(String clientId, String clientSecret) throws IOException {
        try {
            api = login(clientId, clientSecret);
        } catch (Exception e) {
            throw new IOException("Unable to log in", e);
        }
    }

    public HederaApi(Settings settings) throws IOException {
        this(settings.clientId, settings.clientSecret);
    }

    private ScheduleApi login(String clientId, String clientSecret) throws IOException {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        // Configure OAuth2 access token for authorization: oauth2
        OAuth oauth2 = (OAuth) defaultClient.getAuthentication("oauth2");
        String accessToken = getOAuthAccessToken(clientId, clientSecret);
        lastLoginBearer =accessToken.replaceAll("\"","");
        log.debug("BEARER token: {}", lastLoginBearer);
        oauth2.setAccessToken(lastLoginBearer);
        String auth = "Bearer " + oauth2.getAccessToken();

        ApiClient apiClient = new ApiClient();
        apiClient.addDefaultHeader("Content-Type", "application/vnd.hedera.v1+json");
        apiClient.addDefaultHeader("accept", "application/vnd.hedera.v1+json");
        apiClient.addDefaultHeader("Authorization", auth);
        apiClient.setBasePath("https://api.hedera.alliander.com");
        ScheduleApi apiInstance = new ScheduleApi();
        apiInstance.setApiClient(apiClient);

        return apiInstance;
    }

    private String getOAuthAccessToken(String clientId, String clientSecret) throws IOException {

        URL url = new URL("https://login.microsoftonline.com/697f104b-d7cb-48c8-ac9f-bd87105bafdc/oauth2/v2.0/token");
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setRequestMethod("POST");

        httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        httpConn.setDoOutput(true);
        OutputStreamWriter writer = new OutputStreamWriter(httpConn.getOutputStream());
        writer.write("client_id=" + clientId
                + "&grant_type=client_credentials&scope=api%3A%2F%2Fapi.hedera.alliander.com%2F.default&client_secret="
                + clientSecret);
        writer.flush();
        writer.close();
        httpConn.getOutputStream().close();

        InputStream responseStream =
                httpConn.getResponseCode() / 100 == 2 ? httpConn.getInputStream() : httpConn.getErrorStream();
        Scanner s = new Scanner(responseStream).useDelimiter("\\A");
        String response = s.hasNext() ? s.next() : "";
        log.trace("got response {} with http status code {}", response, httpConn.getResponseCode());
        log.debug("Logged in to Microsoft using oauth to get token");

        JsonObject convertedObject = Context.GSON.fromJson(response, JsonObject.class);

        String BEARER = String.valueOf(convertedObject.get("access_token"));
        log.trace("Got bearer token {}", BEARER);
        return BEARER;
    }

    public HederaSchedule requestExtensionAwaitCalculation(Instant start, HederaScheduleInterval interval,
            List<Double> values, HederaDirection direction, Settings settings) throws HederaException {

        // TODO: as setting!
        final Duration durationUntilAbort = Duration.ofMinutes(5);
        Optional<UUID> scheduleId = Optional.empty();

        try {
            scheduleId = Optional.ofNullable(
                    createSchedule(direction.getMRID(settings), start, interval, values, direction));

            if (scheduleId.isPresent()) {
                log.info("created new schedule with mrid={}", scheduleId.get());
            }
            else {
                throw new HederaException("No scheduleId returned by HEDERA after schedule creation. Parameters:" + //
                        " start=" + start + ",interval=" + interval + ",values=" + values + ",direction=" + direction);
            }
            log.info("Requesting schedule in W: {}", values);
        } catch (HederaException e) {
            throw e;
        } catch (Exception e) {
            throw new HederaException("Unable to create schedule: " + e.getClass() + ": " + e.getMessage());
        }

        try {
            HederaSchedule schedule = awaitScheduleCalculationAtHedera(scheduleId, durationUntilAbort);
            log.info("Result schedule in kW:   {}", schedule.getValues());
            return schedule;
        } catch (Exception e) {
            try {
                log.info("Deleting corrupt schedule with mrid={}", scheduleId);
                this.deleteSchedule(scheduleId.get());
            } catch (Exception ex) {
                log.warn("Unable to delete corrupt schedule with mrid={}. Reason {}:{}", scheduleId, ex.getClass(),
                        ex.getMessage());
            }
            throw new HederaException(
                    "HEDERA was unable to calculate schedule. Stopped with " + e.getClass() + ": " + e.getMessage());
        }
    }

    private HederaSchedule awaitScheduleCalculationAtHedera(Optional<UUID> scheduleId, Duration durationUntilAbort)
            throws HederaException {
        AtTypeEnum status;
        HederaSchedule schedule;
        int count = 0;
        Instant start = Instant.now();
        final Instant end = start.plus(durationUntilAbort);
        final long pollrateMillis = 5_000;
        boolean inital = true;
        try {
            log.info("Will now start polling HEDERA with an interval of {}s until calculation is completed.",
                    pollrateMillis / 1000.0);
            do {
                if (!inital) {
                    // give hedera a bit time to calculate the schedules
                    Thread.sleep(pollrateMillis);
                }
                inital = false;

                //Reading the created schedule at HEDERA
                schedule = readSchedule(scheduleId.get());
                //   log.debug("read schedule result: " + schedule.getRawResponse());
                status = schedule.getStatus();
                log.debug("New schedule at HEDERA, is now in state {} with message {}", status,
                        schedule.getStatusMessage());

                count++;
                log.info("Read HEDERA API {} times. Schedule calculation is currently in state '{}'", count, status);
                if (AtTypeEnum.DECLINED.equals(status)) {
                    throw new HederaException("Schedule was rejected by HEDERA. Reason as provided by HEDERA: '"
                            + schedule.getStatusMessage() + "'.");
                }
            } while ((Instant.now().isBefore(end)) && (!AtTypeEnum.ACCEPTED.equals(status)));

            if (AtTypeEnum.ACCEPTED.equals(status)) {
                log.info("Calculation at HEDERA finished. Took {}s",
                        Duration.between(start, Instant.now()).toSeconds());
                return schedule;
            }
            else {
                throw new HederaException("Schedule calculation at hedera took too long");
            }
        } catch (HederaException e) {
            throw e;
        } catch (Exception e) {
            throw new HederaException(e);
        }
    }

    public void deleteSchedule(HederaSchedule schedule) throws ApiException {
        deleteSchedule(schedule.getScheduleUuid());
    }

    /**
     * Values in W
     */
    public static RegisteredInterTie getRegisteredInterTies(UUID mrid, Instant start, HederaScheduleInterval interval,
            List<Double> values, HederaDirection direction) {

        RegisteredInterTie jsonBody = new RegisteredInterTie();
        jsonBody.setMRID(mrid);
        jsonBody.direction(direction.getDirectionAsHederaEnum());
        jsonBody.isAggregatedRes(false);
        jsonBody.aggregatedNodes(null);
        TimeSeries timeSeries = new TimeSeries();
        timeSeries.setMRID(jsonBody.getMRID());
        Period period = new Period();
        period.setResolution(interval.getAsResolutionEnum());
        DateTimeInterval dateTimeInterval = new DateTimeInterval();
        Instant end = start.plus(interval.getAsDuration().multipliedBy(values.size()));
        dateTimeInterval.setStart(convertToThreetenTime(start));
        dateTimeInterval.setEnd(convertToThreetenTime(end));
        period.setTimeInterval(dateTimeInterval);
        timeSeries.period(period);
        Map<Integer, Double> pointItemMap = new HashMap<>();
        int index = 0;
        for (Double value : values) {
            pointItemMap.put(index, value);
            index++;
        }
        for (Map.Entry<Integer, Double> point : pointItemMap.entrySet()) {
            Point pointItems = new Point();
            pointItems.setPosition(point.getKey());
            pointItems.setQuantity(point.getValue());
            timeSeries.addPointsItem(pointItems);

        }
        Quantity quantity = new Quantity();
        quantity.setUnitMultiplier(UnitMultiplier.NONE);
        quantity.setUnitSymbol(UnitSymbol.W);
        timeSeries.setQuantity(quantity);
        jsonBody.timeSeries(timeSeries);
        return jsonBody;
    }

    /**
     * Values in kW
     *
     * @returns the schedule mrid that can be used for further requests
     */
    public UUID createSchedule(UUID mrid, Instant start, HederaScheduleInterval interval, List<Double> values,
            HederaDirection direction) throws ApiException {
        ScheduleRequest body = new ScheduleRequest().addRegisteredInterTiesItem(
                getRegisteredInterTies(mrid, start, interval, values, direction)); // ScheduleRequest |
        log.trace("JSON body sent to create Schedule:\n" + body.toString());
        log.debug("Trying to create a schedule at HEDERA...");
        SchedulePostResponse result = api.schedulePost(ACCEPT_HEADER, body);
        log.info("Successfully created a schedule at HEDERA.");
        return result.getScheduleReference().getMRID();
    }

    public HederaSchedule readSchedule(UUID result_mRID) throws ApiException {
        log.debug("Trying to read the created schedule at HEDERA...");

        ScheduleGetResponse result = api.scheduleMRIDGet(result_mRID, ACCEPT_HEADER);
        log.trace("Successfully read the schedule at HEDERA, got result {}", result);

        return new HederaSchedule(result);
    }

    public void deleteSchedule(UUID result_mRID) throws ApiException {
        log.debug("Trying to delete the schedule at HEDERA...");

        api.scheduleMRIDDelete(result_mRID, ACCEPT_HEADER);
        log.debug("Successfully deleted a schedule at HEDERA");
    }

    /**
     * This is a very simplistic implementation of an HEDERA API v2 call.
     *
     * TODO: rather use autogenerated v2 code here (but the API changed, so this will need a bit of more work)
     */
    public Collection<MinimalSchedule> getScheduleMRIDsOfAllExistingSchedules() throws IOException {
        HttpURLConnection connection = null;
        try {
            int pageSize = Integer.MAX_VALUE;
            String urlString = "https://api.hedera.alliander.com/schedule?pageSize=" + pageSize;
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + lastLoginBearer);
            connection.setRequestProperty("accept", "application/vnd.hedera.v1+json");
            connection.setRequestProperty("Content-Type", "application/vnd.hedera.v1+json");
            int responseCode = connection.getResponseCode();
            InputStream responseStream = connection.getInputStream();
            Scanner s = new Scanner(responseStream).useDelimiter("\\A");
            String response = s.hasNext() ? s.next() : "";
            JsonObject convertedObject = Context.GSON.fromJson(response, JsonObject.class);
            JsonElement schedules = convertedObject.get("schedules");
            Type listOfMyClassObject = new TypeToken<ArrayList<MinimalSchedule>>() {}.getType();

            List<MinimalSchedule>existingSchedules = Context.GSON.fromJson(schedules, listOfMyClassObject);
            log.debug("Got response code {} and {} existing schedules",responseCode,existingSchedules.size());
            return existingSchedules;
        }
        finally {
            if(connection!=null){
                connection.disconnect();
            }
        }
    }

    public static class MinimalSchedule {
        UUID mRID;
        AtTypeEnum status;

        @Override
        public String toString() {
            return "Schedule mRID="+mRID+" ("+status+")";
        }
        public void setmRID(String mRID) {
            this.mRID = UUID.fromString(mRID);
        }

        public void setStatus(String status){
            AtTypeEnum atTypeEnum = AtTypeEnum.fromValue(status);
            if(atTypeEnum == null){
                this.status = AtTypeEnum.UNKNOWN;
            }else
                this.status=atTypeEnum;
        }
    }

    /**
     * Swagger seems to use {@link OffsetDateTime} instead of the default java {@link java.time.OffsetDateTime}, which
     * makes this conversion necessary.
     */
    private static OffsetDateTime convertToThreetenTime(Instant javaTime) {
        long epochSecond = javaTime.getEpochSecond();
        long nanos = javaTime.getNano();
        long epochMillis = epochSecond * 1000 + nanos / 1000 / 1000;
        OffsetDateTime convertedOffsetDateTime = OffsetDateTime.ofInstant(
                org.threeten.bp.Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
        log.trace("Converted {} into {}", javaTime, convertedOffsetDateTime);
        return convertedOffsetDateTime;
    }
}
