package Client;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Client {
    public static void main(String[] args)
    {
        try {
            Socket sock = new Socket("localhost",9001);
            SendThread sendThread = new SendThread(sock);
            Thread thread = new Thread(sendThread);thread.start();
            ReceiveThread receiveThread = new ReceiveThread(sock);
            Thread thread2 =new Thread(receiveThread);thread2.start();
            while(true) {
                if (!thread2.isAlive()) {
                    thread.interrupt();
                    break;
                }
            }
        } catch (Exception e) {System.out.println(e.getMessage());}
    }
}
class ReceiveThread implements Runnable
{
    Socket sock=null;
    BufferedReader in =null;
    PrintWriter out = null; //ADDED
    String outMsg;
    static Random r = new Random(); //ADDED

    static char pickRandom(char... letters) {
        return letters[r.nextInt(letters.length)];
    }

    public ReceiveThread(Socket sock) {
        this.sock = sock;
    }//end constructor
    public void run() {
        try{
            in = new BufferedReader(new InputStreamReader(this.sock.getInputStream()));
            out = new PrintWriter(sock.getOutputStream(), true); //ADDED
            String inMsg = null;
            while((inMsg = in.readLine())!= null)
            {
                //ADDED
                if(inMsg.equals("DISCONNECT")){
                    System.out.println("Server: " + inMsg);
                    break;
                }
                else if(inMsg.equals("CONNECT")){
                    Thread.sleep(100);
                    outMsg = "LOGIN s" + r.nextInt(4000);
                    System.out.println("CLIENT: " + outMsg);
                    out.println(outMsg);
                }
                else if(inMsg.startsWith("PLAYERS ")){
                    Thread.sleep(100);
                    outMsg = "BEGIN " + pickRandom('N', 'S', 'E', 'W');
                    System.out.println("CLIENT: " + outMsg);
                    out.println(outMsg);
                }
                System.out.println("Server: " + inMsg);
            }
            sock.close();
        }catch(Exception e){System.out.println(e.getMessage());}
    }
}



class SendThread implements Runnable
{
    private Socket sock=null;
    char testchar;
    String msgtoServerString = "";


    SendThread(Socket sock)
    {
        this.sock = sock;
    }//end constructor
    public void run(){
        try{
            while(sock.isConnected() && !Thread.interrupted() && !sock.isClosed())
            {
                System.out.println("Client connected to "+sock.getInetAddress() + " on port "+sock.getPort());
                PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
                while(!Thread.interrupted()){

/*                    while((testchar = (char)consoleReader.read()) != '\n' ){
                        msgtoServerString += testchar;
                    }*/


                    if(consoleReader.ready()) {
                        String msgtoServerString = consoleReader.readLine();
                        out.println(msgtoServerString);
                    }
                    if(msgtoServerString.equals("DISCONNECT"))
                        break;
                }

            }
            sock.close();
        }catch(Exception e){System.out.println(e.getMessage());}
    }//end run method
}//end class