import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import model.SearchCriteria;

/**
 * Stores search history in MySQL.
 *
 * Setup: set the environment variable THREADSEEK_DB_PASSWORD (see README.md).
 * If it is not set, an empty password is tried (the XAMPP default).
 * The database "threadseek" and the table are created automatically on first use.
 * Optional: THREADSEEK_DB_USER (default root) and THREADSEEK_DB_URL.
 */
public class MySQLHistoryService implements HistoryService {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/threadseek"
            + "?createDatabaseIfNotExist=true&connectTimeout=3000&allowPublicKeyRetrieval=true";

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS search_history ("
            + "id INT AUTO_INCREMENT PRIMARY KEY, "
            + "search_name VARCHAR(255), "
            + "extension VARCHAR(20), "
            + "location VARCHAR(500), "
            + "total_found INT, "
            + "time_taken_seconds DOUBLE, "
            + "search_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

    private boolean schemaReady = false;

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private Connection getConnection() throws SQLException {
        // If THREADSEEK_DB_PASSWORD is not set we try an empty password (XAMPP default).
        String password = System.getenv("THREADSEEK_DB_PASSWORD");
        boolean passwordGiven = password != null && !password.isEmpty();
        if (password == null) password = "";

        Connection con;
        try {
            con = DriverManager.getConnection(
                    env("THREADSEEK_DB_URL", DEFAULT_URL),
                    env("THREADSEEK_DB_USER", "root"),
                    password);
        } catch (SQLException e) {
            if (!passwordGiven) {
                throw new SQLException(e.getMessage()
                        + " (tip: set THREADSEEK_DB_PASSWORD to your MySQL password)", e);
            }
            throw e;
        }
        ensureSchema(con);
        return con;
    }

    private synchronized void ensureSchema(Connection con) throws SQLException {
        if (schemaReady) return;
        try (Statement st = con.createStatement()) {
            st.executeUpdate(CREATE_TABLE);
        }
        schemaReady = true;
    }

    private IllegalStateException wrap(SQLException e) {
        return new IllegalStateException("database error: " + e.getMessage(), e);
    }

    @Override
    public void save(SearchCriteria criteria, int totalFound, double seconds) {
        String sql = "INSERT INTO search_history "
                + "(search_name, extension, location, total_found, time_taken_seconds) "
                + "VALUES (?, ?, ?, ?, ?)";

        StringBuilder location = new StringBuilder();
        for (java.io.File root : criteria.getRoots()) {
            if (location.length() > 0) location.append("; ");
            location.append(root.getAbsolutePath());
        }
        if (location.length() > 500) location.setLength(500);

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, criteria.getKeyword());
            pst.setString(2, criteria.getExtension());
            pst.setString(3, location.toString());
            pst.setInt(4, totalFound);
            pst.setDouble(5, seconds);
            pst.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    @Override
    public List<String> getRecent(int limit) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT search_name, extension, location, total_found, "
                + "time_taken_seconds, search_date "
                + "FROM search_history ORDER BY search_date DESC, id DESC LIMIT ?";
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy HH:mm");

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setInt(1, limit);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("search_name");
                    String ext = rs.getString("extension");
                    history.add("[" + fmt.format(rs.getTimestamp("search_date")) + "] "
                            + "name: " + (name == null || name.isEmpty() ? "(any)" : name)
                            + " | type: " + (ext == null || ext.isEmpty() ? "any" : "." + ext)
                            + " | found: " + rs.getInt("total_found")
                            + " | " + String.format("%.2f", rs.getDouble("time_taken_seconds")) + "s"
                            + " | " + rs.getString("location"));
                }
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return history;
    }

    @Override
    public void clear() {
        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement("DELETE FROM search_history")) {
            pst.executeUpdate();
        } catch (SQLException e) {
            throw wrap(e);
        }
    }
}
