import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Program {
	static int minPerActive, maxPerActive, minSendDelay, snapshotDelay, maxNumber;
	static int neighborsNode[], numNeighbors, myNode, myRound;
	static String parentAddress;
	static List<String> children = new ArrayList<>();
	static String address[], port[], myAddress, myPort;
	static List<LinkedBlockingQueue<Message>> MessageQ; //List of FIFO Blocking queue of messages
	
	public static void main(String[] args) { //
		setup(args);
		
		boolean isActive, isSnapshoting = false, isTreeBuilding = true;
		
		int totalSentMsgs = 0;
		int roundSentMsgs = 0;
		
		Random rand = new Random();
		
		if(myNode == 0) {
			isActive = true;
		} else {
			int randNum = rand.nextInt();
			isActive = (randNum%2 == 0) ? true : false;
		}
		
		
		int i = 0;
		myRound = 0;
		MessageQ = new ArrayList<LinkedBlockingQueue<Message>>(numNeighbors);
		
		for(i = 0; i < numNeighbors; i++)
			MessageQ.add(i, new LinkedBlockingQueue<Message>(100));
				
		Message m = null;
		
		Thread Server = new Server(myAddress, myPort);
		Server.start();
		
		Socket clients[] = new Socket[numNeighbors];
		ObjectOutputStream[] oos = new ObjectOutputStream[numNeighbors];
		
		try{
			Thread.sleep(1000);
			for(i = 0; i < numNeighbors; i++){
				clients[i] = new Socket(address[i], Integer.parseInt(port[i]));
				oos[i] = new ObjectOutputStream(clients[i].getOutputStream());
							
				oos[i].writeInt(myNode);
				}
			}
		catch(Exception e){
				System.out.println("Got Error in Client Setup: " + e);
		}
				
		startTree(oos);
		
		try{
			boolean done = false;
			boolean treeReply = false;
			boolean treeSent = (myNode == 0) ? true : false;
			
			long timeForNextAppSend = System.currentTimeMillis();
			
			List<String> treeMsgsReceived = new ArrayList<>();
			List<String> treeTemp = new ArrayList<>();
			
			//Start Algorithm
			do{
				//Send message to a random neighbor
				if(!isSnapshoting && isActive && !isTreeBuilding && timeForNextAppSend <= System.currentTimeMillis()) {
					int index = rand.nextInt() % neighborsNode.length;
					oos[index].writeObject(new Message(myAddress,address[index],"app"));
					timeForNextAppSend = System.currentTimeMillis() + minSendDelay;
					roundSentMsgs++;
					totalSentMsgs++;
					
					if(roundSentMsgs >= maxPerActive) {
						isActive = false;
						roundSentMsgs = 0;
					} else if(roundSentMsgs >= minPerActive) {
						if(rand.nextInt()%2 == 0) {
							isActive = false;
							roundSentMsgs = 0;
						}
					}
				}
				
				//TODO: Change what happens on a read
				//Read each message from my 1 hop neighbors and get their n hop neighbors
				for(i = 0; i < numNeighbors; i++){
					
					if(MessageQ.get(i).peek() != null) {
						m = MessageQ.get(i).remove();
						
						if(m.GetMessage().compareTo("app") == 0) {
							if(totalSentMsgs < maxNumber) {
								isActive = true;
							}
						} else if(m.GetMessage().compareTo("snapshot") == 0) {
							
						} else if(m.GetMessage().compareTo("info") == 0) {
							
						} else if(m.GetMessage().compareTo("tree") == 0) {
							if(parentAddress == null || parentAddress.isEmpty()) {
								parentAddress = m.GetFrom();
								treeMsgsReceived.add(m.GetFrom());
								treeTemp.add(m.GetFrom());
							} else {
								treeMsgsReceived.add(m.GetFrom());
								treeTemp.add(m.GetFrom());
							}
						} else if(m.GetMessage().compareTo("child") == 0) {
							treeTemp.add(m.GetFrom());
							children.add(m.GetFrom());
						} else if(m.GetMessage().compareTo("notChild") == 0) {
							treeTemp.add(m.GetFrom());
						} else if(m.GetMessage().compareTo("begin") == 0) {
							isTreeBuilding = false;
							if(children != null && !children.isEmpty()) {
								sendBeginMsgs(oos);
							}
						}
					}
				}
				
				if(!treeReply && treeTemp.size() == neighborsNode.length) {
					
					treeReply = true;
					treeSent = true;
					
					if(myNode == 0) {
						isTreeBuilding = false;
						sendBeginMsgs(oos);
					} else {
						for(int x=0; x<treeMsgsReceived.size(); x++) {
							if(treeMsgsReceived.get(x) != parentAddress) {
								sendNotChildMsg(treeMsgsReceived.get(x), oos);
							} else {
								sendChildMsg(treeMsgsReceived.get(x), oos);
							}
						}
					}
				} else if(!treeSent && !treeMsgsReceived.isEmpty()) {
					treeSent = true;
					sendTreeMsgs(treeMsgsReceived, oos);
				}
				
			}while(!done);

			//TODO: Update or remove this
			for(i = 0; i < numNeighbors;i++)
				oos[i].writeObject(new Message(myAddress,address[i],"",myRound-1,khop.get(myRound-1)));
			
		} catch (Exception e)
		{
			System.out.println("Get Error in Algorithm: ");
			e.printStackTrace();
		}
		
		//TODO: Will need to write to different files here
		try{
			for(i = 0; i < numNeighbors; i++)
				oos[i].writeObject(new Message(myAddress,address[i],"END", myRound, tmp = null));
			Thread.sleep(5000);
			System.out.println("My Node: " + myNode);
			System.out.println("My k-hop Neighbors: ");
			
			for(i = 0; i < khop.size() - 1; i++){
				tmpset = new HashSet<Integer>(khop.get(i));
				System.out.print(i + ") ");
				for(int s : tmpset) {
					System.out.print(s + " ");
				}
				System.out.println();
				}
			for(i = 0; i < numNeighbors; i++){
				oos[i].close();
				clients[i].close();
			}
			
			((Server) Server).TurnOffServer();
		} catch(Exception e) {
			System.out.println("Got Error Cleaning up: " + e);
		}
		System.out.println("END");
	}
	
	public static void setup(String args[]){
		try{
			String tmp, tmp2[];
			myNode = Integer.parseInt(args[0]);
			Scanner in = new Scanner(new FileReader("config.txt"));
			
			//Scan to Number of Nodes
			do{
				tmp = in.nextLine();
			}while(tmp.startsWith("#") || tmp.trim().length() <= 0);
			
			Scanner paramScan = new Scanner(tmp);
			
			@SuppressWarnings("unused")
			int numNodes = paramScan.nextInt();
			minPerActive = paramScan.nextInt();
			maxPerActive = paramScan.nextInt();
			minSendDelay = paramScan.nextInt();
			snapshotDelay = paramScan.nextInt();
			maxNumber = paramScan.nextInt();
			
			paramScan.close();
			
			//Scan to my nodes info
			do{
				tmp = in.nextLine();
				tmp2 = tmp.trim().split("\\s+");
			}
			while(tmp2.length < 1 || !tmp2[0].equals(Integer.toString(myNode)));

			myAddress = tmp2[1];
			myPort = tmp2[2];
						
			//Scan to my neighbors list
			do{
				tmp = in.nextLine();
				tmp2 = tmp.trim().split("\\s+");
			}
			while(tmp2.length < 1 || !tmp2[0].equals(Integer.toString(myNode)));
			
			tmp2 = tmp.trim().split("\\s+");
			numNeighbors = tmp2.length - 1;
			neighborsNode = new int[numNeighbors];
			address = new String[numNeighbors];
			port = new String[numNeighbors];
			
			for(int i = 1; i < tmp2.length; i++){
				neighborsNode[i - 1] = Integer.parseInt(tmp2[i]);
			}
			
			//Scan to my neighbors info
			for(int i = 0; i < numNeighbors; i++){
				in.close();
				in = new Scanner(new FileReader("config.txt"));
				do{
					tmp = in.nextLine();
				}while(tmp.startsWith("#") || tmp.trim().length() <= 0);
				
				do{
					tmp = in.nextLine();
				}while(!tmp.trim().startsWith(Integer.toString(neighborsNode[i])));
				
				tmp2 = tmp.trim().split("\\s+");
				address[i] = tmp2[1];
				port[i] = tmp2[2];
			}
			
			in.close();
		
		} catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static int getNode(int i)
	{
		int j;
		for(j = 0; j < numNeighbors; j++)
			if(neighborsNode[j] == i)
				return j;
		return -1;
	}
	
	public static void showInfo()
	{
		int i = 0;
		
		System.out.println("------------------------------");
		System.out.println("Program info:");
		System.out.println("My Node: " + myNode);
		System.out.println("My Address: " + myAddress);
		System.out.println("My Port: " + Integer.parseInt(myPort));
		System.out.println("My Round: " + myRound);
		System.out.println("Number of Neihbors: " + numNeighbors);
		
		while(i < numNeighbors)
		{
			System.out.println("Neighbor " + (i+1) + ": Node: " + neighborsNode[i]);
			System.out.println(address[i] + ":" + port[i]);
			i++;
		}
		System.out.println("------------------------------");
	}
	
	private static void startTree(ObjectOutputStream[] oos) throws IOException {
		if(myNode == 0) {
			for(int i = 0; i < numNeighbors; i++) {
				oos[i].writeObject(new Message(myAddress, address[i], "tree"));
			}
		}
	}
	
	private static void sendAppMsg() {
		
	}
	
	private static void sendMarker() {
		
	}
	
	private static void receiveMarker() {
		
	}
	
	private static void sendTreeMsgs(List<String> msgsReceived, ObjectOutputStream[] oos) throws IOException {
		for(int i=0; i<address.length; i++) {
			boolean receivedFromAddress = false;
			
			for(String a : msgsReceived) {
				if(a.compareTo(address[i]) == 0) {
					receivedFromAddress = true;
				}
			}
			
			if(!receivedFromAddress) {
				oos[i].writeObject(new Message(myAddress, address[i], "tree"));
			}
		}
	}
	
	private static void sendBeginMsgs(ObjectOutputStream[] oos) throws IOException {
		for(int i = 0; i<children.size(); i++) {
			oos[i].writeObject(new Message(myAddress, children.get(i), "begin"));
		}
	}
	
	private static void sendChildMsg(String a, ObjectOutputStream[] oos) throws IOException {
		for(int i=0; i<address.length; i++) {
			if(address[i].compareTo(a) == 0) {
				oos[i].writeObject(new Message(myAddress, a, "child"));
			}
		}
	}
	
	private static void sendNotChildMsg(String a, ObjectOutputStream[] oos ) throws IOException {
		for(int i=0; i<address.length; i++) {
			if(address[i].compareTo(a) != 0) {
				oos[i].writeObject(new Message(myAddress, a, "notChild"));
			}
		}
	}
}