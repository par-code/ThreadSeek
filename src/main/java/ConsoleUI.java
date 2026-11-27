import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import model.FileResult;
import model.SearchCriteria;
import model.SearchStats;
import service.SearchService;

/** ThreadSeek console user interface. */
public class ConsoleUI {

    // ---------- DISPLAY OPTIONS ----------
    // Colours and box characters are ON by default.
    // Turn them off with:  -Dthreadseek.color=false   or   -Dthreadseek.ascii=true
    private static final boolean COLOR =
            System.getenv("NO_COLOR") == null
            && !"false".equalsIgnoreCase(System.getProperty("threadseek.color", "true"));

    private static final boolean UNICODE =
            !"true".equalsIgnoreCase(System.getProperty("threadseek.ascii", "false"));

    // ---------- COLORS ----------
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String BRIGHT_CYAN = "\u001B[96m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String WHITE = "\u001B[97m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    // ---------- BOX CHARACTERS ----------
    private static final String TL = UNICODE ? "╔" : "+";
    private static final String TR = UNICODE ? "╗" : "+";
    private static final String BL = UNICODE ? "╚" : "+";
    private static final String BR = UNICODE ? "╝" : "+";
    private static final String H  = UNICODE ? "═" : "=";
    private static final String V  = UNICODE ? "║" : "|";
    private static final String LINE_CHAR = UNICODE ? "─" : "-";

    private static final int PAGE_SIZE = 10;

    private final Scanner scanner = new Scanner(System.in);
    private final SearchService searchService;
    private final HistoryService historyService;

    public ConsoleUI(SearchService searchService, HistoryService historyService) {
        this.searchService = searchService;
        this.historyService = historyService;

    }

    // ---------- COLOR HELPER ----------
    private String color(String text, String code) {
        return COLOR ? code + text + RESET : text;
    }

    // ---------- INPUT HELPER ----------
    /** Reads a line. If the input is closed (Ctrl+D / Ctrl+Z) the program exits cleanly. */
    private String readLine() {
        if (!scanner.hasNextLine()) {
            exitApplication();
            System.exit(0);
        }
        return scanner.nextLine();
    }

    // ---------- START APPLICATION ----------
    public void start() {

        showWelcome();

        while (true) {
            showMenu();

            System.out.print(color("\n  Select an option  > ", BRIGHT_CYAN));
            String choice = readLine().trim();

            switch (choice) {
                case "1":
                    searchFiles();
                    break;
                case "2":
                    viewHistory();
                    break;
                case "3":
                    showHelp();
                    break;
                case "4":
                    exitApplication();
                    return;
                default:
                    error("Invalid option. Please select 1, 2, 3 or 4.");
            }

            pause();
        }
    }

    // ---------- WELCOME SCREEN ----------
    private void showWelcome() {
        int w = 50;
        System.out.println();
        System.out.println(color("  " + TL + repeat(H, w) + TR, CYAN));
        System.out.println(color("  " + V + center("", w) + V, CYAN));
        System.out.println(color("  " + V + center("T H R E A D S E E K", w) + V, BRIGHT_CYAN + BOLD));
        System.out.println(color("  " + V + center("", w) + V, CYAN));
        System.out.println(color("  " + V + center("SMART FILE DISCOVERY SYSTEM", w) + V, WHITE));
        System.out.println(color("  " + V + center("", w) + V, CYAN));
        System.out.println(color("  " + BL + repeat(H, w) + BR, CYAN));

        System.out.println();
        System.out.println(color("  SYSTEM STATUS : READY", GREEN + BOLD));
        System.out.println(color("  VERSION       : 1.0", GRAY));
        System.out.println(color("  MODE          : CONSOLE", GRAY));
        System.out.println();
    }

    // ---------- MAIN MENU ----------
    private void showMenu() {
        line();
        System.out.println(color("                     MAIN MENU", BRIGHT_CYAN + BOLD));
        line();
        System.out.println();
        System.out.println(color("    [1]", CYAN) + color("  Search Files", WHITE));
        System.out.println(color("    [2]", CYAN) + color("  View Search History", WHITE));
        System.out.println(color("    [3]", CYAN) + color("  Help & Information", WHITE));
        System.out.println(color("    [4]", RED) + color("  Exit Application", WHITE));
        System.out.println();
        line();
    }

    // =====================================================================
    //  SEARCH
    // =====================================================================
    private void searchFiles() {

        heading("NEW FILE SEARCH");
        System.out.println(color("  Enter the details below to configure your search.", GRAY));
        System.out.println(color("  Leave the name or extension blank to match any.", GRAY));
        System.out.println();

        // File name (blank = any)
        System.out.print(color("  File name / keyword : ", CYAN));
        String name = readLine().trim();

        // Extension
        String extension = readExtension();

        // Location
        System.out.println();
        List<File> roots = chooseLocation();
        if (roots == null) {
            warning("Search cancelled.");
            return;
        }

        // Sizes
        System.out.println();
        double[] sizesMb = readSizeRange();
        long minSize = (long) (sizesMb[0] * 1024L * 1024L);
        long maxSize = sizesMb[1] == 0 ? Long.MAX_VALUE : (long) (sizesMb[1] * 1024L * 1024L);

        // Build the criteria
        SearchCriteria criteria = new SearchCriteria();
        criteria.setKeyword(name);
        criteria.setExtension(extension);
        criteria.setMinSize(minSize);
        criteria.setMaxSize(maxSize);
        criteria.setRoots(roots);

        showSearchConfiguration(name, extension, roots, sizesMb[0], sizesMb[1]);

        // Protect the user from an accidental "match everything" search
        if (name.isEmpty() && extension.isEmpty() && minSize == 0 && maxSize == Long.MAX_VALUE) {
            warning("No name, extension or size filter: this matches EVERY file.");
            if (!askYesNo("Continue anyway?")) {
                warning("Search cancelled.");
                return;
            }
        }

        runSearch(criteria);
    }

    private void runSearch(SearchCriteria criteria) {

        System.out.println();
        heading("SEARCH IN PROGRESS");

        SearchStats stats = new SearchStats();
        AtomicBoolean running = new AtomicBoolean(true);

        // Spinner thread: only shows progress, it never touches the search itself.
        Thread spinner = new Thread(() -> {
            String[] frames = {"|", "/", "-", "\\"};
            int i = 0;
            while (running.get()) {
                System.out.print("\r" + color("  Searching " + frames[i++ % 4], CYAN)
                        + "   scanned: " + stats.getFilesScanned()
                        + "   found: " + stats.getFilesMatched()
                        + "   skipped folders: " + stats.getDirsSkipped() + "   ");
                System.out.flush();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        spinner.setDaemon(true);
        spinner.start();

        List<FileResult> results;
        try {
            results = searchService.search(criteria, stats);
            if (results == null) results = Collections.emptyList();
        } catch (Exception e) {
            stopSpinner(running, spinner);
            error("Search failed: " + e.getMessage());
            return;
        }
        stopSpinner(running, spinner);

        success("Search completed.");

        results = new ArrayList<>(results);
        results.sort(Comparator
                .comparing((FileResult r) -> r.getFileName().toLowerCase())
                .thenComparing(FileResult::getFilePath));

        displayResults(results, stats.getFilesMatched());
        displayStatistics(stats);
        saveToHistory(criteria, stats);
    }

    private void stopSpinner(AtomicBoolean running, Thread spinner) {
        running.set(false);
        try {
            spinner.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        System.out.print("\r" + repeat(" ", 100) + "\r");
    }

    private void saveToHistory(SearchCriteria criteria, SearchStats stats) {
        if (historyService == null) return;
        try {
            historyService.save(criteria, (int) stats.getFilesMatched(),
                    stats.getTimeTaken() / 1000.0);
            success("Search saved to history.");
        } catch (RuntimeException e) {
            warning("Search was NOT saved to history: " + e.getMessage());
        }
    }

    // =====================================================================
    //  INPUT: EXTENSION, LOCATION, SIZE
    // =====================================================================
    private String readExtension() {
        while (true) {
            System.out.print(color("  File extension      : ", CYAN));
            String ext = readLine().trim();
            if (ext.startsWith(".")) ext = ext.substring(1);
            if (ext.isEmpty()) return "";
            if (ext.matches("[A-Za-z0-9_+-]{1,15}")) return ext;
            error("That does not look like an extension. Try pdf, .jpg or java.");
        }
    }

    /** Returns the folders to search, or null if the user cancelled. */
    private List<File> chooseLocation() {

        System.out.println(color("  Where do you want to search?", CYAN));
        System.out.println(color("    [1]", CYAN) + color("  Entire PC (all drives)", WHITE));
        System.out.println(color("    [2]", CYAN) + color("  Choose drive(s)", WHITE));
        System.out.println(color("    [3]", CYAN) + color("  Specific folder(s)", WHITE));
        System.out.println();

        while (true) {
            System.out.print(color("  Search location     : ", CYAN));
            String choice = readLine().trim();

            switch (choice) {
                case "1": {
                    List<File> drives = availableDrives();
                    if (drives.isEmpty()) {
                        error("No readable drives were found.");
                        return null;
                    }
                    return drives;
                }
                case "2":
                    return chooseDrives();
                case "3":
                    return chooseFolders();
                default:
                    error("Please enter 1, 2 or 3.");
            }
        }
    }

    private List<File> availableDrives() {
        List<File> drives = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (root.exists() && root.canRead()) drives.add(root);
            }
        }
        return drives;
    }

    private List<File> chooseDrives() {
        List<File> drives = availableDrives();
        if (drives.isEmpty()) {
            error("No readable drives were found.");
            return null;
        }

        System.out.println();
        for (int i = 0; i < drives.size(); i++) {
            System.out.println(color("    [" + (i + 1) + "]", CYAN)
                    + color("  " + drives.get(i).getPath(), WHITE));
        }
        System.out.println();

        while (true) {
            System.out.print(color("  Drive numbers (1,2 / back) : ", CYAN));
            String input = readLine().trim();
            if (input.equalsIgnoreCase("back")) return null;

            List<File> chosen = new ArrayList<>();
            for (String part : input.split(",")) {
                try {
                    int index = Integer.parseInt(part.trim()) - 1;
                    if (index >= 0 && index < drives.size() && !chosen.contains(drives.get(index))) {
                        chosen.add(drives.get(index));
                    }
                } catch (NumberFormatException ignored) {
                    // skip the bad part
                }
            }

            if (!chosen.isEmpty()) return chosen;
            error("Please choose at least one valid drive number.");
        }
    }

    private List<File> chooseFolders() {
        System.out.println(color("  Tip: separate several folders with ;   Type 'back' to cancel.", GRAY));

        while (true) {
            System.out.print(color("  Folder path(s)      : ", CYAN));
            String input = readLine().trim();
            if (input.equalsIgnoreCase("back")) return null;

            List<File> folders = new ArrayList<>();
            List<String> invalid = new ArrayList<>();

            for (String part : input.split(";")) {
                String path = part.trim().replace("\"", "");
                if (path.isEmpty()) continue;
                File folder = new File(path).getAbsoluteFile().toPath().normalize().toFile();
                if (folder.isDirectory() && folder.canRead()) {
                    if (!folders.contains(folder)) folders.add(folder);
                } else {
                    invalid.add(path);
                }
            }

            if (!invalid.isEmpty()) {
                error("Not a readable folder: " + String.join(", ", invalid));
                continue;
            }
            if (folders.isEmpty()) {
                error("Please enter at least one folder.");
                continue;
            }
            return folders;
        }
    }

    private double[] readSizeRange() {
        while (true) {
            double min = readSizeMb("Minimum file size (MB, 0 for no minimum): ");
            double max = readSizeMb("Maximum file size (MB, 0 for no maximum): ");
            if (max != 0 && max < min) {
                error("Maximum size cannot be smaller than the minimum size.");
                continue;
            }
            return new double[]{min, max};
        }
    }

    private double readSizeMb(String message) {
        while (true) {
            System.out.print(color("  " + message, CYAN));
            String input = readLine().trim();
            if (input.isEmpty()) return 0;
            try {
                double value = Double.parseDouble(input);
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 1_000_000_000) {
                    error("Enter a valid non-negative size.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                error("Please enter a number, e.g. 5 or 0.5");
            }
        }
    }

    private boolean askYesNo(String question) {
        while (true) {
            System.out.print(color("  " + question + " (y/n) : ", YELLOW));
            String input = readLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) return true;
            if (input.equals("n") || input.equals("no")) return false;
            error("Please type y or n.");
        }
    }

    // =====================================================================
    //  DISPLAY
    // =====================================================================
    private void showSearchConfiguration(String name, String extension,
                                         List<File> roots, double minMb, double maxMb) {

        heading("SEARCH CONFIGURATION");

        System.out.println(color("  Keyword       : ", GRAY)
                + color(name.isEmpty() ? "Any name" : name, WHITE));
        System.out.println(color("  Extension     : ", GRAY)
                + color(extension.isEmpty() ? "All files" : "." + extension, WHITE));
        System.out.println(color("  Location      : ", GRAY) + color(describeRoots(roots), WHITE));
        System.out.println(color("  Minimum size  : ", GRAY)
                + color(minMb == 0 ? "No minimum" : trim(minMb) + " MB", WHITE));
        System.out.println(color("  Maximum size  : ", GRAY)
                + color(maxMb == 0 ? "No limit" : trim(maxMb) + " MB", WHITE));
        line();
    }

    private String describeRoots(List<File> roots) {
        if (roots.size() == 1) return roots.get(0).getPath();
        List<String> names = new ArrayList<>();
        for (File root : roots) names.add(root.getPath());
        return roots.size() + " locations (" + String.join(", ", names) + ")";
    }

    private void displayResults(List<FileResult> results, long totalMatched) {

        heading("SEARCH RESULTS");

        if (results.isEmpty()) {
            warning("No matching files found.");
            return;
        }

        System.out.println(color("  Total matches: " + totalMatched, GREEN + BOLD));
        if (totalMatched > results.size()) {
            System.out.println(color("  (Showing the first " + results.size()
                    + " matches only. Use a more specific search to narrow it down.)", YELLOW));
        }
        System.out.println();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a");

        for (int i = 0; i < results.size(); i++) {
            FileResult file = results.get(i);

            System.out.println(color("  [" + (i + 1) + "] " + file.getFileName(), BRIGHT_CYAN + BOLD));
            System.out.println(color("      Path     : " + file.getFilePath(), GRAY));
            System.out.println(color("      Size     : " + formatSize(file.getFileSize()), WHITE));
            System.out.println(color("      Modified : "
                    + dateFormat.format(new Date(file.getLastModified())), GRAY));
            System.out.println();

            boolean endOfPage = (i + 1) % PAGE_SIZE == 0 && i + 1 < results.size();
            if (endOfPage) {
                System.out.print(color("  -- shown " + (i + 1) + " of " + results.size()
                        + ".  ENTER = next page, Q = stop : ", GRAY));
                if (readLine().trim().equalsIgnoreCase("q")) {
                    System.out.println();
                    break;
                }
                System.out.println();
            }
        }
    }

    private void displayStatistics(SearchStats stats) {

        heading("SEARCH STATISTICS");

        stat("Files scanned", String.valueOf(stats.getFilesScanned()), WHITE);
        stat("Matching files", String.valueOf(stats.getFilesMatched()), GREEN + BOLD);
        stat("Folders scanned", String.valueOf(stats.getDirsScanned()), WHITE);
        stat("Folders skipped (no access)", String.valueOf(stats.getDirsSkipped()),
                stats.getDirsSkipped() > 0 ? YELLOW : WHITE);
        stat("Other errors", String.valueOf(stats.getErrors()), WHITE);
        stat("Threads used", String.valueOf(stats.getThreadsUsed()), WHITE);
        stat("Execution time", String.format("%.3f seconds", stats.getTimeTaken() / 1000.0),
                BRIGHT_CYAN);

        line();
    }

    private void stat(String label, String value, String valueColor) {
        System.out.println(color(String.format("  %-29s: ", label), GRAY) + color(value, valueColor));
    }

    // =====================================================================
    //  HISTORY
    // =====================================================================
    private void viewHistory() {

        heading("SEARCH HISTORY");

        if (historyService == null) {
            warning("History service is not connected.");
            return;
        }

        try {
            List<String> history = historyService.getRecent(10);

            if (history == null || history.isEmpty()) {
                warning("No search history available.");
                return;
            }

            int count = 1;
            for (String entry : history) {
                System.out.println(color("  " + count + ". ", CYAN) + color(entry, WHITE));
                count++;
            }

            System.out.println();
            if (askYesNo("Delete ALL saved search history?")) {
                historyService.clear();
                success("Search history cleared.");
            }
        } catch (RuntimeException e) {
            warning("History is unavailable: " + e.getMessage());
        }
    }

    // =====================================================================
    //  HELP / EXIT
    // =====================================================================
    private void showHelp() {

        heading("HELP & INFORMATION");

        System.out.println(color("  THREADSEEK - SMART FILE DISCOVERY SYSTEM", BRIGHT_CYAN + BOLD));
        System.out.println();

        helpItem("1. Search Files",
                "Search by name (full or partial), extension and size.",
                "Search the entire PC, chosen drives, or specific folders.",
                "Folders are searched at the same time using multiple threads.",
                "Folders you are not allowed to open are skipped automatically.");
        helpItem("2. Search History",
                "View your last 10 searches (stored in MySQL).",
                "You can also delete the saved history from there.");
        helpItem("3. Help", "Show this screen.");
        helpItem("4. Exit", "Close ThreadSeek.");

        line();
        System.out.println(color("  TIP: Searching the entire PC can take a while. Use an", YELLOW));
        System.out.println(color("       extension or name filter to get faster, shorter results.", YELLOW));
    }

    private void helpItem(String title, String... lines) {
        System.out.println(color("  " + title, CYAN + BOLD));
        for (String text : lines) System.out.println("     " + text);
        System.out.println();
    }

    private void exitApplication() {
        int w = 42;
        System.out.println();
        System.out.println(color("  " + TL + repeat(H, w) + TR, CYAN));
        System.out.println(color("  " + V + center("THREADSEEK SESSION CLOSED", w) + V, BRIGHT_CYAN + BOLD));
        System.out.println(color("  " + V + center("Thank you for using us!", w) + V, WHITE));
        System.out.println(color("  " + BL + repeat(H, w) + BR, CYAN));
        System.out.println();
    }

    // =====================================================================
    //  UI HELPERS
    // =====================================================================
    private void heading(String title) {
        System.out.println();
        line();
        System.out.println(color("  >> " + title, BRIGHT_CYAN + BOLD));
        line();
        System.out.println();
    }

    private void line() {
        System.out.println(color("  " + repeat(LINE_CHAR, 50), CYAN));
    }

    private void success(String message) {
        System.out.println(color("\n  [SUCCESS] " + message, GREEN + BOLD));
    }

    private void warning(String message) {
        System.out.println(color("\n  [WARNING] " + message, YELLOW + BOLD));
    }

    private void error(String message) {
        System.out.println(color("\n  [ERROR] " + message, RED + BOLD));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private String repeat(String text, int times) {
        return text.repeat(Math.max(0, times));
    }

    private String center(String text, int width) {
        int space = width - text.length();
        if (space <= 0) return text.substring(0, width);
        int left = space / 2;
        return repeat(" ", left) + text + repeat(" ", space - left);
    }

    private void pause() {
        System.out.println();
        System.out.print(color("  Press ENTER to continue...", GRAY));
        readLine();
    }
}
