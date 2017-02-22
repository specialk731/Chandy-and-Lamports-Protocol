import java.io.*;
import java.util.*;

class Message implements Serializable{
	private static final long serialVersionUID = 2675242361263880997L;
	private String to,from,message;
	int round = -1;
	ArrayList<Integer> Nodes;
	
	Message(String f, String t, String m, int r, ArrayList<Integer> a){
		from = f;
		to = t;
		message = m;
		round = r;
		Nodes = a;
	}
	
	Message(String f, String t, String m){
		to = t;
		from = f;
		message = m;
	}
	
	public String GetMessage(){
		return message;
	}
	
	public String GetTo(){
		return to;
	}
	
	public String GetFrom(){
		return from;
	}
	
	public ArrayList<Integer> GetNodes(){
		return Nodes;
	}
	
	public boolean SetMessage(String s){
		message = s;
		return true;
	}
	
	public boolean SetTo(String s){
		to = s;
		return true;
	}
	
	public boolean SetFrom(String s){
		from = s;
		return true;
	}
	
	public boolean SetNodes(ArrayList<Integer> a){
		Nodes = a;
		return true;
	}

	public void Display() {
		System.out.println("Round: " + round);
		System.out.println("To: " + to);
		System.out.println("From: " + from);
		System.out.println("Message: " + message);
		System.out.print("newNodes: ");
		for(int i = 0; i < Nodes.size(); i++)
			System.out.print(Nodes.get(i) + " ");
		System.out.println();
	}
	
}