package model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics collected during a search.
 * The search threads update the counters while the UI thread reads them
 * (for the live progress line), so everything here is thread safe.
 */
public class SearchStats {
    private final AtomicLong filesScanned = new AtomicLong();
    private final AtomicLong filesMatched = new AtomicLong();
    private final AtomicLong dirsScanned = new AtomicLong();
    private final AtomicLong dirsSkipped = new AtomicLong();   // folders we were not allowed to open
    private final AtomicLong errors = new AtomicLong();        // other unreadable items
    private volatile int threadsUsed = 1;
    private volatile long startMillis;
    private volatile long endMillis;

    // --- updated by the search threads ---
    public void addFileScanned() { filesScanned.incrementAndGet(); }
    public void addFileMatched() { filesMatched.incrementAndGet(); }
    public void addDirScanned()  { dirsScanned.incrementAndGet(); }
    public void addDirSkipped()  { dirsSkipped.incrementAndGet(); }
    public void addError()       { errors.incrementAndGet(); }
    public void setThreadsUsed(int threads) { threadsUsed = threads; }

    public void start() { endMillis = 0; startMillis = System.currentTimeMillis(); }
    public void stop()  { endMillis = System.currentTimeMillis(); }

    // --- read by the UI ---
    public long getFilesScanned() { return filesScanned.get(); }
    public long getFilesMatched() { return filesMatched.get(); }
    public long getDirsScanned()  { return dirsScanned.get(); }
    public long getDirsSkipped()  { return dirsSkipped.get(); }
    public long getErrors()       { return errors.get(); }
    public int getThreadsUsed()   { return threadsUsed; }

    /** Time taken in milliseconds (keeps counting while the search is still running). */
    public long getTimeTaken() {
        if (startMillis == 0) return 0;
        long end = endMillis == 0 ? System.currentTimeMillis() : endMillis;
        return end - startMillis;
    }

    @Override
    public String toString() {
        return "Files scanned: " + getFilesScanned()
                + ", matched: " + getFilesMatched()
                + ", folders skipped: " + getDirsSkipped()
                + ", errors: " + getErrors()
                + ", time: " + getTimeTaken() + " ms";
    }
}
