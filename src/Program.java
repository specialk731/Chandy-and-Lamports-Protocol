import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Program {
	static int numNodes, minPerActive, maxPerActive, minSendDelay, snapshotDelay, maxNumber;
	static int neighborsNode[], numNeighbors, myNode, myRound;
	static List<Integer> children = new ArrayList<>();
	static String myAddress, myPort;
	static Map<Integer, LinkedBlockingQueue<Message>> MessageQ; //List of FIFO Blocking queue of messages
	static Map<Integer, String> addresses = new HashMap<>();
	static Map<Integer, String> ports = new HashMap<>();
	public static boolean termThreads = false;

	
	public static void main(String[] args) throws IOException { //
		setup(args);
		
		//Sets to store info from children snapshots at node 0
		Map<Integer, int[]> clockSet =  new HashMap<>();
		Map<Integer, int[]> msgInfoSet =  new HashMap<>();
		Map<Integer, Boolean> statusSet =  new HashMap<>();
		
		int[] clock = new int[numNodes];
		int parentNode = (myNode == 0) ? 0 : -1;
		
		//initialize clock to 0
		for(int i=0; i<clock.length; i++) {
			clock[i] = 0;
		}
		
		//booleans used for state control
		boolean isActive, isSnapshoting = false, isTreeBuilding = true;
		
		int totalSentMsgs = 0;
		int roundSentMsgs = 0;
		int lastSentClockNum = 0;
		int lastSentDest = -1;
		
		//open output file and writer

		String outputFileName = "config-" + myNode + ".txt";
		File dir = new File(System.getProperty("user.dir") + "/output");
		PrintWriter fileOutput = null;

		if(dir.mkdir() || dir.isDirectory()) {
			File outputFile = new File(dir, outputFileName);
			fileOutput = new PrintWriter(outputFile, "UTF-8");
		} else {
			File outputFile = new File(outputFileName);
			fileOutput = new PrintWriter(outputFile, "UTF-8");
		}
		//used for activeness determination and which node to send to
		Random rand = new Random();
		
		//Node 0 always starts active
		if(myNode == 0) {
			isActive = true;
		} else {
			int randNum = rand.nextInt();
			isActive = (randNum%2 == 0) ? true : false;
		}
		
		
		int i = 0;
		myRound = 0;
		MessageQ = new HashMap<Integer, LinkedBlockingQueue<Message>>(addresses.size());
		
		for(i = 0; i < addresses.size(); i++) {
			MessageQ.put(i, new LinkedBlockingQueue<Message>(100));
		}
				
		Message m = null;
		
		//spawn thread that will create the server socket to listen to other processes
		Thread Server = new Server(myAddress, myPort);
		Server.start();
		
		//maps of sockets and object output streams keyed by node number
		Map<Integer, Socket> clients = new HashMap<>();
		Map<Integer, ObjectOutputStream> oos = new HashMap<>();
		
		//create sockets and Object Output Streams and map them
		try{
			Thread.sleep(5000);
			for(i = 0; i < addresses.size(); i++){
				if(i != myNode ){
					clients.put(i, new Socket(addresses.get(i), Integer.parseInt(ports.get(i))));
					oos.put(i, new ObjectOutputStream(clients.get(i).getOutputStream()));
			
					oos.get(i).writeInt(myNode);
				}
			}
		} catch(Exception e){
				System.out.println(myNode + "Got Error in Client Setup: " + e);
		}
				
		//Node 0 sends initial tree building messages
		startTree(oos);
		
		//Main program begins here
		try{
			boolean treeReply = false;
			boolean treeSent = (myNode == 0) ? true : false;
			boolean terminate = false;
			
			long timeForNextAppSend = System.currentTimeMillis();
			long timeForNextSnapshot = System.currentTimeMillis();
			
			int infoReceived = 0;
			
			List<Integer> treeMsgsReceived = new ArrayList<>();
			List<Integer> treeTemp = new ArrayList<>();
			
			//Start Algorithm
			do{
				
				//Send message to a random neighbor if conditions are met
				if(!isSnapshoting && isActive && !isTreeBuilding && timeForNextAppSend <= System.currentTimeMillis()) {
					int index = (rand.nextInt(100)) % neighborsNode.length;
					clock[myNode]++;
					oos.get(neighborsNode[index]).writeObject(new Message(myNode, neighborsNode[index], "app", clock));
					timeForNextAppSend = System.currentTimeMillis() + minSendDelay;
					roundSentMsgs++;
					totalSentMsgs++;
					lastSentDest = neighborsNode[index];
					lastSentClockNum = clock[myNode];
					
					//go passive if max # of messages have been sent this "round"
					if(roundSentMsgs >= maxPerActive) {
						isActive = false;
						roundSentMsgs = 0;
					} else if(roundSentMsgs >= minPerActive) { 	//50% chance to go passive if enough messages have been sent
						if(rand.nextInt()%2 == 0) {
							isActive = false;
							roundSentMsgs = 0;
						}
					}
				}
				
				//Check each incoming message q for a message
				for(i = 0; i < MessageQ.size(); i++){
					
					//get message from the queue if it exists
					if(MessageQ.get(i).peek() != null) {
						m = MessageQ.get(i).remove();
						
						
						if(m.GetMessage().compareTo("app") == 0) {	//application Message
							//become active if you weren't
							if(!isActive && totalSentMsgs < maxNumber) {
								isActive = true;
							}
							
							//get new clock values
							for(int k=0; k<clock.length; k++) {
								if(clock[k] < m.GetClock()[k]) {
									clock[k] = m.GetClock()[k];
								}
							}
							
							//increment your own
							clock[myNode]++;
						} else if(m.GetMessage().compareTo("snapshot") == 0) {	//Message to begin snapshoting
							if(children == null || children.isEmpty()) {	//if you are a tree leaf
								isSnapshoting = true;
								
								//send info message to parent
								oos.get(parentNode).writeObject(new Message(myNode, parentNode, "info", clock, lastSentDest, lastSentClockNum, isActive));
							} else { //if you are not a leaf
								isSnapshoting = true;
								//pass snapshot message to children
								for(int t=0; t<children.size(); t++) {	
									oos.get(children.get(t)).writeObject(new Message(myNode, children.get(t), "snapshot"));
								}
								
								//reply with info message
								oos.get(parentNode).writeObject(new Message(myNode, parentNode, "info", clock, lastSentDest, lastSentClockNum, isActive));
							}
						} else if(m.GetMessage().compareTo("info") == 0) {	//message that holds information for root on snapshot
							
							//if you are node 0 (the root)
							if(myNode == 0) {
								//count number of info messages received
								infoReceived++;
								
								//get the information from the message and store it for later
								clockSet.put(m.GetFrom(), m.clock);
								int[] tmpArray = {m.lastDest, m.lastClock};
								msgInfoSet.put(m.GetFrom(), Arrays.copyOf(tmpArray, tmpArray.length));
								statusSet.put(m.GetFrom(), m.isActive);
								
								//if you have received an info message from everyone else
								if(infoReceived == (addresses.size()-1)) {
									
									infoReceived = 0;
										
									//store your own info in the maps
									clockSet.put(myNode, clock);
									int[] tempArray = {lastSentDest, lastSentClockNum};
									msgInfoSet.put(myNode, Arrays.copyOf(tempArray, tempArray.length));
									statusSet.put(myNode, isActive);
									
									//check for consistency first
									if(detectConsistency(clockSet)) {
										if(detectTermination(clockSet, msgInfoSet, statusSet)) {	//then check for termination conditions
											terminate = true;
											timeForNextSnapshot= System.currentTimeMillis() + snapshotDelay;
										} else { //termination not met
											for(int h=0; h<children.size(); h++) {
												oos.get(children.get(h)).writeObject(new Message(myNode, children.get(h), "resume"));
											}
											timeForNextSnapshot= System.currentTimeMillis() + snapshotDelay;
										}
										
										//print current clock on successful snapshot
										for(int w=0; w<clock.length; w++) {
											fileOutput.print(clock[w] + " ");
										}
										
										fileOutput.println();
										
										isSnapshoting = false;
									} else { //snapshot was not consistent
										isSnapshoting = false;
										for(int p=0; p<children.size(); p++) {
											oos.get(children.get(p)).writeObject(new Message(myNode, children.get(p), "abort"));
										}
									}
									
									//clear all sets for future use
									isSnapshoting = false;
									clockSet.clear();
									msgInfoSet.clear();
									statusSet.clear();
								}
							} else { //if not node 0, pass info message to parent towards 0
								oos.get(parentNode).writeObject(m);
							}
							
						} else if(m.GetMessage().compareTo("abort") == 0) {		//message used if bad snapshot was taken
							if(children == null || children.isEmpty()) {
								isSnapshoting = false;
							} else {
								isSnapshoting = false;
								for(int y=0; y<children.size(); y++) {
									oos.get(children.get(y)).writeObject(new Message(myNode, children.get(y), "abort"));
								}
								clockSet.clear();
								msgInfoSet.clear();
								statusSet.clear();
							}
						} else if(m.GetMessage().compareTo("resume") == 0) {	//continue normal operation after snapshot
							if(children == null || children.isEmpty()) {
								isSnapshoting = false;
								
								//print clock to file
								for(int w=0; w<clock.length; w++) {
									fileOutput.print(clock[w] + " ");
								}
								
								fileOutput.println();
							} else {
								isSnapshoting = false;
								for(int y=0; y<children.size(); y++) {
									oos.get(children.get(y)).writeObject(new Message(myNode, children.get(y), "resume"));
								}
								
								for(int w=0; w<clock.length; w++) {
									fileOutput.print(clock[w] + " ");
								}
								
								fileOutput.println();
								
								clockSet.clear();
								msgInfoSet.clear();
								statusSet.clear();
							}
						} else if(m.GetMessage().compareTo("tree") == 0) {	//passed to neighbors to see if you are their parent
							if(parentNode == -1) { //if tree msg received an you have no paretn, assign sender as parent
								parentNode = m.GetFrom();
								treeMsgsReceived.add(m.GetFrom()); //store in list of nodes who sent you tree messages
								treeTemp.add(m.GetFrom()); //store node number in treeTmp, used to know when to reply to parent
							} else { //otherwise, do not treat sender as parent
								treeMsgsReceived.add(m.GetFrom());
								treeTemp.add(m.GetFrom());
							}
						} else if(m.GetMessage().compareTo("child") == 0) { //sender of this message is your child
							treeTemp.add(m.GetFrom());
							children.add(m.GetFrom());	//add to set of children
						} else if(m.GetMessage().compareTo("notChild") == 0) { //sender is not your child. Also used as filler message
							treeTemp.add(m.GetFrom());
						} else if(m.GetMessage().compareTo("begin") == 0) { //used to end tree construction and begin normal operation
							isTreeBuilding = false;
							if(children != null && !children.isEmpty()) {
								sendBeginMsgs(oos);
							}
						} else if(m.GetMessage().compareTo("END") == 0) { //used to terminate
							terminate = true;
							
							//output clock to file one last time
							for(int w=0; w<clock.length; w++) {
								fileOutput.print(clock[w] + " ");
							}
							
							fileOutput.println();
						}
					}
				}
				
				//if you have not sent tree messages to your neighbors and you have received at least one tree message
				if(!treeSent && !treeMsgsReceived.isEmpty()) {
					treeSent = true;
					sendTreeMsgs(treeMsgsReceived, oos, parentNode);
				}
				
				//if you have not replied to your parent and you have received at least on tree related message from all nodes
				if(!treeReply && treeTemp.size() >= oos.size()) {
					
					treeReply = true;
					treeSent = true;
					
					//if node 0, begin normal operation
					if(myNode == 0) {
						isTreeBuilding = false;
						sendBeginMsgs(oos);
						timeForNextSnapshot = System.currentTimeMillis() + snapshotDelay;
					} else { //otherwise, send a child message to your parent and a notChild message to everyone else who sent you a tree message
						for(int x=0; x<treeMsgsReceived.size(); x++) {
							if(treeMsgsReceived.get(x) != parentNode) {
								sendNotChildMsg(treeMsgsReceived.get(x), oos);
							} else {
								sendChildMsg(treeMsgsReceived.get(x), oos);
							}
						}
					}
				}
				
				//if you are node 0 and it is time to snapshot
				if(myNode == 0 && !isSnapshoting && !isTreeBuilding && System.currentTimeMillis() >= timeForNextSnapshot) {
					isSnapshoting = true;
					
					//send snapshot message to all children
					for(int g=0; g<children.size(); g++) {
						oos.get(children.get(g)).writeObject(new Message(myNode, children.get(g), "snapshot"));
					}
				} 
				
			}while(!terminate);
			
			//after termination, send messages to everyone else
			for(Map.Entry<Integer, ObjectOutputStream> entry : oos.entrySet()) {
				int key = entry.getKey();
				ObjectOutputStream tempOOS = entry.getValue();
				
				if(children.contains(key)) {
					tempOOS.writeObject(new Message(myNode, key, "END"));		//sent to children so that they may terminate
				} else {
					tempOOS.writeObject(new Message(myNode, key, "KILL"));		//sent to all listening sockets to kill any hanging threads
				}
			}
			
		} catch (Exception e)
		{
			System.out.println("Get Error in Algorithm: ");
			e.printStackTrace();
		}
		
		//wait until the server thread is joined to terminate
		try {
			Server.join();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		//close all sockets
		for(Map.Entry<Integer, Socket> entry : clients.entrySet()) {
			entry.getValue().close();
		}
		
		//close the file writer
		fileOutput.close();
	}
	
	//Reads the config file
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
			
			//scan in all information from the first valid line
			numNodes = paramScan.nextInt();
			minPerActive = paramScan.nextInt();
			maxPerActive = paramScan.nextInt();
			minSendDelay = paramScan.nextInt();
			snapshotDelay = paramScan.nextInt();
			maxNumber = paramScan.nextInt();
			
			paramScan.close();
			
			//read until the addressing info is found
			String tempLine = in.nextLine().trim();
			
			while(tempLine == null || tempLine.isEmpty() || tempLine.charAt(0) == '#') {
				tempLine = in.nextLine().trim();
			}
			
			//store the address and port of each node
			int i=0;
			while(tempLine != null && !tempLine.isEmpty() && tempLine.charAt(0) != '#' && i < numNodes){
				tmp2 = tempLine.trim().split("\\s+");
				
				addresses.put(i, tmp2[1]);
				ports.put(i, tmp2[2]);
				i++;
				
				tempLine = in.nextLine().trim();
			}
						
			//Scan to my neighbors list
			
			while(tempLine == null || tempLine.isEmpty() || tempLine.charAt(0) == '#') {
				tempLine = in.nextLine().trim();
			}
			
			//scan to the info of my node
			i=0;
			while(i<myNode){
				tempLine = in.nextLine().trim();
				i++;
			}
			
			tmp2 = tempLine.trim().split("\\s+");
			
			int tempCount = 0;
			
			//count the valid characters in the line aka the number of neighbors
			for(int x=0; x<tmp2.length; x++) {
				if(tmp2[x].charAt(0) != '#') {
					tempCount++;
				} else {
					break;
				}
			}
				
			
			numNeighbors = tempCount;
			neighborsNode = new int[numNeighbors];
			
			//store neighbors
			for(int y = 0; y < tempCount; y++){
				neighborsNode[y] = Integer.parseInt(tmp2[y]);
			}
			
			//get your own address and port
			myAddress = addresses.get(myNode);
			myPort = ports.get(myNode);
			
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
	
	//Used by node 0 to begin the process of tree building
	private static void startTree(Map<Integer, ObjectOutputStream> oos) throws IOException {
		if(myNode == 0) {		
			//loop through all Object Output Streams
			for(Map.Entry<Integer, ObjectOutputStream> entry : oos.entrySet()) {
				int key = entry.getKey();
				boolean isNeighbor = false;
				
				//check to see if the current stream is for a neighbor
				for(int i=0; i<neighborsNode.length; i++) {
					if(neighborsNode[i] == key) {
						isNeighbor = true;
						break;
					}
				}
				
				if(isNeighbor) { //if it is, send them a tree message
					entry.getValue().writeObject(new Message(myNode, key, "tree"));
				} else { //if it isn't, send them a notChild message as filler, ensuring all nodes get messages from everyone
					entry.getValue().writeObject(new Message(myNode, key, "notChild"));
				}
			}
		}
	}
	
	//used by nodes that are not 0 to send tree messages
	private static void sendTreeMsgs(List<Integer> msgsReceived, Map<Integer, ObjectOutputStream> oos, int parent) throws IOException {		
		//loop through all object output streams
		for(Map.Entry<Integer, ObjectOutputStream> entry : oos.entrySet()) {
			int key = entry.getKey();
			
			//only send a message if the node is not your parent
			if(key != parent) {
				boolean isNeighbor = false;
				
				//determine if this node is a neighbor or not
				for(int i=0; i<neighborsNode.length; i++) {
					if(neighborsNode[i] == key) {
						isNeighbor = true;
						break;
					}
				}
				
				if(isNeighbor) { //if they are a neighbor
					boolean receivedFromAddress = false;
					
					//check to see if they sent you a tree message
					for(Integer a : msgsReceived) {
						if(key == a) {
							receivedFromAddress = true;
							break;
						}
					}
					
					if(receivedFromAddress) { //if they are a neighbor and sent you a tree message, send them a filler notChild
						entry.getValue().writeObject(new Message(myNode, key, "notChild"));
					} else { //if they are a neighbor and have not sent you a tree message, send them a tree message
						entry.getValue().writeObject(new Message(myNode, key, "tree"));
					}
				} else { //if they are not a neighbor, send them a notChild message as filler
					entry.getValue().writeObject(new Message(myNode, key, "notChild"));
				}
			}
		}
	}
	
	//sends begin messages to all children
	private static void sendBeginMsgs(Map<Integer, ObjectOutputStream> oos) throws IOException {
		for(int i = 0; i<children.size(); i++) {
			int nodeNum = children.get(i);
			oos.get(nodeNum).writeObject(new Message(myNode, nodeNum, "begin"));
		}
	}
	
	//sends a child message to a node
	private static void sendChildMsg(int a, Map<Integer, ObjectOutputStream> oos) throws IOException {
		oos.get(a).writeObject(new Message(myNode, a, "child"));
	}
	
	//sends a not child message to a node
	private static void sendNotChildMsg(int a, Map<Integer, ObjectOutputStream> oos) throws IOException {
		oos.get(a).writeObject(new Message(myNode, a, "notChild"));
	}
	
	//detects if it is time to terminate
	private static boolean detectTermination(Map<Integer, int[]> clockSet, Map<Integer, int[]> msgInfoSet, Map<Integer, Boolean> statusSet) {
		
		//First check to ensure all node are in a passive state, if not, return false.
		for(Map.Entry<Integer, Boolean> entry : statusSet.entrySet()) {
			if(entry.getValue()) {
				return false;
			}
		}
		
		//take the destination and clock value at the sender for the last sent message for every process
		for(Map.Entry<Integer, int[]> entry : msgInfoSet.entrySet()) {
			int clockVal = entry.getValue()[1];
			int dest = entry.getValue()[0];
			int src = entry.getKey();
			
			//and if it has a valid destination
			if(dest != -1) {
				//compare it sender's value in the destination clock
				//this ensures that there are no in-transit messages
				int[] destClock = clockSet.get(dest);
				int destValue = destClock[src];

				//If the sender's clock value at send time is smaller than the current clock at the destination
				//		then the message was never received
				if(clockVal > destValue) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	//ensure that all states are consistent
	private static boolean detectConsistency(Map<Integer, int[]> clockSet) {
		
		//for the value of a node within its own clock,
		for(int i=0; i<clockSet.size(); i++) {
			int[] baseClock = clockSet.get(i);
			int baseValue = baseClock[i];
			
			//ensure that no other clock has a higher value
			for(int x=0; x<clockSet.size(); x++) {
				if(x != i) {
					int[] currClock = clockSet.get(x);
					int currValue = currClock[i];
					
					//A processes own clock should have the largest value for that process out of all clocks
					if(currValue > baseValue) {
						System.out.println("Failed Consistency check == Base Clock: " + i + ", " + baseValue + "; Incorrect Clock: " + x + currValue); 
						return false;
					}
				}
			}
		}
		
		return true;
	}
}
