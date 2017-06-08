package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    private static final int PORT = 9001;
    private static final int PLAYERS = 2;


    private static Random r = new Random();

    private static  HashSet<String> clientsLogins = new HashSet<String>();
    private static HashMap<Integer, int[]> clientsData = new HashMap<>();
    private static  HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    private static HashMap<Socket, Integer> clientSockets = new HashMap<>();
    private static final List<String> directionsBegin = Arrays.asList("S","N","E","W");
    private static final List<String> directionsMove = Arrays.asList("R","L","S");
    private static HashMap<Integer, String> clientsBeginDirections = new HashMap<>();
    private static int beginCounter = 0;
    private static int lostCounter = 0;
    private static boolean statusStatus = false;
    private static boolean sendGameStatus = false;
    private static boolean sendBoardStatus = false;
    private static Lock lock = new ReentrantLock();
    private static Condition counter = lock.newCondition();
    private static final Object lockobj = new Object();
    private static int[][] Board = new int[100][100];




    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        try (ServerSocket listener = new ServerSocket(PORT)) {
            new Player(listener.accept(), 1).start();
            new Player(listener.accept(), 2).start();
            //        new Player(listener.accept(), 3).start();



        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Player extends Thread {
        private String inMsg;
        private String outMsg;
        private Socket socket;
        private int id;
        private BufferedReader in;
        private PrintWriter out;

        Player(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        public void run() {
            try {

                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);


                //CONNECT

                out.println("CONNECT");
                while(true) {
                    inMsg = in.readLine();
                    String loginpart[] = inMsg.split(" ");
                    if (inMsg == null) {
                        outMsg = "ERROR";
                        out.println(outMsg);
                    }
                    else{
                        //LOGIN
                        synchronized (clientsLogins) {
                            if (inMsg.startsWith("LOGIN ")) {
                                int[] coords = new int[2];
                                //do zrobienia, zeby nie mogly byc te same koordy
                                coords[0] = r.nextInt(100) + 1;
                                coords[1] = r.nextInt(100) + 1;
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
                                    outMsg = "ERROR";
                                    out.println(outMsg);
                                }
                            } else {
                                outMsg = "ERROR";
                                out.println(outMsg);
                            }
                        }

                    }
                }

                outMsg = "START";

                lock.lock();
                try {
                    while (clientsLogins.size() < PLAYERS) {
                        counter.await();
                    }
                    counter.signal();
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
                finally {
                    lock.unlock();
                }

                //START I PLAYERS
                synchronized (lockobj) {
                    if (clientsLogins.size() == PLAYERS && !statusStatus) {
                        sendLoginToAllTCP("START");
                        StringBuilder outBuffer = new StringBuilder();
                        outBuffer.append("PLAYERS");
                        for (Map.Entry<Integer, int[]> entry : clientsData.entrySet()) {
                            outBuffer.append(" ").append(entry.getValue()[0]).append(" ").append(entry.getValue()[1]);
                        }
                        sendMesageToAll(outBuffer.toString());

                        statusStatus = true;
                    }
                }

                //BEGIN
                if(clientsLogins.size() == PLAYERS) {
                    while (true) {
                        inMsg = in.readLine();
                        if(inMsg == null){
                            outMsg = "ERROR";
                            out.println(outMsg);
                        }
                        else {
                            String beginpart[] = inMsg.split(" ");
                            if (beginpart[0].equals("BEGIN")) {
                                if (directionsBegin.contains(beginpart[1])) {
                                    clientsBeginDirections.put(clientSockets.get(socket), beginpart[1]);
                                    outMsg = "OK";
                                    out.println(outMsg);
                                    beginCounter++;
                                    break;
                                } else {
                                    outMsg = "ERROR";
                                    out.println(outMsg);
                                }
                            } else {
                                outMsg = "ERROR";
                                out.println(outMsg);
                            }
                        }
                    }
                }


                lock.lock();
                try {
                    while (beginCounter < PLAYERS) {
                        counter.await();
                    }
                    counter.signal();
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
                finally {
                    lock.unlock();
                }

                //GAME
                synchronized (lockobj) {
                    if (beginCounter == PLAYERS && !sendGameStatus) {
                        outMsg = "GAME";
                        sendMesageToAll(outMsg);
                        sendGameStatus = true;
                    }
                }

                //GRA
                socket.setSoTimeout(500);
                inMsg = null;
                int counterdisc = 0;
                int lost = 0;
                while(true) {
                    System.out.println(lostCounter);
                   if(lostCounter == PLAYERS - 1)
                    {
                        lostCounter++;
                        outMsg = "WIN";
                        out.println(outMsg);
                        break;
                    }
                    if (counterdisc == 20000) { // powinno byc 6, bo 6*500 = 3s
                        break;
                    }
                    lost = updateBoard();
                    if (socket.equals(getSocketById(lost))) {
                        lostCounter++;
                        outMsg = "LOST";
                        out.println(outMsg);
                        break;
                    }
                    lost = updateBoard();
                    synchronized (lockobj) {
                        if (!sendBoardStatus) {
                            sendMesageToAll("BOARD " + Arrays.deepToString(Board).replace(",", "").replace("[", "").replace("]", ""));
                            sendBoardStatus = true;
                        }
                    }

                        try {
                            clientBeginMove(clientSockets.get(socket), clientsBeginDirections.get(clientSockets.get(socket)));
                            inMsg = in.readLine();
                        } catch (SocketTimeoutException e) {
                            counterdisc++;

                        }
                        if (inMsg != null) {
                            counterdisc = 0;
                            if (inMsg.startsWith("MOVE ")) {
                                String movepart[] = inMsg.split(" ");
                                if (directionsMove.contains(movepart[1])) {
                                    changeDirection(movepart[1], id);
                                    outMsg = "OK";
                                    out.println(outMsg);
                                } else {
                                    outMsg = "ERROR";
                                    out.println(outMsg);
                                }
                            } else {
                                outMsg = "ERROR";
                                out.println(outMsg);
                            }

                            System.out.println(inMsg);
                            inMsg = null;
                        }
                        sendBoardStatus = false;
                }

                lock.lock();
                try {
                    while (lostCounter < PLAYERS) {
                        counter.await();
                    }
                    counter.signal();
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
                finally {
                    lock.unlock();
                }

                synchronized (lockobj){
                    
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
                    assert out != null;
                    out.println("DISCONNECT");
                    clientsData.remove(id);
                    socket.close();
                } catch (IOException | NullPointerException e) {e.printStackTrace();
                }
            }
        }
    }


    public static void changeDirection(String direction, int id){
        String changedDirection = clientsBeginDirections.get(id);
        switch (direction) {
            case "R":
                switch (changedDirection) {
                    case "S":
                    case "N":
                        changedDirection = "E";
                        break;
                    case "E":
                        changedDirection = "S";
                        break;
                    case "W":
                        changedDirection = "N";
                        break;
                }
                break;
            case "L":
                switch (changedDirection) {
                    case "S":
                    case "N":
                        changedDirection = "W";
                        break;
                    case "E":
                        changedDirection = "N";
                        break;
                    case "W":
                        changedDirection = "S";
                        break;
                }
                break;
            case "S":
                changedDirection = changedDirection;
                break;
        }

        clientsBeginDirections.put(id,changedDirection);
    }



    public static void lockCounter(int count){

    }

    public static void clientBeginMove(int id, String direction){
        int[] coords = clientsData.get(id);
        switch (direction) {
            case "S":
                coords[1]++;
                break;
            case "N":
                coords[1]--;
                break;
            case "E":
                coords[0]++;
                break;
            case "W":
                coords[0]--;
                break;
        }
        clientsData.put(id,coords);

    }

    public static int updateBoard(){
        int lost = 0;
        for (Map.Entry<Integer, int[]> entry : clientsData.entrySet()) {
            int id = entry.getKey();
            int[] coords = entry.getValue();
            System.out.println("ID " + id + " " + coords[0] + " " + coords[1]);
            if(coords[0]==0 || coords[1]==0 || coords[0] == 101 || coords[1] == 101){
                lost = id;
            }
            else if((Board[coords[0] - 1][coords[1] - 1])!= 0 && (Board[coords[0] - 1][coords[1] - 1] != id)){
                lost = id;
            }
            else {
                Board[coords[0] - 1][coords[1] - 1] = id;
            }
        }
        return lost;
    }

    public static Socket getSocketById(int id){
        Socket socketToKick = null;
        for(Socket socket: clientSockets.keySet()){
            if(clientSockets.get(socket).equals(id)){
                socketToKick = socket;

            }
        }
        return socketToKick;
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

    private static void sendMesageToAll(String message){
        for (PrintWriter writer : writers) {
            writer.println(message);
        }
    }
}