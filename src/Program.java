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
	static Map<Integer, int[]> clockSet =  new HashMap<>();
	static Map<Integer, int[]> msgInfoSet =  new HashMap<>();
	static Map<Integer, Boolean> statusSet =  new HashMap<>();
	
	public static void main(String[] args) throws IOException { //
		setup(args);
		
		int[] clock = new int[numNodes];
		int parentNode = (myNode == 0) ? 0 : -1;
		
		for(int i=0; i<clock.length; i++) {
			clock[i] = 0;
		}
		
		boolean isActive, isSnapshoting = false, isTreeBuilding = true;
		
		int totalSentMsgs = 0;
		int roundSentMsgs = 0;
		int lastSentClockNum = 0;
		int lastSentDest = -1;
		
		String outputFileName = "config-" + myNode + ".txt";
		
		PrintWriter fileOutput = new PrintWriter(outputFileName, "UTF-8");
		
		Random rand = new Random();
		
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
		
		Thread Server = new Server(myAddress, myPort);
		Server.start();
		
		Map<Integer, Socket> clients = new HashMap<>();
		Map<Integer, ObjectOutputStream> oos = new HashMap<>();
		
		try{
			Thread.sleep(3000);
			for(i = 0; i < addresses.size(); i++){
				if(i != myNode ){
					clients.put(i, new Socket(addresses.get(i), Integer.parseInt(ports.get(i))));
					oos.put(i, new ObjectOutputStream(clients.get(i).getOutputStream()));
			
					oos.get(i).writeInt(myNode);
				}
			}
		} catch(Exception e){
				System.out.println("Got Error in Client Setup: " + e);
		}
				
		startTree(oos);
		
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
				
				//Send message to a random neighbor
				if(!isSnapshoting && isActive && !isTreeBuilding && timeForNextAppSend <= System.currentTimeMillis()) {
					int index = rand.nextInt() % neighborsNode.length;
					clock[myNode]++;
					oos.get(neighborsNode[index]).writeObject(new Message(myNode, neighborsNode[index], "app", clock));
					timeForNextAppSend = System.currentTimeMillis() + minSendDelay;
					roundSentMsgs++;
					totalSentMsgs++;
					lastSentDest = neighborsNode[index];
					lastSentClockNum = clock[myNode];
					
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
					
					if(MessageQ.get(neighborsNode[i]).peek() != null) {
						m = MessageQ.get(neighborsNode[i]).remove();
						
						if(m.GetMessage().compareTo("app") == 0) {
							if(!isActive && totalSentMsgs < maxNumber) {
								isActive = true;
							}
							
							for(int k=0; k<clock.length; k++) {
								if(clock[k] < m.GetClock()[k]) {
									clock[k] = m.GetClock()[k];
								}
							}
							
							clock[myNode]++;
						} else if(m.GetMessage().compareTo("snapshot") == 0) {
							if(children == null || children.isEmpty()) {
								isSnapshoting = true;
								
								oos.get(parentNode).writeObject(new Message(myNode, parentNode, "info", clock, new HashMap<Integer, int[]>(), lastSentDest, lastSentClockNum, new HashMap<Integer, int[]>(), isActive, new HashMap<Integer, Boolean>()));
							} else {
								isSnapshoting = true;
								for(int t=0; t<children.size(); t++) {	
									oos.get(children.get(t)).writeObject(new Message(myNode, children.get(t), "snapshot"));
								}
							}
						} else if(m.GetMessage().compareTo("info") == 0) {
							
							infoReceived++;
							
							Map<Integer, int[]> msgClocks = m.clocks;
							for(Map.Entry<Integer, int[]> entry : msgClocks.entrySet()) {
								clockSet.put(entry.getKey(), entry.getValue());
							}
							
							Map<Integer, int[]> msgInfo = m.msgValues;
							for(Map.Entry<Integer, int[]> entry : msgInfo.entrySet()) {
								msgInfoSet.put(entry.getKey(), entry.getValue());
							}
							
							Map<Integer, Boolean> msgStatus = m.status;
							for(Map.Entry<Integer, Boolean> entry : msgStatus.entrySet()) {
								statusSet.put(entry.getKey(), entry.getValue());
							}
							
							if(infoReceived == children.size()) {
								if(myNode != 0 ) {
									oos.get(parentNode).writeObject(new Message(myNode, parentNode, "info", clock, clockSet, lastSentDest, lastSentClockNum, msgInfoSet, isActive, statusSet));
								} else if(myNode == 0) {
									
									clockSet.put(myNode, clock);
									int[] tempArray = {lastSentDest, lastSentClockNum};
									msgInfoSet.put(myNode, tempArray);
									statusSet.put(myNode, isActive);
									
									if(detectConsistency()) {
										if(detectTermination()) {
											terminate = true;
										} else {
											for(int h=0; h<children.size(); h++) {
												oos.get(children.get(h)).writeObject(new Message(myNode, children.get(h), "resume"));
											}
										}
										
										for(int w=0; w<clock.length; w++) {
											fileOutput.print(clock[w] + " ");
										}
										
										fileOutput.println();
										
										isSnapshoting = false;
									} else {
										isSnapshoting = false;
										for(int p=0; p<children.size(); p++) {
											oos.get(children.get(p)).writeObject(new Message(myNode, children.get(p), "abort"));
										}
									}
								}
							}
						} else if(m.GetMessage().compareTo("abort") == 0) {
							if(children == null || children.isEmpty()) {
								isSnapshoting = false;
							} else {
								isSnapshoting = false;
								for(int y=0; y<children.size(); y++) {
									oos.get(children.get(y)).writeObject(new Message(myNode, children.get(y), "abort"));
								}
							}
						} else if(m.GetMessage().compareTo("resume") == 0) {
							if(children == null || children.isEmpty()) {
								isSnapshoting = false;
								
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
							}
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
						} else if(m.GetMessage().compareTo("END") == 0) {
							terminate = true;
							
							for(int w=0; w<clock.length; w++) {
								fileOutput.print(clock[w] + " ");
							}
							
							fileOutput.println();
						}
					}
				}
				
				if(!treeReply && treeTemp.size() == neighborsNode.length) {
					
					treeReply = true;
					treeSent = true;
					
					if(myNode == 0) {
						isTreeBuilding = false;
						sendBeginMsgs(oos);
						timeForNextSnapshot = System.currentTimeMillis() + snapshotDelay;
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
				
				if(myNode == 0 && !isSnapshoting && !isTreeBuilding && System.currentTimeMillis() >= timeForNextSnapshot) {
					isSnapshoting = true;
					
					for(int g=0; g<children.size(); g++) {
						oos.get(children.get(g)).writeObject(new Message(myNode, children.get(g), "snapshot"));
					}
				} 
				
			}while(!terminate);
			
			for(Map.Entry<Integer, ObjectOutputStream> entry : oos.entrySet()) {
				int key = entry.getKey();
				ObjectOutputStream tempOOS = entry.getValue();
				
				if(children.contains(key)) {
					tempOOS.writeObject(new Message(myNode, key, "END"));
				} else {
					tempOOS.writeObject(new Message(myNode, key, "KILL"));
				}
			}
			
		} catch (Exception e)
		{
			System.out.println("Get Error in Algorithm: ");
			e.printStackTrace();
		}
		
		try {
			Server.join();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		for(Map.Entry<Integer, Socket> entry : clients.entrySet()) {
			entry.getValue().close();
		}
		
		fileOutput.close();
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
			
			tmp2 = tempLine.trim().split("\\s+");
			
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
			
			for(int y = 0; y < tempCount; y++){
				neighborsNode[y] = Integer.parseInt(tmp2[y]);
			}
			
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
	
	private static boolean detectTermination() {
		
		for(Map.Entry<Integer, Boolean> entry : statusSet.entrySet()) {
			if(entry.getValue()) {
				return false;
			}
		}
		
		for(Map.Entry<Integer, int[]> entry : msgInfoSet.entrySet()) {
			int clockVal = entry.getValue()[1];
			int dest = entry.getValue()[0];
			int src = entry.getKey();
			
			if(dest != -1) {
				int[] destClock = clockSet.get(dest);
				int destValue = destClock[src];
				
				if(clockVal > destValue) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	private static boolean detectConsistency() {
		
		for(int i=0; i<clockSet.size(); i++) {
			int[] baseClock = clockSet.get(i);
			int baseValue = baseClock[i];
			
			for(int x=0; x<clockSet.size(); x++) {
				if(x != i) {
					int[] currClock = clockSet.get(x);
					int currValue = currClock[i];
					
					if(currValue > baseValue) {
						return false;
					}
				}
			}
		}
		
		return true;
	}
}