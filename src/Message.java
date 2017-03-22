import java.io.*;
import java.util.*;

class Message implements Serializable{
	private static final long serialVersionUID = 2675242361263880997L;
	private String message;
	private int from, to;
	int[] clock = null;
	Map<Integer, int[]> clocks = null;
	Map<Integer, int[]> msgValues = null;
	Map<Integer, Boolean> status = null;
	
	Message(int f, int t, String m, int[] c){
		from = f;
		to = t;
		message = m;
		clock = Arrays.copyOf(c, c.length);
	}
	
	Message(int f, int t, String m){
		to = t;
		from = f;
		message = m;
	}
	
	Message(int f, int t, String m, int[] c, Map<Integer, int[]> map, int lastDest, int lastClock, Map<Integer, int[]> msgs, boolean isActive, Map<Integer, Boolean> _status) {
		from = f;
		to = t;
		message = m;
		
		clocks = map;
		clocks.put(f, c);
		
		msgValues = msgs;
		int[] temp = {lastDest, lastClock};
		msgs.put(f, temp);
		
		status = _status;
		status.put(f, isActive);
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