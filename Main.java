package Server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
      public static ArrayList<String> ClientsLogins = new ArrayList<>();
      public static HashMap<Integer, int[]> clientsData = new HashMap<>();
      public static Socket clientSockets[] = new Socket[8];
      public static int intLastSocket = 0;
      public static int clientNumber = 1;


    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket(12900, 100,
                InetAddress.getByName("localhost"));
        System.out.println("Server.Main started  at:  " + serverSocket);

        while (intLastSocket < 3) {
            System.out.println("Waiting for a  connection...");

            final Socket activeSocket = serverSocket.accept();
            clientSockets[intLastSocket] = activeSocket;

            System.out.println("Received a  connection from  " + activeSocket);
            Runnable runnable = () -> handleClientRequest(activeSocket);
            new Thread(runnable).start(); // start a new thread

            intLastSocket++;
        }
    }

    private static void handleClientRequest(Socket socket) {
        try{
            BufferedReader socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

           // Pattern login = Pattern.compile("\\bLOGIN \\b");
            //Matcher m = null;

            String outMsg = null;
            String inMsg = null;
            outMsg = "CONNECT\n";
            socketWriter.write(outMsg);
            socketWriter.flush();


            //LOGOWANIE
            while((inMsg = socketReader.readLine()) != null){
                if(inMsg.contains("LOGIN ")){
                    int[] coords = new int[2];
                    String loginpart = inMsg.split(" ")[1];
                    //do zrobienia, zeby nie mogly byc te same koordy
                    coords[0] = ThreadLocalRandom.current().nextInt(1,101);
                    coords[1] = ThreadLocalRandom.current().nextInt(1,101);
                    ClientsLogins.add(loginpart);
                    clientsData.put(clientNumber, coords);

                    outMsg="OK\n";
                    socketWriter.write(outMsg);
                    socketWriter.flush();
                    if (ClientsLogins.size() == 3)
                    {
                        break;
                    }
                    clientNumber++;
                }
                else{
                    outMsg="ERROR\n";
                    socketWriter.write(outMsg);
                    socketWriter.flush();
                }
            }

            //wysłanie START
            if(ClientsLogins.size() == 3){
                outMsg = "START ";
                sendLoginToAllTCP(outMsg);
                StringBuffer outBuffer = new StringBuffer();
                outBuffer.append("PLAYERS\n");
                        for (Map.Entry<Integer,int[]> entry: clientsData.entrySet()){
                            outBuffer.append(entry.getKey());
                            outBuffer.append(" ").append(entry.getValue()[0]).append(",").append(entry.getValue()[1]).append("\n");
                        }
                System.out.println(outBuffer);

                //Wysłanie listy playerów z koordami
                for (Socket z : clientSockets) {
                    if (z != null) {
                        try {
                            BufferedWriter socketWriters = new BufferedWriter(new OutputStreamWriter(z.getOutputStream()));
                            socketWriters.write(outBuffer.toString());
                            socketWriters.flush();
                        } catch (IOException e) {
                            System.out.println("Caught an IO exception trying "
                                    + "to send to TCP connections");
                            e.printStackTrace();
                        }
                    }
                }

            }

            socket.close();
        } catch(Exception e){
            e.printStackTrace();
        }

    }



    public static void sendLoginToAllTCP(String message) {
        int i = 1;
        String startMessage = message;
        for (Socket z : clientSockets) {
            if (z != null) {
                try {
                    BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(z.getOutputStream()));
                    message = startMessage;
                    message = message + i + "\n";
                    socketWriter.write(message);
                    socketWriter.flush();
                    i++;
                } catch (IOException e) {
                    System.out.println("Caught an IO exception trying "
                            + "to send to TCP connections");
                    e.printStackTrace();
                }
            }
        }



    }
}