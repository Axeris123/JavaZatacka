package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    private static final int PORT = 9001;
    private static final int PLAYERS = 8;

    private static Random r = new Random();

    private static HashMap<Integer, String> clientsLogins = new HashMap<>();
    private static HashMap<Integer, int[]> clientsData = new HashMap<>();
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    private static volatile ConcurrentHashMap<Integer, PrintWriter> activePlayers = new ConcurrentHashMap<>();
    private static HashMap<Socket, Integer> clientSockets = new HashMap<>();
    private static HashMap<Integer, ArrayList<Integer>> clientsPositions = new HashMap<>();
    private static final List<String> directionsBegin = Arrays.asList("S","N","E","W");
    private static final List<String> directionsMove = Arrays.asList("R","L","S");
    private static HashMap<Integer, String> clientsBeginDirections = new HashMap<>();
    private static int beginCounter = 0;
    private static int lostCounter = 0;
    private static int moveCounter = 0;
    private static HashSet<Integer> lostArr = new HashSet<>();
    private static int ROUNDS = 1;
    private static int position = PLAYERS;
    private static int arrSizeBeforeRemoval = 0;
    private static int sendBoardID;
    private static boolean statusStatus = false;
    private static boolean sendGameStatus = false;
    private static boolean sendBoardStatus = false;
    private static boolean sendRoundStatus = false;
    private static boolean sendRankingStatus = false;
    private static boolean setBoardSender = false;
    private static boolean increaseRoundsVar = false;
    private static boolean editPosition = false;
    private static boolean lastTwoRemaining = false;
    private static Lock lock = new ReentrantLock();
    private static Condition counter = lock.newCondition();
    private static final Object lockobj = new Object();
    private static int[][] Board = new int[100][100];
    private static CyclicBarrier barrier;




    public static void main(String[] args) throws Exception {
        System.out.println("The server is running.");
        try (ServerSocket listener = new ServerSocket(PORT)) {
            for(int i = 1; i<= PLAYERS;i++) {
                new Player(listener.accept(), i).start();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Player extends Thread {
        private String inMsg;
        private String outMsg;
        private Socket socket;
        private ArrayList<Integer> positions = new ArrayList<>();
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
                        synchronized (lockobj) {
                            if (inMsg.startsWith("LOGIN ") && (inMsg.length() > 6)) {
                                int[] coords = new int[2];
                                coords[0] = r.nextInt(100) + 1;
                                coords[1] = r.nextInt(100) + 1;
                                if (!clientsLogins.values().contains(loginpart[1])) {
                                    clientsLogins.put(id,loginpart[1]);
                                    clientsData.put(id, coords);
                                    outMsg = "OK";
                                    out.println(outMsg);
                                    writers.add(out);
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

                Waiter waiter = new Waiter(socket);
                lock.lock();
                try {
                    waiter.start();
                    while (clientsLogins.size() < PLAYERS) {
                        counter.await();
                    }
                    counter.signalAll();
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
                finally {
                    lock.unlock();
                    waiter.interrupt();
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
                    if (clientsLogins.size() == PLAYERS) {
                        while (true) {
                            inMsg = in.readLine();
                            if (inMsg == null) {
                                outMsg = "ERROR";
                                out.println(outMsg);
                            } else {
                               synchronized (lockobj) {
                                    String beginpart[] = inMsg.split(" ");
                                    if (inMsg.startsWith("BEGIN ") && (inMsg.length() > 6)) {
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
                    }

                    waiter = new Waiter(socket);
                    lock.lock();
                    try {
                        waiter.start();
                        while (beginCounter < PLAYERS) {
                       //     System.out.println("BEGINCOUNTER " + beginCounter);
                            counter.await();
                        }
                        counter.signalAll();
                    }
                    catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    finally {
                        lock.unlock();
                        waiter.interrupt();
                    }


//System.out.println("PRZESZEDLEM 1 BEGIN !!!");
                //GAME
                synchronized (lockobj) {
                    if (beginCounter == PLAYERS && !sendGameStatus) {
                        outMsg = "GAME";
                        sendMesageToAll(outMsg);
                        sendGameStatus = true;
                    }
                }



                //GRA
                 while(ROUNDS <=5 ) {
                    socket.setSoTimeout(300);
                    synchronized (lockobj){
                        activePlayers.put(id, out);
                    }


                    inMsg = null;
                    int counterdisc = 0;


                     lock.lock();
                     try {
                         while (activePlayers.size() != PLAYERS) {
                             counter.await();
                         }
                         counter.signal();
                     } catch (InterruptedException e) {
                         e.printStackTrace();
                     } finally {
                         lock.unlock();
                     }


                    barrier = new CyclicBarrier(activePlayers.size());
                    while (true) {

                        PrintWriter testWriter = activePlayers.get(id);
                        synchronized (lockobj) {
                            if (testWriter != null && !setBoardSender) {
                                sendBoardID = id;
                                setBoardSender = true;
                            }
                        }

                        if (activePlayers.size() == 1) {
                            outMsg = "WIN";
                            positions.add(1);
                            clientsPositions.put(id,positions);
                            activePlayers.remove(id);
                            out.println(outMsg);
                            lostCounter = PLAYERS;
                            break;
                        }
                        if (counterdisc == 20000) { // powinno byc 6, bo 6*500 = 3s
                            break;
                        }


                        synchronized (lockobj) {
                            if (!sendBoardStatus && id == sendBoardID) {
                                sendBoardToActiveTCP(id + " BOARD " + Arrays.deepToString(Board).replace(",", "").replace("[", "").replace("]", ""));
                                sendBoardStatus = true;
                            }
                        }

                        try {
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

                        synchronized (lockobj) {
                            clientBeginMove(clientSockets.get(socket), clientsBeginDirections.get(clientSockets.get(socket)));
                            moveCounter++;
                        }

                        try{
                            barrier.await(500, TimeUnit.MILLISECONDS);
                        }
                        catch (InterruptedException|BrokenBarrierException | TimeoutException ex){
                        }

                        synchronized (lockobj){
                            if (sendBoardID == id) {
                                updateBoard();
                            }
                        }
                            if (!lostArr.isEmpty()) {

                                arrSizeBeforeRemoval = lostArr.size();
                                if(activePlayers.size() == arrSizeBeforeRemoval){
                                    lostCounter = PLAYERS;
                                    lastTwoRemaining = true;
                                }
                                System.out.println("ARRAY SIZE " + arrSizeBeforeRemoval);
                               if (lostArr.contains(id)) {
                                   outMsg = "LOST " + position;
                                   positions.add(position);
                                   clientsPositions.put(id, positions);
                                   activePlayers.remove(id);
                                   if (id == sendBoardID) {
                                       setBoardSender = false;
                                   }
                                   lostArr.remove(id);
                                   out.println(outMsg);


                                   lock.lock();
                                   try {
                                       while (!lostArr.isEmpty()) {
                                           counter.await();
                                       }
                                       counter.signal();
                                   } catch (InterruptedException e) {
                                       e.printStackTrace();
                                   } finally {
                                       synchronized (lockobj){
                                           if (!editPosition && !lastTwoRemaining) {
                                               position -= arrSizeBeforeRemoval;
                                               lostCounter += arrSizeBeforeRemoval;
                                               arrSizeBeforeRemoval = 0;
                                               editPosition = true;
                                           }
                                           if(activePlayers.size() > 0 && (activePlayers.size() != arrSizeBeforeRemoval)) {
                                               barrier = new CyclicBarrier(activePlayers.size());
                                           }
                                       }
                                       lock.unlock();

                                       editPosition = false;
                                   }

                                    break;
                                }

                            }




                        synchronized (lockobj) {
                            if (id == sendBoardID) {
                                sendBoardStatus = false;
                                System.out.println("POSITION NUMBER " + position);
                                System.out.println("LOST COUNTER " + lostCounter);
                            }
                        }



                    }



                     waiter = new Waiter(socket);
                     lock.lock();
                     try {
                         waiter.start();
                         while (lostCounter < PLAYERS) {
                             counter.await();
                         }
                         counter.signalAll();
                     }
                     catch (InterruptedException e){
                         e.printStackTrace();
                     }
                     finally {
                         lock.unlock();
                         waiter.interrupt();
                     }


                     if(ROUNDS>=5){
                         break;
                     }
                     else {
                         synchronized (lockobj) {

                             if (lostCounter == PLAYERS && !sendRoundStatus) {
                                 outMsg = "ROUND " + (ROUNDS + 1);
                                 sendMesageToAll(outMsg);
                                 randomizeDirections();
                                 StringBuilder outBuffer = new StringBuilder();
                                 outBuffer.append("PLAYERS");
                                 for (Map.Entry<Integer, int[]> entry : clientsData.entrySet()) {
                                     outBuffer.append(" ").append(entry.getValue()[0]).append(" ").append(entry.getValue()[1]);
                                 }
                                 sendMesageToAll(outBuffer.toString());
                                 sendRoundStatus = true;
                                 increaseRoundsVar = false;
                                 setBoardSender = false;
                                 beginCounter = 0;
                                 activePlayers = new ConcurrentHashMap<>();
                             }
                         }
                     }
                     socket.setSoTimeout(0);


                     try{
                         barrier.await();
                     }
                     catch (InterruptedException|BrokenBarrierException e){
                         e.printStackTrace();
                     }


                        while (true) {
                            inMsg = in.readLine();
                            if(inMsg == null){
                                outMsg = "ERROR";
                                out.println(outMsg);
                            }
                            else {
                                synchronized (lockobj) {
                                    String beginpart[] = inMsg.split(" ");
                                    if (inMsg.startsWith("BEGIN ") && (inMsg.length() > 6)) {
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


                        waiter = new Waiter(socket);
                    lock.lock();
                    try {
                        waiter.start();
                        while (beginCounter < PLAYERS) {
                            counter.await();
                        }
                        counter.signalAll();
                    }
                    catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    finally {
                        lock.unlock();
                        waiter.interrupt();
                    }
             //       System.out.println("PRZESZEDLEM BEGINCOUNT LOCKA!!");
                        sendRoundStatus = false;

                     synchronized (lockobj){
                         if(!increaseRoundsVar){
                             lostCounter = 0;
                             Board = new int[100][100];
                             position = PLAYERS;
                             lostArr = new HashSet<>();
                             ROUNDS++;
                             increaseRoundsVar = true;
                         }
                     //    System.out.println(beginCounter);

                     }

                     try{
                         barrier.await();
                     }
                     catch (InterruptedException|BrokenBarrierException e){
                         e.printStackTrace();
                     }


                 }


                synchronized (lockobj){
                    if(!sendRankingStatus) {
                        String ranking = getRanking();
                        sendMesageToAll(ranking);
                        sendMesageToAll("DISCONNECT");
                        sendRankingStatus = true;
                    }
                }
   /*             synchronized (lockobj){
                    System.out.println("TU TEZ JESTEM");
                    if(!disconnectAll){

                        disconnectAll = true;
                    }
                }*/


            } catch (IOException e) {
                e.printStackTrace();
            } finally {
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

    private static class Waiter extends Thread{
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String inMsg;

        Waiter(Socket socket){
            this.socket = socket;
        }

        public void run(){
            try{
            while(!isInterrupted()) {

                    in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream()));
                    out = new PrintWriter(socket.getOutputStream(), true);

                    if (in.ready()) {
                        inMsg = in.readLine();
                        out.println("ERROR FROM WAITER");
                    }

                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    private static String getRanking(){
        //HashMap<String, Integer> map = new HashMap<>();
        HashMap<Integer,Integer> map = new HashMap<>();

        String login;
        int sum;
        int id;
        ArrayList<Integer> positions;

        StringBuilder outBuffer = new StringBuilder();

        for (Map.Entry<Integer, ArrayList<Integer>> entry : clientsPositions.entrySet()){
            sum = 0;
          //  login = clientsLogins.get(entry.getKey());
            id = entry.getKey();
            positions = entry.getValue();
            for(Integer position : positions){
                sum+=position;
            }
            map.put(id , sum);

        }

        Map<Integer,Integer> finalpositions = createSortedMap(map);


        outBuffer.append("ENDGAME ");

        for(Map.Entry<Integer,Integer> entry : finalpositions.entrySet()) {
            String key = clientsLogins.get(entry.getKey());
          //  String key = entry.getKey();
            //int key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println(key + " "+ value);

            outBuffer.append(key).append(" ").append(value).append(" ");
        }
        return outBuffer.toString();



    }

    private static void randomizeDirections(){
        for (int id : clientsData.keySet()) {
            int[] coords = new int[2];
            coords[0] = r.nextInt(100) + 1;
            coords[1] = r.nextInt(100) + 1;
            clientsData.put(id,coords);
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

    public static void updateBoard(){
            for (Map.Entry<Integer, int[]> entry : clientsData.entrySet()) {
                for (Integer activeID : activePlayers.keySet()) {
                    int id = entry.getKey();
                    int[] coords = entry.getValue();
                    if (activeID == id) {
                        System.out.println("ID " + id + " " + coords[0] + " " + coords[1]);
                        if (coords[0] < 1 || coords[1] < 1 || coords[0] > 100 || coords[1] > 100) {
                            lostArr.add(id);
                        } else if ((Board[coords[0] - 1][coords[1] - 1]) != 0 ) {
                            System.out.println("ID " + id + " mLOST AT " + coords[0] + " " + coords[1] + " BECAUSE OF ID " + Board[coords[0] - 1][coords[1] - 1]);
                            lostArr.add(id);
                        } else {
                            Board[coords[0] - 1][coords[1] - 1] = id;
                        }
                    }
                }
            }

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


    public static void sendBoardToActiveTCP(String message) {
        for(PrintWriter writer : activePlayers.values()){
            writer.println(message);
        }
    }

    private static void sendMesageToAll(String message){
        for (PrintWriter writer : writers) {
            writer.println(message);
        }
    }

    private static Map<Integer, Integer> createSortedMap(Map<Integer, Integer> passedMap) {
        List<Map.Entry<Integer, Integer>> entryList = new ArrayList<>(passedMap.entrySet());

        Collections.sort(entryList, (e1, e2) -> {
            if (!e1.getValue().equals(e2.getValue())) {
                return e1.getValue().compareTo(e2.getValue()); // The * -1 reverses the order.
            } else {
                return e1.getKey().compareTo(e2.getKey());
            }
        });

        Map<Integer, Integer> orderedMap = new LinkedHashMap<Integer, Integer>();

        for (Map.Entry<Integer, Integer> entry : entryList) {
            orderedMap.put(entry.getKey(), entry.getValue());
        }

        return orderedMap;
    }


}

class ValueComparator<Integer extends Comparable<Integer>> implements Comparator<Integer>{

    HashMap<Integer, Integer> map = new HashMap<Integer,Integer>();

    public ValueComparator(HashMap<Integer,Integer> map){
        this.map.putAll(map);
    }

    @Override
    public int compare(Integer s1, Integer s2) {

        if (map.get(s1).compareTo(map.get(s2)) == -1)
        {
            return map.get(s1).compareTo(map.get(s2));
        }
        else{
            return -map.get(s1).compareTo(map.get(s2));

        }
    }
}


