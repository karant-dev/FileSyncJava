import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class Watcher extends Thread {

    Path baseDir;
    WatchService watchService = null;
    WatchKey watchKey = null;

    // Sets for files Create/Modify and Delete at the other end
    Set<String> filesToSend = new HashSet<>();
    Set<String> filesToDelete = new HashSet<>();

    public Watcher(String directoryPath) {
        baseDir = Paths.get(directoryPath);
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Watcher() {
        this(Constants.CLIENT_FILE_ROOT);
    }

    public void run() {
        // Registering the WatchKey object to the WatchService for Create, Modify and Delete events
        try {
            watchKey = baseDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        while (true) {
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                Path fileNamePath = (Path) event.context();
                String fileName = fileNamePath.toString();
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE || event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    filesToSend.add(fileName);
                } else {
                    filesToDelete.add(fileName);
                }
            }
        }
    }

    public Set<String> getFilenamesToSend() {
        Set<String> tempFilesToSend = new HashSet<String>();
        tempFilesToSend.addAll(filesToSend);
        filesToSend.clear();
        return tempFilesToSend;
    }

    public Set<String> getFilenamesToDelete() {
        Set<String> tempFilesToDelete = new HashSet<String>();
        tempFilesToDelete.addAll(filesToDelete);
        filesToDelete.clear();
        return tempFilesToDelete;
    }
}
