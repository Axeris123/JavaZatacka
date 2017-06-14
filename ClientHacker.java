package ClientHacker;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ClientHacker {
    static BufferedReader in =null;
  static  PrintWriter out = null; //ADDED
   static  String outMsg;
    static Random r = new Random(); //ADDED
    static char pickRandom(char... letters) {
        return letters[r.nextInt(letters.length)];
    }
    public static void main(String[] args)
    {
        SecureRandom random = new SecureRandom();

        try {
            int errorCount = 0;
            Socket sock = new Socket("localhost",9001);
                in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                out = new PrintWriter(sock.getOutputStream(), true); //ADDED
                String inMsg = null;
                while((inMsg = in.readLine())!= null)
                {
                    //ADDED
                    if(inMsg.contains("DISCONNECT")){
                        System.out.println("Server: " + inMsg);
                        break;
                    }
                    else if(inMsg.equals("CONNECT")){
                        Thread.sleep(100);
                        outMsg = "LOGIN H4CK3RM4N " + r.nextInt(4000);
                        System.out.println("CLIENT: " + outMsg);
                        out.println(outMsg);
                    }
                    else if(inMsg.startsWith("PLAYERS ")){
                        Thread.sleep(100);
                        outMsg = "BEGIN " + pickRandom('N', 'S', 'E', 'W');
                        System.out.println("CLIENT: " + outMsg);
                        out.println(outMsg);
                    }
                    else if(inMsg.contains("BOARD ")){
                        while(errorCount != 20){
                            String flood = new BigInteger(1000000,random).toString();
                            out.println(flood);
                            System.out.println(flood);
                            inMsg = in.readLine();
                            if(inMsg.equals("ERROR")){
                                errorCount++;
                            }
                        }
                        sock.close();
                    }
                    if(sock.isClosed()) {
                        sock = new Socket("localhost", 9001);
                        errorCount = 0;
                        while (errorCount != 20) {
                            String flood = new BigInteger(1000000, random).toString();
                            out.println(flood);
                            System.out.println(flood);
                            inMsg = in.readLine();
                            if (inMsg.equals("ERROR")) {
                                errorCount++;
                            }
                        }
                        System.out.println("Server: " + inMsg);
                    }
                }
                sock.close();
        } catch (Exception e) {System.out.println(e.getMessage());}
    }
}