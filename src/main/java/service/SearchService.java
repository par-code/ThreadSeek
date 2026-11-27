package service;

import java.util.List;
import model.FileResult;
import model.SearchCriteria;
import model.SearchStats;

public interface SearchService {
    List<FileResult> search(SearchCriteria criteria, SearchStats stats);
}
