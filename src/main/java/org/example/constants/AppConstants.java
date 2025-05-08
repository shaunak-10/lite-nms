package org.example.constants;

public class AppConstants
{

    public static class CredentialQuery
    {
        public static final String ADD_CREDENTIAL = "INSERT INTO credential_profile (name, username, password, system_type) VALUES ($1, $2, $3, $4) RETURNING id";

        public static final String GET_ALL_CREDENTIALS = "SELECT id, name, username, system_type FROM credential_profile ORDER BY id ASC";

        public static final String GET_CREDENTIAL_BY_ID = "SELECT id, name, username, system_type FROM credential_profile WHERE id = $1";

        public static final String DELETE_CREDENTIAL = "DELETE FROM credential_profile WHERE id = $1";

        public static final String UPDATE_CREDENTIAL = "UPDATE credential_profile SET name = $1, username = $2, password = $3 WHERE id = $4";
    }

    public static class DiscoveryQuery
    {
        public static final String ADD_DISCOVERY = "INSERT INTO discovery_profile (name, ip, port, status, credential_profile_id) VALUES ($1, $2, $3, $4, $5) RETURNING id";

        public static final String GET_ALL_DISCOVERY = "SELECT id, name, ip, port, status, credential_profile_id FROM discovery_profile ORDER BY id ASC";

        public static final String GET_DISCOVERY_BY_ID = "SELECT * FROM discovery_profile WHERE id = $1";

        public static final String DELETE_DISCOVERY = "DELETE FROM discovery_profile WHERE id = $1";

        public static final String UPDATE_DISCOVERY = "UPDATE discovery_profile SET name = $1, ip = $2, port = $3, credential_profile_id = $4 WHERE id = $5";

        public static final String DATA_TO_PLUGIN_FOR_DISCOVERY = "SELECT d.id, d.port, d.ip, c.username, c.password, c.system_type FROM discovery_profile d JOIN credential_profile c ON d.credential_profile_id = c.id WHERE d.id = $1";

        public static final String UPDATE_DISCOVERY_STATUS = "UPDATE discovery_profile SET status = $1 WHERE id = $2";

        public static final String FETCH_CREDENTIAL_FROM_ID = "SELECT username, password, system_type FROM credential_profile WHERE id = $1";
    }


    public static class CredentialField
    {
        public static final String ID = "id";

        public static final String NAME = "name";

        public static final String USERNAME = "username";

        public static final String PASSWORD = "password";

        public static final String SYSTEM_TYPE = "system_type";

        public static final String SYSTEM_TYPE_RESPONSE = "system.type";
    }

    public static class DiscoveryField
    {
        public static final String ID = "id";

        public static final String NAME = "name";

        public static final String IP = "ip";

        public static final String PORT = "port";

        public static final String STATUS = "status";

        public static final String CREDENTIAL_PROFILE_ID = "credential_profile_id";

        public static final String CREDENTIAL_PROFILE_ID_RESPONSE = "credential.profile.id";

        public static final String ACTIVE = "active";

        public static final String INACTIVE = "inactive";
    }

    public static class ProvisionQuery
    {
        public static final String ADD_PROVISION = "INSERT INTO provisioned_device (name, ip, port, credential_profile_id) VALUES ($1, $2, $3, $4) RETURNING id";

        public static final String GET_ALL_PROVISIONS = "SELECT pd.*, COALESCE(json_agg(json_build_object('polled.at', pr.polled_at, 'metrics', pr.metrics::json)) FILTER (WHERE pr.id IS NOT NULL), '[]') AS polling_results, (SELECT ROUND(COUNT(*) FILTER (WHERE was_available)/GREATEST(COUNT(*),1)::decimal * 100, 2) FROM availability a WHERE a.provisioned_device_id = pd.id) AS availability_percent FROM provisioned_device pd LEFT JOIN polling_result pr ON pd.id = pr.provisioned_device_id GROUP BY pd.id";

        public static final String GET_PROVISION_BY_ID = "SELECT pd.*, COALESCE(json_agg(json_build_object('polled.at', pr.polled_at, 'metrics', pr.metrics::json)) FILTER (WHERE pr.id IS NOT NULL), '[]') AS polling_results, (SELECT ROUND(COUNT(*) FILTER (WHERE was_available)/GREATEST(COUNT(*),1)::decimal * 100, 2) FROM availability a WHERE a.provisioned_device_id = pd.id) AS availability_percent FROM provisioned_device pd LEFT JOIN polling_result pr ON pd.id = pr.provisioned_device_id WHERE pd.id = $1 GROUP BY pd.id";

        public static final String DELETE_PROVISION = "DELETE FROM provisioned_device WHERE id = $1";

        public static final String DATA_TO_PLUGIN_FOR_POLLING = "SELECT p.id, p.port, p.ip, c.username, c.password FROM provisioned_device p JOIN credential_profile c ON p.credential_profile_id = c.id";

