import service.ConcurrentSearchService;
import service.SearchService;

public class Main {

    public static void main(String[] args) {

        // Multithreaded file searching
        SearchService searchService = new ConcurrentSearchService();

        // Search history in MySQL
        HistoryService historyService = new MySQLHistoryService();

        // Connect both to the console UI and start
        new ConsoleUI(searchService, historyService).start();
    }
}
