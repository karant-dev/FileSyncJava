import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;


public class Client {

    static Watcher watcher;

    public Client() {
    }

    public static void watch() {
        watcher = new Watcher(Constants.CLIENT_FILE_ROOT);
        watcher.start();
    }

    public static void main(String[] args) {
        watch();
        Set<String> filesToSend;
        Set<String> filesToDelete;
        do {
            synchronized (watcher.filesToSendLock) {
                filesToSend = watcher.filesToSend;
            }
            synchronized (watcher.filesToDeleteLock) {
                filesToDelete = watcher.filesToDelete;
            }

            for (String fileName : filesToSend) {
                try {
                    File file = new File(Constants.CLIENT_FILE_ROOT + fileName);
                    if (!file.exists()) return;

                    InetAddress serverIp = InetAddress.getByName("localhost");
                    Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);

                    // Get the port number from the server that will receive data through UDP datagrams
                    String action = "SEND REQUEST";
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