import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Program {
	static int numNodes, minPerActive, maxPerActive, minSendDelay, snapshotDelay, maxNumber;
	static int neighborsNode[], numNeighbors, myNode, myRound;
	static List<Integer> children = new ArrayList<>();
	static String myAddress, myPort;
	static List<LinkedBlockingQueue<Message>> MessageQ; //List of FIFO Blocking queue of messages
	static Map<Integer, String> addresses = new HashMap<>();
	static Map<Integer, String> ports = new HashMap<>();
	
	public static void main(String[] args) throws IOException { //
		setup(args);
		
		int[] clock = new int[numNodes];
		int parentNode = -1;
		
		for(int i=0; i<clock.length; i++) {
			clock[i] = 0;
		}
		
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
		
		Map<Integer, Socket> clients = new HashMap<>();
		Map<Integer, ObjectOutputStream> oos = new HashMap<>();
		
		try{
			Thread.sleep(1000);
			for(i = 0; i < addresses.size(); i++){
				clients.put(i, new Socket(addresses.get(i), Integer.parseInt(ports.get(i))));
				oos.put(i, new ObjectOutputStream(clients.get(i).getOutputStream()));
							
				oos.get(i).writeInt(myNode);
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
			
			List<Integer> treeMsgsReceived = new ArrayList<>();
			List<Integer> treeTemp = new ArrayList<>();
			
			//Start Algorithm
			do{
				//Send message to a random neighbor
				if(!isSnapshoting && isActive && !isTreeBuilding && timeForNextAppSend <= System.currentTimeMillis()) {
					int index = rand.nextInt() % neighborsNode.length;
					clock[myNode]++;
					oos.get(index).writeObject(new Message(myNode, neighborsNode[index], "app", clock));
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
				
				//Read each message from my 1 hop neighbors and get their n hop neighbors
				for(i = 0; i < numNeighbors; i++){
					
					if(MessageQ.get(i).peek() != null) {
						m = MessageQ.get(i).remove();
						
						if(m.GetMessage().compareTo("app") == 0) {
							if(!isActive && totalSentMsgs < maxNumber) {
								isActive = true;
							}
							
							for(int k=0; k<clock.length; k++) {
								if(clock[k] < m.GetClock()[k]) {
									clock[k] = m.GetClock()[k];
								}
							}
						} else if(m.GetMessage().compareTo("snapshot") == 0) {
							//TODO: Chandy & Lamports protocol
						} else if(m.GetMessage().compareTo("info") == 0) {
							
						} else if(m.GetMessage().compareTo("tree") == 0) {
							if(parentNode == -1) {
								parentNode = m.GetFrom();
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
							if(treeMsgsReceived.get(x) != parentNode) {
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
				
				//TODO: Termination
				
			}while(!done);
			
		} catch (Exception e)
		{
			System.out.println("Get Error in Algorithm: ");
			e.printStackTrace();
		}
		
		//TODO: Will need to write to different files here
		try{
			
		} catch(Exception e) {
			System.out.println("Got Error Cleaning up: " + e);
		}
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
			
			numNodes = paramScan.nextInt();
			minPerActive = paramScan.nextInt();
			maxPerActive = paramScan.nextInt();
			minSendDelay = paramScan.nextInt();
			snapshotDelay = paramScan.nextInt();
			maxNumber = paramScan.nextInt();
			
			paramScan.close();
			
			String tempLine = in.nextLine().trim();
			
			while(tempLine == null || tempLine.isEmpty() || tempLine.charAt(0) == '#') {
				tempLine = in.nextLine().trim();
			}
			
			int i=0;
			//Scan to my nodes info
			while(tempLine != null && !tempLine.isEmpty() && tempLine.charAt(0) != '#'){
				tmp2 = tempLine.trim().split("\\s+");
				
				addresses.put(i, tmp2[1]);
				ports.put(i, tmp2[2]);
				i++;
				
				tempLine = in.nextLine().trim();
			}
						
			//Scan to my neighbors list
			tempLine = in.nextLine().trim();
			
			while(tempLine == null || tempLine.isEmpty() || tempLine.charAt(0) == '#') {
				tempLine = in.nextLine().trim();
			}
			
			i=0;
			//Scan to my nodes info
			while(i<myNode){
				tempLine = in.nextLine().trim();
				i++;
			}
			
			tmp2 = tmp.trim().split("\\s+");
			
			int tempCount = 0;
			
			for(int x=0; x<tmp2.length; x++) {
				if(tmp2[x].charAt(0) != '#') {
					tempCount++;
				} else {
					break;
				}
			}
				
			
			numNeighbors = tempCount;
			neighborsNode = new int[numNeighbors];
			
			for(int y = 1; y < tempCount; y++){
				neighborsNode[y - 1] = Integer.parseInt(tmp2[y]);
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
	
	/*
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
	*/
	
	private static void startTree(Map<Integer, ObjectOutputStream> oos) throws IOException {
		if(myNode == 0) {
			for(int i = 0; i < numNeighbors; i++) {
				int nodeNum = neighborsNode[i];
				oos.get(nodeNum).writeObject(new Message(myNode, nodeNum, "tree"));
			}
		}
	}
	
	private static void sendAppMsg() {
		
	}
	
	private static void sendMarker() {
		
	}
	
	private static void receiveMarker() {
		
	}
	
	private static void sendTreeMsgs(List<Integer> msgsReceived, Map<Integer, ObjectOutputStream> oos) throws IOException {
		for(int i=0; i<neighborsNode.length; i++) {
			boolean receivedFromAddress = false;
			int nodeNum = neighborsNode[i];
			
			for(Integer a : msgsReceived) {
				if(nodeNum == a) {
					receivedFromAddress = true;
				}
			}
			
			if(!receivedFromAddress) {
				oos.get(nodeNum).writeObject(new Message(myNode, nodeNum, "tree"));
			}
		}
	}
	
	private static void sendBeginMsgs(Map<Integer, ObjectOutputStream> oos) throws IOException {
		for(int i = 0; i<children.size(); i++) {
			int nodeNum = children.get(i);
			oos.get(nodeNum).writeObject(new Message(myNode, nodeNum, "begin"));
		}
	}
	
	private static void sendChildMsg(int a, Map<Integer, ObjectOutputStream> oos) throws IOException {
		oos.get(a).writeObject(new Message(myNode, a, "child"));
	}
	
	private static void sendNotChildMsg(int a, Map<Integer, ObjectOutputStream> oos) throws IOException {
		oos.get(a).writeObject(new Message(myNode, a, "notChild"));
	}
}