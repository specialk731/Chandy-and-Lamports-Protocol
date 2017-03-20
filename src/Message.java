import java.io.*;
import java.util.*;

class Message implements Serializable{
	private static final long serialVersionUID = 2675242361263880997L;
	private String from,to,message;
	int[] clock = null;
	
	Message(String f, String t, String m, int[] c){
		from = f;
		to = t;
		message = m;
		clock = Arrays.copyOf(c, c.length);
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
	
	public int[] GetClock(){
		return clock;
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
	
	public boolean SetClock(int[] c){
		clock = Arrays.copyOf(c, c.length);
		return true;
	}
	
	public void Display() {

	}
	
}