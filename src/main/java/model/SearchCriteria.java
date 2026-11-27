package model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Search filters and one or more directories/files to search. */
public class SearchCriteria {
    private final List<File> roots = new ArrayList<>();
    private String keyword = "";
    private String extension = "";
    private long minSize = 0;
    private long maxSize = Long.MAX_VALUE;
    private boolean caseSensitive = false;

    public List<File> getRoots() { return roots; }

    public void setRoots(List<File> newRoots) {
        roots.clear();
        if (newRoots != null) {
            for (File root : newRoots) if (root != null) roots.add(root);
        }
    }

    public void addRoot(File root) {
        if (root != null) roots.add(root);
    }

    public File getRootDirectory() {
        return roots.isEmpty() ? null : roots.get(0);
    }

    public void setRootDirectory(File root) {
        roots.clear();
        if (root != null) roots.add(root);
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword == null ? "" : keyword.trim(); }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension == null ? "" : extension.trim(); }
    public long getMinSize() { return minSize; }
    public void setMinSize(long minSize) { this.minSize = Math.max(0, minSize); }
    public long getMaxSize() { return maxSize; }
    public void setMaxSize(long maxSize) { this.maxSize = maxSize < 0 ? Long.MAX_VALUE : maxSize; }
    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }
}
