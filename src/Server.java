import java.io.*;
import java.net.*;

class Server extends Thread{
	String myAddress;
	int myPort;
	static boolean serverOn = true;
	static ServerSocket serversocket;
	
	Server(String s ,String s2){
		myAddress = s;
		myPort = Integer.parseInt(s2);
	}
	
	public void run(){
		
		try{
		serversocket = new ServerSocket(myPort);		
		
		for(int i = 0; i < Program.numNeighbors; i++){
			Socket s = serversocket.accept();
			new ServerThread(s).start();
		}
		
		} catch(Exception e){
			System.out.println("Error in Server: " + e);
		}
		
		System.out.println("End of Server");
	}

	public void TurnOffServer() throws IOException {
		serversocket.close();
	}
	
}