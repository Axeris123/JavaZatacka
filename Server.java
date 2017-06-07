package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Server {

    private static final int PORT = 9001;

    private volatile static HashSet<String> clientsLogins = new HashSet<String>();
    private static HashMap<Integer, int[]> clientsData = new HashMap<>();
    private static final HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    private static HashMap<Socket, Integer> clientSockets = new HashMap<>();
    private static List<String> directionsBegin = Arrays.asList("S","N","E","W");
    private static HashMap<Integer, String> clientsBeginDirections = new HashMap<>();
    private static int beginCounter = 0;
    private static boolean statusStatus = false;
    private static boolean beginStatus = false;

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {

                new Player(listener.accept(), 1).start();
                new Player(listener.accept(), 2).start();

            }
        } finally {
            listener.close();
        }
    }

    private static class Player extends Thread {
        private String inMsg;
        private String outMsg;
        private Socket socket;
        private int id;
        private BufferedReader in;
        private PrintWriter out;

        public Player(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        public void run() {
            try {

                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a inMsg from this client.  Keep requesting until
                // a inMsg is submitted that is not already used.  Note that
                // checking for the existence of a inMsg and adding the inMsg
                // must be done while locking the set of clientsLogins.

                out.println("CONNECT");
                while(true) {
                    inMsg = in.readLine();
                    String loginpart[] = inMsg.split(" ");
                    if (inMsg == null) {
                        return;
                    }
                    synchronized (clientsLogins) {
                        if (inMsg.startsWith("LOGIN")) {
                            int[] coords = new int[2];
                            //do zrobienia, zeby nie mogly byc te same koordy
                            coords[0] = ThreadLocalRandom.current().nextInt(2, 100);
                            coords[1] = ThreadLocalRandom.current().nextInt(2, 100);
                            if (!clientsLogins.contains(loginpart[1])) {
                                clientsLogins.add(loginpart[1]);
                                clientsData.put(id, coords);
                                outMsg = "OK";
                                out.println(outMsg);
                                writers.add(out);
                                System.out.println(out);
                                clientSockets.put(socket, id);
                                break;
                            } else {
                                outMsg = "ERRORTEST";
                                out.println(outMsg);
                            }
                        } else {
                            outMsg = "ERRORTEST";
                            out.println(outMsg);
                        }

                    }
                }

                outMsg = "START";
                while(clientsLogins.size() < 2){
                    try {
                        Thread.sleep(1000);
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                }

                if(clientsLogins.size() == 2 && statusStatus != true)  {
                    sendLoginToAllTCP("START");
                    StringBuilder outBuffer = new StringBuilder();
                    outBuffer.append("PLAYERS");
                    for (Map.Entry<Integer, int[]> entry : clientsData.entrySet()) {
                        outBuffer.append(" ").append(entry.getValue()[0]).append(" ").append(entry.getValue()[1]);
                    }
                    for (PrintWriter writer : writers) {
                        writer.println(outBuffer.toString());
                    }
                    statusStatus = true;
                }

                if(clientsLogins.size() == 2){
                    inMsg = in.readLine();
                    String beginpart[] = inMsg.split(" ");
                    System.out.println(inMsg);
                    if (beginpart[0].equals("BEGIN")) {
                        if (directionsBegin.contains(beginpart[1])) {
                            clientsBeginDirections.put(clientSockets.get(socket), beginpart[1]);
                            outMsg = "OK";
                            out.println(outMsg);
                            beginCounter++;
                        } else {
                            outMsg = "ERROR";
                            out.println(outMsg);
                        }
                    } else {
                        outMsg = "ERROR";
                        out.println(outMsg);
                    }
                }

                while(beginCounter < 2){
                    try {
                        Thread.sleep(1000);
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                }

                if(beginCounter == 2 && !beginStatus){
                    outMsg = "GAME";
                    for (PrintWriter writer : writers) {
                        writer.println(outMsg);
                        beginStatus = true;
                    }
                }








/*              while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }
                    for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + inMsg + ": " + input);
                    }
                }*/
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                // This client is going down!  Remove its inMsg and its out
                // writer from the sets, and close its socket.
                if (inMsg != null) {
                    clientsLogins.remove(inMsg);
                }
                if (out != null) {
                    writers.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static void sendLoginToAllTCP(String message) {
        String startMessage = message;
        for (Map.Entry<Socket, Integer> entry: clientSockets.entrySet()) {
            if (entry != null) {
                try {
                    PrintWriter socketWriter = new PrintWriter(entry.getKey().getOutputStream(),true);
                    message = startMessage;
                    message = message + " " + entry.getValue();
                    socketWriter.println(message);
                } catch (IOException e) {
                    System.out.println("Caught an IO exception trying "
                            + "to send to TCP connections");
                    e.printStackTrace();
                }
            }
        }
    }
}