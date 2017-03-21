import java.io.*;
import java.net.*;

class ServerThread extends Thread{
	Socket socket = null;
	int node;
	Message m = new Message(Program.myNode, 0, "Message");
	
	public ServerThread(Socket s){
		socket = s;
	}
	
	public void run(){
		ObjectOutputStream oos = null;
		ObjectInputStream ois = null;
		
		try{
			ois = new ObjectInputStream(socket.getInputStream());
			oos = new ObjectOutputStream(socket.getOutputStream());
			
			node = ois.readInt();
					
			if(node >= 0) {
				do {
					m = (Message)ois.readObject();
					
					if(m.GetMessage().compareTo("KILL") == 0) {
						break;
					}
					
					Program.MessageQ.get(node).put(m);
				}
				while(!m.GetMessage().equals("END"));
			} else {
				System.out.println("Got a bad Node");
			}
			
			ois.close();
			oos.close();
			socket.close();
			
		} catch (Exception e) {
			System.out.println("Error in ServerThread: " + e);
		}
	}
}