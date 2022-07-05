import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class Server {
    static ServerSocket serverSocketA = null;
    static ServerSocket serverSocketB = null;

    public Server() {
    }

    public static void main(String[] args) {

        try {
            boolean isTurnClientA = true; // set turn to be clientA
            List<String> filesDeletedInA = new ArrayList<>();
            List<String> filesDeletedInB = new ArrayList<>();
            try {
                serverSocketA = new ServerSocket(Constants.SERVER_TCP_PORT_RCV_FROM_A);
                serverSocketA.setSoTimeout(3000);
                serverSocketB = new ServerSocket(Constants.SERVER_TCP_PORT_RCV_FROM_B);
                serverSocketB.setSoTimeout(3000);
            } catch (IOException ioEx) {
                System.out.println("\n>> Unable to set up port!");
                System.exit(1);
            }
            System.out.println("\r\n>> Ready to accept requests");

            while(true) {
                if(isTurnClientA) {
                    syncWithA(filesDeletedInA);
                } else {
                    syncWithB(filesDeletedInB);
                }
                isTurnClientA = !isTurnClientA;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteFiles(List<String> filesToDelete) {
        for (String fileName : filesToDelete) {
            if (fileName.equals("DELETE REQUEST")) { continue; }
            try {
                File file = new File(Constants.SERVER_FILE_ROOT + fileName);
                if (file.canWrite()) {
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }




    // Client A

    public static void receiveHandleA(Socket socket, PrintWriter outputSocket, int senderPort) {
        try {
            // create the response with the port number which will receive data from clients through UDP
            String response = "SEND REQUEST OK: receive data with the port:" + Constants.SERVER_UDP_PORT_RCV_FROM_A;
            System.out.println(">> Response: " + response + Constants.CRLF);

            // send the response
            outputSocket.println(response + Constants.CRLF + "STOP");
            outputSocket.close();

            PacketBoundedBufferMonitor bm = new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);
            InetAddress senderIp = socket.getInetAddress(); // get the IP of the sender
            InetAddress receiverIp = InetAddress.getByName("localhost");

            receiveFileA(bm, receiverIp, Constants.SERVER_UDP_PORT_RCV_FROM_A, senderIp, senderPort); // receive the file

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void receiveFileA(PacketBoundedBufferMonitor bm, InetAddress receiverIp, int receiverPort, InetAddress senderIp, int senderPort) {

        PacketReceiver packetReceiver = new PacketReceiver(bm, receiverIp, receiverPort, senderIp, senderPort);
        packetReceiver.start();

        FileWriter fileWriter = new FileWriter(bm);
        fileWriter.start();

        try {
            packetReceiver.join();
            fileWriter.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void syncWithA(List<String> filesDeletedInA) {
        try {
            // Wait for clients...
            Socket client = serverSocketA.accept();

            System.out.println("\n>> New request is accepted." + Constants.CRLF);

            Scanner inputSocket = new Scanner(client.getInputStream());
            PrintWriter outputSocket = new PrintWriter(client.getOutputStream(), true);

            // Get actionType from the received data
            String line = inputSocket.nextLine();
            int clientUDPPort = 0;
            String actionType = "";

            // Get filesToDelete
            if (line.startsWith("DELETE REQUEST")) {
                while (!line.equals("DELETE STOP")) {
                    if (line.isEmpty()) {
                        line = inputSocket.nextLine();
                        continue;
                    }
                    if (line.startsWith("DELETE REQUEST")) {
                        System.out.println(">> Request: " + line + Constants.CRLF);
                        actionType = "DELETE REQUEST";
                        filesDeletedInA = Arrays.asList(line.split("#"));
                        break;
                    }
                    line = inputSocket.nextLine();
                }
            }

            if (actionType.equals("DELETE REQUEST")) {
                deleteFiles(filesDeletedInA);
            }

            // line = inputSocket.nextLine();
            actionType = "";

            if (!line.startsWith("DELETE REQUEST")) {
                while (!line.equals("STOP")) {
                    if (line.isEmpty()) {
                        line = inputSocket.nextLine();
                        continue;
                    }
                    if (line.startsWith("SEND REQUEST")) {
                        System.out.println(">> Request: " + line + Constants.CRLF);
                        actionType = "SEND REQUEST";
                        clientUDPPort = Integer.parseInt(line.split("#")[2].strip());
                        break;
                    }
                    line = inputSocket.nextLine();
                }

                if (actionType.equals("SEND REQUEST")) {
                    receiveHandleA(client, outputSocket, clientUDPPort);
                }
            }

        } catch (SocketTimeoutException to) {
            System.out.println("Waited for Client A, now polling Client B");
        } catch (IOException io) {
            System.out.println(">> Fail to listen to requests!");
            System.exit(1);
        }
    }



    // Client B

    public static void receiveHandleB(Socket socket, PrintWriter outputSocket, int senderPort) {
        try {
            // create the response with the port number which will receive data from clients through UDP
            String response = "SEND REQUEST OK: receive data with the port:" + Constants.SERVER_UDP_PORT_RCV_FROM_B;
            System.out.println(">> Response: " + response + Constants.CRLF);

            // send the response
            outputSocket.println(response + Constants.CRLF + "STOP");
            outputSocket.close();

            PacketBoundedBufferMonitor bm = new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);
            InetAddress senderIp = socket.getInetAddress(); // get the IP of the sender
            InetAddress receiverIp = InetAddress.getByName("localhost");

            receiveFileB(bm, receiverIp, Constants.SERVER_UDP_PORT_RCV_FROM_B, senderIp, senderPort); // receive the file

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void receiveFileB(PacketBoundedBufferMonitor bm, InetAddress receiverIp, int receiverPort, InetAddress senderIp, int senderPort) {

        PacketReceiver packetReceiver = new PacketReceiver(bm, receiverIp, receiverPort, senderIp, senderPort);
        packetReceiver.start();

        FileWriter fileWriter = new FileWriter(bm);
        fileWriter.start();

        try {
            packetReceiver.join();
            fileWriter.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void syncWithB(List<String> filesDeletedInB) {
        try {
            // Wait for clients...
            Socket client = serverSocketB.accept();
            System.out.println("\n>> New request is accepted." + Constants.CRLF);

            Scanner inputSocket = new Scanner(client.getInputStream());
            PrintWriter outputSocket = new PrintWriter(client.getOutputStream(), true);

            // Get actionType from the received data
            String line = inputSocket.nextLine();
            int clientUDPPort = 0;
            String actionType = "";

            // Get filesToDelete
            if (line.startsWith("DELETE REQUEST")) {
                while (!line.equals("DELETE STOP")) {
                    if (line.isEmpty()) {
                        line = inputSocket.nextLine();
                        continue;
                    }
                    if (line.startsWith("DELETE REQUEST")) {
                        System.out.println(">> Request: " + line + Constants.CRLF);
                        actionType = "DELETE REQUEST";
                        filesDeletedInB = Arrays.asList(line.split("#"));
                        break;
                    }
                    line = inputSocket.nextLine();
                }
            }

            if (actionType.equals("DELETE REQUEST")) {
                deleteFiles(filesDeletedInB);
            }

            // line = inputSocket.nextLine();
            actionType = "";

            if (!line.startsWith("DELETE REQUEST")) {
                while (!line.equals("STOP")) {
                    if (line.isEmpty()) {
                        line = inputSocket.nextLine();
                        continue;
                    }
                    if (line.startsWith("SEND REQUEST")) {
                        System.out.println(">> Request: " + line + Constants.CRLF);
                        actionType = "SEND REQUEST";
                        clientUDPPort = Integer.parseInt(line.split("#")[2].strip());
                        break;
                    }
                    line = inputSocket.nextLine();
                }

                if (actionType.equals("SEND REQUEST")) {
                    receiveHandleB(client, outputSocket, clientUDPPort);
                }
            }

        } catch (SocketTimeoutException to) {
            System.out.println("Waited for Client B, now polling Client A");
        } catch (IOException io) {
            System.out.println(">> Fail to listen to requests!");
            System.exit(1);
        }
    }
}

	