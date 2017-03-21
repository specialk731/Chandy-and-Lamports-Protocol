import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

class Server extends Thread{
	String myAddress;
	int myPort;
	static boolean serverOn = true;
	static ServerSocket serversocket;
	List<Thread> threads = new ArrayList<>();
	
	Server(String s ,String s2){
		myAddress = s;
		myPort = Integer.parseInt(s2);
	}
	
	public void run(){
		
		try{
			serversocket = new ServerSocket(myPort);		
			
			for(int i = 0; i < Program.addresses.size()-1; i++){
				Socket s = serversocket.accept();
				threads.add(new ServerThread(s));
				threads.get(i).start();
			}
		} catch(Exception e){
			System.out.println("Error in Server: " + e);
		}
		
		for(int i=0; i<threads.size(); i++) {
			try {
				threads.get(i).join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("End of Server");
	}

	public void TurnOffServer() throws IOException {
		serversocket.close();
	}
	
}