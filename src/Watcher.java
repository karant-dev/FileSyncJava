import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class Watcher extends Thread {

    Path baseDir;
    WatchService watchService;
    WatchKey watchKey = null;

    // Sets for files Create/Modify and Delete at the other end
    static Set<String> filesToSend = new HashSet<>();
    static Set<String> filesToDelete = new HashSet<>();
    static final Object filesToSendLock = new Object();
    static final Object filesToDeleteLock = new Object();

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
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        while (true) {
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                Path fileNamePath = (Path) event.context();
                String fileName = fileNamePath.toString();
                System.out.println(event.kind() + " " + fileName);
                if (fileName.startsWith("~") || fileName.endsWith(".tmp")) {
                    continue;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE || event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                    synchronized (filesToSendLock) {
                        filesToSend.add(fileName);
                        System.out.println(filesToSend);
                    }
                } else {
                    synchronized (filesToDeleteLock) {
                        filesToDelete.add(fileName);
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public Set<String> getFilenamesToSend() {
        Set<String> tempFilesToSend;
        synchronized (filesToSendLock) {
            tempFilesToSend = new HashSet<>(filesToSend);
//            filesToSend.clear();
        }
        return tempFilesToSend;
    }

    public Set<String> getFilenamesToDelete() {
        Set<String> tempFilesToDelete;
        synchronized (filesToDeleteLock) {
            tempFilesToDelete = new HashSet<>(filesToDelete);
//            filesToDelete.clear();
        }
        return tempFilesToDelete;
    }
}
