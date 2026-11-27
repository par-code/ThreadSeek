package service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import model.FileResult;
import model.SearchCriteria;
import model.SearchStats;

/**
 * Multithreaded file search.
 *
 * How it works:
 *  - A fixed pool of worker threads is created.
 *  - Every DIRECTORY is a small task. A worker lists one directory, checks the
 *    files in it, and submits each sub-directory as a new task.
 *  - So different drives / folders / sub-folders are searched at the same time.
 *  - Folders we are not allowed to open are skipped and counted (never crash).
 */
public class ConcurrentSearchService implements SearchService {

    /** Matches beyond this are still counted, but not stored (protects memory). */
    private static final int MAX_STORED_RESULTS = 10_000;

    private final int threadCount;

    public ConcurrentSearchService() {
        this(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public ConcurrentSearchService(int threadCount) {
        this.threadCount = Math.max(1, threadCount);
    }

    public int getThreadCount() { return threadCount; }

    @Override
    public List<FileResult> search(SearchCriteria criteria, SearchStats stats) {
        if (stats == null) stats = new SearchStats();
        stats.setThreadsUsed(threadCount);
        stats.start();

        if (criteria == null) {
            stats.stop();
            return new ArrayList<>();
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Scan scan = new Scan(criteria, stats, executor);
        try {
            scan.run();
        } finally {
            executor.shutdownNow();
            stats.stop();
        }
        return new ArrayList<>(scan.results);
    }

    /** All the state of one search run. */
    private static class Scan {
        final SearchCriteria criteria;
        final SearchStats stats;
        final ExecutorService executor;

        final Queue<FileResult> results = new ConcurrentLinkedQueue<>();
        final AtomicInteger stored = new AtomicInteger();

        // pending = number of directory tasks not finished yet.
        // Starts at 1 (a "guard") so the search cannot finish while roots are still being submitted.
        final AtomicInteger pending = new AtomicInteger(1);
        final CountDownLatch done = new CountDownLatch(1);

        final String keyword;
        final String extension;
        final boolean caseSensitive;

        Scan(SearchCriteria criteria, SearchStats stats, ExecutorService executor) {
            this.criteria = criteria;
            this.stats = stats;
            this.executor = executor;
            this.caseSensitive = criteria.isCaseSensitive();

            String k = criteria.getKeyword();
            String e = criteria.getExtension();
            if (e.startsWith(".")) e = e.substring(1);
            if (!caseSensitive) {
                k = k.toLowerCase(Locale.ROOT);
                e = e.toLowerCase(Locale.ROOT);
            }
            this.keyword = k;
            this.extension = e;
        }

        void run() {
            for (File root : criteria.getRoots()) {
                if (root == null || !root.exists() || !root.canRead()) {
                    stats.addDirSkipped();
                    continue;
                }
                Path path = root.toPath();
                if (Files.isDirectory(path)) {
                    submitDir(path);
                } else {
                    handleEntry(path);   // a single file given as a root
                }
            }
            finishOne();   // release the guard

            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // cancelled
            }
        }

        void submitDir(Path dir) {
            pending.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        scanDir(dir);
                    } finally {
                        finishOne();
                    }
                });
            } catch (RejectedExecutionException e) {
                finishOne();
            }
        }

        void finishOne() {
            if (pending.decrementAndGet() == 0) done.countDown();
        }

        /** Looks inside one folder. Inaccessible folder = skip it, do not stop the search. */
        void scanDir(Path dir) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
                stats.addDirScanned();
                for (Path entry : entries) {
                    handleEntry(entry);
                }
            } catch (IOException | SecurityException | DirectoryIteratorException e) {
                stats.addDirSkipped();
            }
        }

        void handleEntry(Path entry) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(
                        entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isDirectory()) {
                    submitDir(entry);              // sub-folder -> new task
                } else if (attrs.isRegularFile()) {
                    checkFile(entry, attrs);
                }
            } catch (IOException | SecurityException e) {
                stats.addError();
            }
        }

        void checkFile(Path file, BasicFileAttributes attrs) {
            stats.addFileScanned();
            Path namePart = file.getFileName();
            if (namePart == null) return;
            String name = namePart.toString();

            if (!matches(name, attrs.size())) return;

            stats.addFileMatched();
            if (stored.incrementAndGet() <= MAX_STORED_RESULTS) {
                results.add(new FileResult(name, file.toAbsolutePath().toString(),
                        attrs.size(), attrs.lastModifiedTime().toMillis()));
            }
        }

        boolean matches(String fileName, long size) {
            String name = caseSensitive ? fileName : fileName.toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty() && !name.contains(keyword)) return false;
            if (!extension.isEmpty()) {
                int dot = name.lastIndexOf('.');
                if (dot < 0 || !name.substring(dot + 1).equals(extension)) return false;
            }
            return size >= criteria.getMinSize() && size <= criteria.getMaxSize();
        }
    }
}
