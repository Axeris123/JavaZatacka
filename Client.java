package Client;

import java.io.*;
import java.net.*;

public class Client {
    public static void main(String[] args)
    {
        try {
            Socket sock = new Socket("localhost",9001);
            SendThread sendThread = new SendThread(sock);
            Thread thread = new Thread(sendThread);thread.start();
            ReceiveThread receiveThread = new ReceiveThread(sock);
            Thread thread2 =new Thread(receiveThread);thread2.start();
        } catch (Exception e) {System.out.println(e.getMessage());}
    }
}
class ReceiveThread implements Runnable
{
    Socket sock=null;
    BufferedReader in =null;

    public ReceiveThread(Socket sock) {
        this.sock = sock;
    }//end constructor
    public void run() {
        try{
            in = new BufferedReader(new InputStreamReader(this.sock.getInputStream()));
            String inMsg = null;
            while((inMsg = in.readLine())!= null)
            {
                System.out.println("From Server: " + inMsg);
            }
        }catch(Exception e){System.out.println(e.getMessage());}
    }
}



class SendThread implements Runnable
{
    Socket sock=null;
    PrintWriter out =null;
    BufferedReader consoleReader =null;

    public SendThread(Socket sock)
    {
        this.sock = sock;
    }//end constructor
    public void run(){
        try{
            if(sock.isConnected())
            {
                System.out.println("Client connected to "+sock.getInetAddress() + " on port "+sock.getPort());
                out = new PrintWriter(sock.getOutputStream(), true);
                while(true){
                    consoleReader = new BufferedReader(new InputStreamReader(System.in));
                    String msgtoServerString=null;
                    msgtoServerString = consoleReader.readLine();
                    out.println(msgtoServerString);
                    out.flush();

                    if(msgtoServerString.equals("EXIT"))
                        break;
                }//end while
                sock.close();}}catch(Exception e){System.out.println(e.getMessage());}
    }//end run method
}//end class