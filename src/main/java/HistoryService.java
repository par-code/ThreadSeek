import model.SearchCriteria;
import java.util.List;

/**
 * Search history storage.
 * If something goes wrong (for example the database is not available) the methods
 * throw an IllegalStateException with a readable message, and the UI shows it.
 */
public interface HistoryService {

    void save(SearchCriteria criteria, int totalFound, double seconds);

    List<String> getRecent(int limit);

    void clear();
}
