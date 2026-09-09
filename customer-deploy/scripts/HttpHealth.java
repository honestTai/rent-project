import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** A quiet HTTP health probe: JRE images need neither curl nor a shell TCP extension. */
public final class HttpHealth {
    public static void main(String[] args) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(args[0]).openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() != 200) System.exit(1);
            String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.matches("(?s).*\"status\"\\s*:\\s*\"UP\".*")) System.exit(1);
            connection.disconnect();
        } catch (Exception exception) {
            System.exit(1);
        }
    }
}
