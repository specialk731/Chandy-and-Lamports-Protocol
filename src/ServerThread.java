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
					
					//If a KILL message is received, it need to be passed to the process. Break out of the loop and end the thread
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
			
		} catch (SocketException e) {
			
		} catch (Exception e) {
			System.out.println("Error in ServerThread: " + e);
		}
	}
}