        public static final String INSERT_POLLING_RESULT = "INSERT INTO polling_result (provisioned_device_id, metrics) VALUES ($1, $2::jsonb)";

        public static final String ADD_AVAILABILITY_DATA = "INSERT INTO availability (provisioned_device_id, was_available) VALUES ($1, $2)";
    }

    public static class ProvisionField
    {
        public static final String ID = "id";

        public static final String IP = "ip";

        public static final String PORT = "port";

        public static final String DISCOVERY_PROFILE_ID = "discovery_profile_id";

        public static final String AVAILABILITY_PERCENT_RESPONSE = "availability.percent";

        public static final String AVAILABILITY_PERCENT = "availability_percent";

        public static final String POLLING_RESULTS_RESPONSE = "polling.results";

        public static final String POLLING_RESULTS = "polling_results";

    }


    public static class JsonKey
    {
        public static final String MESSAGE = "message";

        public static final String CREDENTIALS = "credentials";

        public static final String DISCOVERIES = "discoveries";

        public static final String ERROR = "error";

        public static final String DETAILS = "details";

        public static final String SUCCESS = "success";

        public static final String QUERY = "query";

        public static final String PARAMS = "params";

        public static final String ROW_COUNT = "rowCount";

        public static final String ROWS = "rows";

        public static final String ACTION = "action";

        public static final String DEVICE = "device";

        public static final String REACHABLE = "reachable";

        public static final String AVAILABILITY_PARAMS = "availabilityParams";

        public static final String METRICS_RESULTS = "metricsResults";
    }

    public static class Message
    {
        public static final String ADDED_SUCCESS = "added";

        public static final String UPDATED_SUCCESS = "updated";

        public static final String DELETED_SUCCESS = "deleted";

        public static final String NOT_FOUND = "not found";

        public static final String INVALID_JSON_BODY = "Invalid or missing JSON body";

        public static final String MISSING_FIELDS = "Missing required fields";

        public static final String NO_DATA_TO_UPDATE = "No data provided for update";

        public static final String INVALID_ID_IN_PATH = "Invalid ID in path";

        public static final String FAILED_TO_FETCH = "Failed to fetch";

        public static final String FAILED_TO_ADD = "Failed to add";

        public static final String FAILED_TO_UPDATE = "Failed to update";

        public static final String FAILED_TO_DELETE = "Failed to delete";

        public static final String INVALID_IP = "Invalid IP address";

        public static final String INVALID_PORT = "Invalid port";

        public static final String DEVICE_NOT_DISCOVERED = "Device not discovered";

        public static final String UPDATE_NOT_ALLOWED = "Update operation is not permitted for provisioned devices";

        public static final String NO_DEVICES_FOR_DISCOVERY = "No devices found for discovery or ping/port of all devices were not reachable";

        public static final String PLUGIN_EXECUTION_FAILED = "plugin execution failed";
    }

    public static class Headers
    {
        public static final String CONTENT_TYPE = "Content-Type";

        public static final String APPLICATION_JSON = "application/json";
    }

    public static class Routes
    {
        public static final String CREDENTIALS = "/credentials";

        public static final String CREDENTIAL_BY_ID = "/credentials/:id";

        public static final String DISCOVERIES = "/discovery";

        public static final String DISCOVERY_BY_ID = "/discovery/:id";

        public static final String DISCOVERY_RUN = "/discovery/:id/run";

        public static final String PROVISIONS = "/provision";

        public static final String PROVISION_BY_ID = "/provision/:id";
    }

    public static class AddressesAndPaths
    {
        public static final String PLUGIN_PATH = "/home/shaunak/IdeaProjects/http-server/src/main/java/org/example/plugin_executable/ssh-plugin-1";

        public static final String CONFIG_FILE_PATH = "src/main/resources/config.json";
    }

    public static class PingConstants
    {
        public static final String PACKETS_OPTION = "-c";

        public static final String INTERVAL_OPTION = "-i";

        public static final String PING_TIMEOUT_OPTION = "-W";

        public static final String COUNT= "count";

        public static final String TIMEOUT = "timeout";

        public static final String INTERVAL= "interval";

        public static final String PING_COMMAND = "ping";
    }

    public static class PortConstants
    {
        public static final String PORT_TIMEOUT_OPTION = "-w";

        public static final String NC_COMMAND = "nc";

        public static final String ZERO_IO = "-zv";
    }

    public static class ConfigKeys
    {
        public static final String PROCESS = "process";
    }

    public static final String START_DISCOVERY = "startDiscovery";

    public static final String SAVE_AND_RUN_DISCOVERY = "fetchCredentialsAndRunDiscovery";

    public static final Boolean FALSE = false;

    public static final Boolean TRUE = true;
}
