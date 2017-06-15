package Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    private static final int PORT = 9001;
    private static int PLAYERS = 4;
    private static final int MAXERRORS = 20;
    private static final int TIMEOUT = 4000;

    private static Random r = new Random();

    private static HashMap<Integer, String> clientsLogins = new HashMap<>();
    private static HashMap<Integer, int[]> clientsData = new HashMap<>();
    private static HashMap<Integer,Integer> clientsErrors = new HashMap<>();
    private static volatile ConcurrentHashMap<Integer, PrintWriter> activePlayers = new ConcurrentHashMap<>();
    private static HashMap<Socket, Integer> clientSockets = new HashMap<>();
    private static HashMap<Integer, ArrayList<Integer>> clientsPositions = new HashMap<>();
    private static final List<String> directionsBegin = Arrays.asList("S","N","E","W");
    private static final List<String> directionsMove = Arrays.asList("R","L","S");
    private static HashMap<Integer, String> clientsBeginDirections = new HashMap<>();
    private static int beginCounter = 0;
    private static int lostCounter = 0;
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
    private static Phaser phaser = new Phaser();




    public static void main(String[] args) throws Exception {
        System.out.println("The server is running.");
        PrintWriter fileWriterErase = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", false)));
        fileWriterErase.close();
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
        private int errorCounter = 0;

        Player(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
            clientSockets.put(socket, id);
            clientsErrors.put(id,errorCounter);
        }

        public void run() {
            try {

                phaser.register();

                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);

                socket.setSoTimeout(TIMEOUT);
                //CONNECT
                outMsg = "CONNECT";

                fileWriter.println("TO ID: " + id + " " + outMsg);
                out.println(outMsg);
                while(true) {
                    try {
                        inMsg = in.readLine();
                    }
                    catch (SocketTimeoutException | SocketException e){
                        timeoutKick(socket);
                    }
                    fileWriter.println("FROM ID: " + id + " " + inMsg);

                    if (inMsg == null) {
                        outMsg = "ERROR";
                        fileWriter.println("TO ID: " + id + " " + outMsg);
                        out.println(outMsg);
                        errorCounter++;
                        clientsErrors.put(id,errorCounter);
                        if(checkErrors(socket)){
                            phaser.arriveAndDeregister();
                            break;
                        }
                    }
                    else{
                        //LOGIN
                        synchronized (lockobj) {
                            if (inMsg.startsWith("LOGIN ") && (inMsg.length() > 6)) {
                                String loginpart[] = inMsg.split(" ");
                                int[] coords = new int[2];
                                coords[0] = r.nextInt(100) + 1;
                                coords[1] = r.nextInt(100) + 1;
                                if (!clientsLogins.values().contains(loginpart[1])) {
                                    clientsLogins.put(id,loginpart[1]);
                                    clientsData.put(id, coords);
                                    outMsg = "OK";
                                    fileWriter.println("TO ID: " + id + " " + outMsg);
                                    out.println(outMsg);
                                    break;
                                } else {
                                    outMsg = "ERROR";
                                    fileWriter.println("TO ID: " + id + " " + outMsg);
                                    out.println(outMsg);
                                    errorCounter++;
                                    clientsErrors.put(id,errorCounter);
                                    checkErrors(socket);
                                }
                            } else {
                                outMsg = "ERROR";
                                fileWriter.println("TO ID: " + id + " " + outMsg);
                                out.println(outMsg);
                                errorCounter++;
                                clientsErrors.put(id,errorCounter);
                                checkErrors(socket);
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
                            try {
                                inMsg = in.readLine();
                            }
                            catch (SocketTimeoutException | SocketException e){
                                timeoutKick(socket);
                                phaser.arriveAndDeregister();
                                break;
                            }
                            fileWriter.println("FROM ID: " + id + " " + inMsg);
                            if (inMsg == null) {
                                outMsg = "ERROR";
                                fileWriter.println("TO ID: " + id + " " + outMsg);
                                out.println(outMsg);
                                errorCounter++;
                                clientsErrors.put(id,errorCounter);
                                if(checkErrors(socket)){
                                    phaser.arriveAndDeregister();
                                    break;
                                }
                            } else {
                               synchronized (lockobj) {
                                    String beginpart[] = inMsg.split(" ");
                                    if (inMsg.startsWith("BEGIN ") && (inMsg.length() > 6)) {
                                        if (directionsBegin.contains(beginpart[1])) {
                                            clientsBeginDirections.put(clientSockets.get(socket), beginpart[1]);
                                            outMsg = "OK";
                                            fileWriter.println("TO ID: " + id + " " + outMsg);
                                            out.println(outMsg);
                                            beginCounter++;
                                            break;
                                        } else {
                                            outMsg = "ERROR";
                                            fileWriter.println("TO ID: " + id + " " + outMsg);
                                            out.println(outMsg);
                                            errorCounter++;
                                            clientsErrors.put(id,errorCounter);
                                            checkErrors(socket);
                                        }
                                    } else {
                                        outMsg = "ERROR";
                                        fileWriter.println("TO ID: " + id + " " + outMsg);
                                        out.println(outMsg);
                                        errorCounter++;
                                        clientsErrors.put(id,errorCounter);
                                        checkErrors(socket);
                                    }
                               }
                            }
                        }
                    }

                    if(socket.isConnected() && !socket.isClosed()){
                        waiter = new Waiter(socket);
                        waiter.start();
                        phaser.arriveAndAwaitAdvance();
                        waiter.interrupt();



                        //GAME
                        synchronized (lockobj) {
                            if (beginCounter == PLAYERS && !sendGameStatus) {
                                outMsg = "GAME";
                                sendMesageToAll(outMsg);
                                phaser = new Phaser();
                                sendGameStatus = true;
                            }
                        }
                    }
                //GRA
                int counterdisc = 0;
                 while(ROUNDS <=5 ) {
                     if(socket.isConnected() && !socket.isClosed()) {
                         socket.setSoTimeout(500);
                     }
                     else{
                         break;
                     }
                    synchronized (lockobj){
                        activePlayers.put(id, out);
                        phaser.register();
                    }


                    inMsg = null;

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


                    while (true) {

                        PrintWriter testWriter = activePlayers.get(id);
                        synchronized (lockobj) {
                            if (testWriter != null && !setBoardSender) {
                                sendBoardID = id;
                                setBoardSender = true;
                            }
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
                            if(counterdisc == 2000){
                                timeoutKick(socket);
                                phaser.arriveAndDeregister();
                                break;
                            }

                        }
                        catch (SocketException e){
                            timeoutKick(socket);
                            phaser.arriveAndDeregister();
                            break;
                        }

                        if (inMsg != null) {
                            counterdisc = 0;
                            fileWriter.println("FROM ID: " + id + " " + inMsg);
                            if (inMsg.startsWith("MOVE ")) {
                                String movepart[] = inMsg.split(" ");
                                if (directionsMove.contains(movepart[1])) {
                                    changeDirection(movepart[1], id);
                                    outMsg = "OK";
                                    fileWriter.println("TO ID: " + id + " " + outMsg);
                                    out.println(outMsg);
                                } else {
                                    outMsg = "ERROR";
                                    fileWriter.println("TO ID: " + id + " " + outMsg);
                                    out.println(outMsg);
                                    errorCounter++;
                                    clientsErrors.put(id,errorCounter);
                                    checkErrors(socket);
                                }
                            } else {
                                outMsg = "ERROR";
                                fileWriter.println("TO ID: " + id + " " + outMsg);
                                out.println(outMsg);
                                errorCounter++;
                                clientsErrors.put(id,errorCounter);
                                checkErrors(socket);
                            }

                            System.out.println(inMsg);
                            inMsg = null;
                        }

                      synchronized (lockobj) {
                            clientBeginMove(clientSockets.get(socket), clientsBeginDirections.get(clientSockets.get(socket)));
                        }

                        phaser.arriveAndAwaitAdvance();

                        synchronized (lockobj){
                            if (sendBoardID == id) {
/*                                System.out.println("PHASER REGISTERD" + phaser.getRegisteredParties());
                                System.out.println("PHASER UNARRIVED" + phaser.getUnarrivedParties());*/
                                updateBoard();
                            }
                        }

                        if (!lostArr.isEmpty()) {

                            arrSizeBeforeRemoval = lostArr.size();
                            if(activePlayers.size() == arrSizeBeforeRemoval){
                                lostCounter = PLAYERS;
                                lastTwoRemaining = true;
                            }
                            if (lostArr.contains(id)) {
                                outMsg = "LOST " + position;
                                positions.add(position);
                                clientsPositions.put(id, positions);
                                activePlayers.remove(id);
                                phaser.arriveAndDeregister();
                                if (id == sendBoardID) {
                                    setBoardSender = false;
                                }
                                lostArr.remove(id);
                                fileWriter.println("TO ID: " + id + " " + outMsg);
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
                                    }
                                    lock.unlock();
                                    editPosition = false;
                                }
                                break;
                            }

                        }
                         if (activePlayers.size() == 1) {
                            outMsg = "WIN";
                            positions.add(1);
                            clientsPositions.put(id,positions);
                            activePlayers.remove(id);
                            fileWriter.println("TO ID: " + id + " " + outMsg);
                            out.println(outMsg);
                            lostCounter = PLAYERS;
                            phaser.arriveAndDeregister();
                            break;
                        }

                        synchronized (lockobj) {
                            if (id == sendBoardID) {
                                sendBoardStatus = false;
                            }
                        }
                        phaser.arriveAndAwaitAdvance();
                    }


                    if(!socket.isClosed()) {
                        waiter = new Waiter(socket);
                    }
                     lock.lock();
                     try {
                         if(activePlayers.size() == 0){
                             counter.signalAll();
                         }
                         if(!socket.isClosed()) {
                             waiter.start();
                         }
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
                         if(!socket.isClosed()) {
                             waiter.interrupt();
                         }
                     }

                     if(socket.isClosed() || PLAYERS == 1){
                         break;
                     }


                     if(ROUNDS>=5){
                         break;
                     }
                     else {

                         synchronized (lockobj) {
                             if (!sendRoundStatus) {
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
                                 phaser = new Phaser();
                             }

                         }
                     }

                     phaser.register();

                     socket.setSoTimeout(TIMEOUT);


                        while (true) {
                            try {
                                inMsg = in.readLine();
                            }
                            catch (SocketTimeoutException e){
                                timeoutKick(socket);
                                phaser.arriveAndDeregister();
                                break;
                            }
                            catch (SocketException e){
                                timeoutKick(socket);
                                phaser.arriveAndDeregister();
                                break;
                            }
                            fileWriter.println("FROM ID: " + id + " " + inMsg);
                            if(inMsg == null){
                                outMsg = "ERROR";
                                fileWriter.println("TO ID: " + id + " " + outMsg);
                                out.println(outMsg);
                                errorCounter++;
                                clientsErrors.put(id,errorCounter);
                                if(checkErrors(socket)){
                                    phaser.arriveAndDeregister();
                                    break;
                                }
                            }
                            else {
                                synchronized (lockobj) {
                                    String beginpart[] = inMsg.split(" ");
                                    if (inMsg.startsWith("BEGIN ") && (inMsg.length() > 6)) {
                                        if (directionsBegin.contains(beginpart[1])) {
                                            clientsBeginDirections.put(clientSockets.get(socket), beginpart[1]);
                                            outMsg = "OK";
                                            fileWriter.println("TO ID: " + id + " " + outMsg);
                                            out.println(outMsg);
                                            beginCounter++;
                                            break;
                                        } else {
                                            outMsg = "ERROR";
                                            fileWriter.println("TO ID: " + id + " " + outMsg);
                                            out.println(outMsg);
                                            errorCounter++;
                                            clientsErrors.put(id,errorCounter);
                                            checkErrors(socket);
                                        }
                                    } else {
                                        outMsg = "ERROR";
                                        fileWriter.println("TO ID: " + id + " " + outMsg);
                                        out.println(outMsg);
                                        errorCounter++;
                                        clientsErrors.put(id,errorCounter);
                                        checkErrors(socket);
                                    }
                                }
                            }
                        }



                    if(socket.isConnected() && !socket.isClosed()) {
                        waiter = new Waiter(socket);
                        waiter.start();

                        phaser.arriveAndAwaitAdvance();
                        waiter.interrupt();




                        synchronized (lockobj) {
                            if (!increaseRoundsVar) {
                                lostCounter = 0;
                                Board = new int[100][100];
                                position = PLAYERS;
                                lostArr = new HashSet<>();
                                ROUNDS++;
                                increaseRoundsVar = true;
                                sendRoundStatus = false;
                                activePlayers = new ConcurrentHashMap<>();
                                phaser=new Phaser();
                            }

                        }
                    }



                 }

                 if(socket.isConnected() && !socket.isClosed()) {
                     synchronized (lockobj) {
                         if (!sendRankingStatus) {
                             System.out.println("MOJE ID TO " + id);
                             String ranking = getRanking();
                             sendMesageToAll(ranking);
                             sendMesageToAll("DISCONNECT");
                             sendRankingStatus = true;
                         }
                         fileWriter.close();
                     }
                 }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    assert out != null;
                    PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                    fileWriter.println("TO ID: " + id + " " + outMsg);
                    outMsg = "DISCONNECT";
                    out.println(outMsg);
                    if(!socket.isClosed() && socket.isConnected()) {
                        PLAYERS--;
                        socket.close();
                        clientsErrors.remove(id);
                        clientSockets.remove(socket);
                        clientsBeginDirections.remove(id);
                        clientsData.remove(id);
                        clientsPositions.remove(id);
                        clientsLogins.remove(id);
                    }
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
        private String outMsg;
        private int id;
        private int errorCount;


        Waiter(Socket socket){
            this.socket = socket;
            this.id = clientSockets.get(this.socket);
            this.errorCount = clientsErrors.get(this.id);
        }

        public void run(){
            if(socket.isConnected() && !socket.isClosed()) {
                try {
                    PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                    while (!isInterrupted()) {

                        in = new BufferedReader(new InputStreamReader(
                                socket.getInputStream()));
                        out = new PrintWriter(socket.getOutputStream(), true);

                        if (in.ready()) {
                            inMsg = in.readLine();
                            outMsg = "ERROR FROM WAITER";
                            fileWriter.println("FROM ID: " + clientSockets.get(socket) + " " + inMsg);
                            fileWriter.println("TO ID: " + clientSockets.get(socket) + " " + outMsg);
                            out.println(outMsg);
                            errorCount++;
                            clientsErrors.put(id, errorCount);
                            checkErrors(socket);
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }


    private static boolean checkErrors(Socket socket){
        if(socket.isConnected() && !socket.isClosed()) {
            int id = clientSockets.get(socket);
            int errorCount = clientsErrors.get(id);
            PrintWriter activeID = activePlayers.get(id);
            String outMsg = "MAX ERROR COUNT DISCONNECT";
            try {
                PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                PrintWriter socketWriter = new PrintWriter(socket.getOutputStream(), true);
                if (errorCount == MAXERRORS) {
                    fileWriter.println("TO ID: " + id + " " + outMsg);
                    socketWriter.println(outMsg);
                    clientsErrors.remove(id);
                    clientSockets.remove(socket);
                    if (clientsBeginDirections.containsKey(id)) {
                        clientsBeginDirections.remove(id);
                    }
                    if (clientsData.containsKey(id)) {
                        clientsData.remove(id);
                    }
                    if (clientsPositions.containsKey(id)) {
                        clientsPositions.remove(id);
                    }
                    if (clientsLogins.containsKey(id)) {
                        clientsLogins.remove(id);
                    }
                    if (activeID != null) {
                        activePlayers.remove(id);
                        if (id == sendBoardID) {
                            setBoardSender = false;
                        }
                        lostCounter++;
                    }
                    PLAYERS--;
                    socket.close();
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;

    }

    private static void timeoutKick(Socket socket){
        String outMsg = "TIMEOUT DISCONNECT";
        if(clientSockets.get(socket)!= null) {
            int id = clientSockets.get(socket);
            PrintWriter activeID = activePlayers.get(id);

            try {
                PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                PrintWriter socketWriter = new PrintWriter(socket.getOutputStream(), true);
                fileWriter.println("TO ID: " + id + " " + outMsg);
                socketWriter.println(outMsg);
                clientsErrors.remove(id);
                clientSockets.remove(socket);
                if (clientsBeginDirections.containsKey(id)) {
                    clientsBeginDirections.remove(id);
                }
                if (clientsData.containsKey(id)) {
                    clientsData.remove(id);
                }
                if (clientsPositions.containsKey(id)) {
                    clientsPositions.remove(id);
                }
                if (clientsLogins.containsKey(id)) {
                    clientsLogins.remove(id);
                }
                if (activeID != null) {
                    activePlayers.remove(id);
                    if (id == sendBoardID) {
                        setBoardSender = false;
                    }
                    lostCounter++;
                }
                PLAYERS--;
                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static String getRanking(){
        HashMap<Integer,Integer> map = new HashMap<>();

        int sum;
        int id;
        ArrayList<Integer> positions;

        StringBuilder outBuffer = new StringBuilder();

        for (Map.Entry<Integer, ArrayList<Integer>> entry : clientsPositions.entrySet()){
            sum = 0;
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
    static void changeDirection(String direction, int id){
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


    static void clientBeginMove(int id, String direction){
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

    static void updateBoard(){
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


    static void sendLoginToAllTCP(String message) {
        String startMessage = message;
        for (Map.Entry<Socket, Integer> entry: clientSockets.entrySet()) {
            if (entry != null) {
                try {
                    PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                    PrintWriter socketWriter = new PrintWriter(entry.getKey().getOutputStream(),true);
                    message = startMessage;
                    message = message + " " + entry.getValue();
                    fileWriter.println("TO ID: " + entry.getValue() + " " + message);
                    socketWriter.println(message);
                } catch (IOException e) {
                    System.out.println("Caught an IO exception trying "
                            + "to send to TCP connections");
                    e.printStackTrace();
                }
            }
        }
    }


    static void sendBoardToActiveTCP(String message) {
        for (Map.Entry<Integer, PrintWriter> entry: activePlayers.entrySet()) {
            if (entry != null) {
                try {
                    PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                    PrintWriter socketWriter = entry.getValue();
                    fileWriter.println("TO ID: " + entry.getKey() + " " + message);
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
        for (Map.Entry<Socket, Integer> entry: clientSockets.entrySet()) {
            if (entry != null) {
                try {
                    PrintWriter fileWriter = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)), true);
                    PrintWriter socketWriter = new PrintWriter(entry.getKey().getOutputStream(),true);
                    fileWriter.println("TO ID: " + entry.getValue() + " " + message);
                    socketWriter.println(message);
                } catch (IOException e) {
                    System.out.println("Caught an IO exception trying "
                            + "to send to TCP connections");
                    e.printStackTrace();
                }
            }
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

/*class ValueComparator<Integer extends Comparable<Integer>> implements Comparator<Integer>{

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
}*/


