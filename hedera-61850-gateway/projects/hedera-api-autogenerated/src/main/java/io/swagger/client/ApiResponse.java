/*
 * Hedera - Hub for Energy Distribution and Excess Resource Allocation
 * <h2>Intended use</h2> <p>   This api is intended to be used by Market Participants who have a contract with the Grid Operator to receive additional capacity.    Schedules can be requested to receive capacity that can be used by the Market Participant. Once schedule with the provisioned capacity   is acquired the Market Participant is expected to live within those set point boundaries. If the Market Participant comes to the conclusion   that he needs more or less capacity he can updated his requested schedule. If the schedule is no longer required it is expected that the Market Participant   removes it. </p>  <h2>Authentication</h2> <p>   The api is secured with OAuth2. Once a contract is provided to the Market Participant credentials of grant type \"client_credentials\" will be provided.    The <strong>client_id</strong> and <strong>client_secret</strong> can be used to authenticate with <a href=\"https://auth.hedera.alliander.com/\">auth.hedera.alliander.com</a>. The bearer token can then be used in the Authentication header as follows <code>Authorization: Bearer &lt;token&gt;</code>. </p>  <h2>Versioning</h2> <p>   This API implements <b>MediaType-based</b> versioning. To request a specific version use the accept header, for example:   <code>Accept: application/vnd.hedera.v1+json</code>. If no version is specified you will request the latest version automatically.    Be aware that not providing a version can cause issues when breaking changes are released in the new version and is therefore not recommended.    When using and older version of the API you will received a Sunset header giving an indication of when support for that version will be removed in the future. </p>  <h2>Non breaking changes</h2> <p>   Within the current major version it is allowed to make non breaking changes in the same major version of the api. Non breaking changed are defined as follows: </p> <ul>   <li>Adding a endpoint</li>   <li>Adding a resource</li>   <li>Adding a optional field to a existing resource</li>   <li>Adding a parameter to a endpoint</li>   <li>Adding enums to fields that have a fallback (default) value</li> </ul>  <h2>Connectivity issues</h2> <p>   When experiencing connection problems with Hedera it is expected that the Market Participant falls back to its Firm Capacity specified in the contract with the Grid Operator. Reasoning behind this is that if we can not communicate anymore we run the risk of overloading the grid capacity limits. The grid must be protected and be as stable as possible at all times and when communication is not possible every Market Participant needs to fallback to its Firm Capacity limits. </p> 
 *
 * OpenAPI spec version: 1.0.0
 * Contact: support@hedera.alliander.com
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */

package io.swagger.client;

import java.util.List;
import java.util.Map;

/**
 * API response returned by API call.
 *
 * @param <T> The type of data that is deserialized from response body
 */
public class ApiResponse<T> {
    final private int statusCode;
    final private Map<String, List<String>> headers;
    final private T data;

    /**
     * @param statusCode The status code of HTTP response
     * @param headers The headers of HTTP response
     */
    public ApiResponse(int statusCode, Map<String, List<String>> headers) {
        this(statusCode, headers, null);
    }

    /**
     * @param statusCode The status code of HTTP response
     * @param headers The headers of HTTP response
     * @param data The object deserialized from response bod
     */
    public ApiResponse(int statusCode, Map<String, List<String>> headers, T data) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.data = data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public T getData() {
        return data;
    }
}
