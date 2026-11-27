# ThreadSeek

A multithreaded console application that searches your computer for files by name,
extension and size, and keeps a search history in MySQL.

## Features
- Search by full or partial file name (not case sensitive)
- Filter by extension and by minimum / maximum size
- Recursive search through sub-folders
- Search the entire PC, chosen drives, or specific folders
- A pool of worker threads searches different folders at the same time
- Folders that cannot be opened are skipped safely and counted
- Live progress, paged results, search statistics
- Search history stored in MySQL (view and clear it from the menu)

## Project structure
```
run.bat / run.sh              one-click compile and run (Windows / Mac-Linux)
lib/                          MySQL JDBC driver
src/main/java/
    Main.java                 starts the program
    ConsoleUI.java            console interface
    HistoryService.java       history interface
    MySQLHistoryService.java  MySQL / JDBC history
    model/                    SearchCriteria, SearchStats, FileResult
    service/                  SearchService, ConcurrentSearchService (multithreading)
schema.sql                    database table (also created automatically)
```

## 1. Install what you need
1. **JDK 17 or newer** - https://adoptium.net  (check with `javac -version`)
2. **MySQL Server** - https://dev.mysql.com/downloads/mysql/
   (during install choose a root password and remember it).
   XAMPP also works: start MySQL in the XAMPP control panel; its root password is empty.

## 2. Run
- **Windows:** double-click `run.bat` (or run it from the VS Code terminal).
- **Mac / Linux:** `./run.sh`

The script compiles the project and asks for your MySQL root password
(press ENTER if it has none). The database `threadseek` and the table
`search_history` are created automatically the first time a search is saved.

To skip the question, set the password first:
- PowerShell: `$env:THREADSEEK_DB_PASSWORD="your_password"`
- Command Prompt: `set THREADSEEK_DB_PASSWORD=your_password`

Optional variables: `THREADSEEK_DB_USER` (default `root`), `THREADSEEK_DB_URL`.

## Manual compile and run (without the scripts)
```
javac -encoding UTF-8 -cp "lib/mysql-connector-j-26.10.0.jar" -d out src/main/java/*.java src/main/java/model/*.java src/main/java/service/*.java
java -cp "out;lib/mysql-connector-j-26.10.0.jar" Main
```
(Mac / Linux: use `:` instead of `;`.)

## Troubleshooting
- **"Search was NOT saved to history: ... Access denied"** - wrong MySQL password.
- **"... Communications link failure"** - MySQL is not running. Start the MySQL service.
- If MySQL is unavailable, searching still works; only the history is affected.
- Colours and box characters are on by default.
  Colours off: add `-Dthreadseek.color=false` before `-cp`.
  Plain ASCII boxes (if the symbols look wrong): add `-Dthreadseek.ascii=true`.
- Menu: 1 Search Files, 2 View Search History (you can also delete it there), 3 Help, 4 Exit.
