import java.io.*;
import java.util.*;

class Message implements Serializable{
	private static final long serialVersionUID = 2675242361263880997L;
	private String message;
	private int from, to;
	int[] clock = null;
	int lastDest = -1;
	int lastClock = -1;
	boolean isActive = false;
	
	//application message
	Message(int f, int t, String m, int[] c){
		from = f;
		to = t;
		message = m;
		clock = Arrays.copyOf(c, c.length);
	}
	
	//all other messages
	Message(int f, int t, String m){
		to = t;
		from = f;
		message = m;
	}
	
	//information message
	Message(int f, int t, String m, int[] c, int _lastDest, int _lastClock, boolean _isActive) {
		from = f;
		to = t;
		message = m;
		clock = Arrays.copyOf(c,  c.length);
		lastDest = _lastDest;
		lastClock = _lastClock;
		isActive = _isActive;
	}
	
	public String GetMessage(){
		return message;
	}
	
	public int GetTo(){
		return to;
	}
	
	public int GetFrom(){
		return from;
	}
	
	public int[] GetClock(){
		return clock;
	}
	
	public boolean SetMessage(String s){
		message = s;
		return true;
	}
	
	public boolean SetTo(int s){
		to = s;
		return true;
	}
	
	public boolean SetFrom(int s){
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