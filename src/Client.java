import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;


public class Client {

    static Watcher watcher;

    public Client() {
    }

    public static void watch() {
        HashSet<String> initFilesToSend = new HashSet<String>();
        File directory = new File(Constants.CLIENT_FILE_ROOT);
        File[] initFileList = directory.listFiles();
        if (initFileList != null) {
            for (File file : initFileList) {
                if (file.isFile() && !file.getName().startsWith("~") && !file.getName().endsWith(".tmp")) {
                    initFilesToSend.add(file.getName());
                }
            }
        }
        watcher = new Watcher(Constants.CLIENT_FILE_ROOT, initFilesToSend);
        watcher.start();
    }

    public static void main(String[] args) {
        watch();
        Set<String> filesToSend;
        Set<String> filesToDelete;
        String action = "";
        do {
            synchronized (watcher.filesToSendLock) {
                filesToSend = watcher.filesToSend;
            }
            synchronized (watcher.filesToDeleteLock) {
                filesToDelete = watcher.filesToDelete;
            }

            // Deletion part
            if(!filesToDelete.isEmpty()) {
                InetAddress server;
                try {
                    server = InetAddress.getByName("localhost");
                    action = "DELETE REQUEST";
                    try (Socket socket = new Socket(server, Constants.SERVER_TCP_PORT)) {
                        sendFileNamesToDelete(socket, filesToDelete, action);
                        synchronized (watcher.filesToDeleteLock) {
                            watcher.filesToDelete.clear();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            // Create/Modify part
            for (String fileName : filesToSend) {
                try {
                    File file = new File(Constants.CLIENT_FILE_ROOT + fileName);
                    if (!file.exists()) return;

                    InetAddress serverIp = InetAddress.getByName("localhost");
                    Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);

                    // Get the port number from the server that will receive data through UDP datagrams
                    action = "SEND REQUEST";
                    int serverPort = getPortFromServer(tcpSocket, action, fileName, Constants.CLIENT_UDP_PORT);
                    if (serverPort == 0) {
                        return;
                    }

                    // Send the file
                    PacketBoundedBufferMonitor bufferMonitor = new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);
                    InetAddress senderIp = InetAddress.getByName("localhost");

                    PacketSender packetSender = new PacketSender(bufferMonitor, senderIp, Constants.CLIENT_UDP_PORT, serverIp, serverPort);
                    packetSender.start();

                    FileReader fileReader = new FileReader(bufferMonitor, fileName);
                    fileReader.start();

                    try {
                        packetSender.join();
                        fileReader.join();
                        // watcher.join();
                        if(!fileReader.hadError) {
                            synchronized (watcher.filesToSendLock) {
                                watcher.filesToSend.remove(fileName);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw e;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } while (true);
    }// end of main

    public static void sendFileNamesToDelete(Socket socket, Set<String> filesToDelete, String action) {
        try {
            String fileNames = "";
            PrintWriter outputSocket = new PrintWriter(socket.getOutputStream(), true);
            for (String fileName : filesToDelete) {
                fileNames += fileName + "#";
            }
            outputSocket.println(action + "#" + fileNames + Constants.CRLF + "DELETE STOP");
            socket.close();
            outputSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * get the port number, through which the server will receive data
     *
     * @param tcpSocket Instance of Socket class
     * @param action The action Client has requested - Ex: SEND REQUEST, DELETE REQUEST
     * @param fileName Name of the file
     * @param udpPort  the port number that the client will send data
     * @return Returns the UDP port of the Server
     */
    public static int getPortFromServer(Socket tcpSocket, String action, String fileName, int udpPort) {
        int serverPort = 0;
        try {
            Scanner inputSocket = new Scanner(tcpSocket.getInputStream());
            PrintWriter outputSocket = new PrintWriter(tcpSocket.getOutputStream(), true);

            // send the HTTP packet
            String request = action + " # " + fileName + " # " + udpPort;
            outputSocket.println(request + Constants.CRLF + "STOP");
            System.out.println(Constants.CRLF + ">> Request:" + request);

            // receive the response
            String line = inputSocket.nextLine();

            // get the port number of the server that will receive data for the file
            while (!line.equals("STOP")) {
                if (line.isEmpty()) {
                    line = inputSocket.nextLine();
                    continue;
                }
                if (line.startsWith(action)) {
                    // get the new port that is assigned by the server to receive data
                    System.out.println(">> Response:" + line + Constants.CRLF);
                    String[] items = line.split(":");
                    serverPort = Integer.parseInt(items[items.length - 1]);
                    break;
                }
                line = inputSocket.nextLine();
            }
            inputSocket.close();
            outputSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serverPort;
    }
}