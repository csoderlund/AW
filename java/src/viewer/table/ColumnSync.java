package viewer.table;

import java.util.Vector;
import util.Globals;
import viewer.ViewerFrame;

public class ColumnSync {
	public ColumnSync(ViewerFrame f, String n) {
		theViewerFrame = f;
		name = n;
		for (int i=0; i<typeRefAlt.length; i++) typeRefAlt[i]=false;
	}
	public void setColDefaults(int s, int c, boolean [] cb) {
		if (allColIsChk!=null) return;
		
		allColIsChk = new boolean [cb.length];
		for (int i=0; i<cb.length; i++) allColIsChk[i] = cb[i];
	}
	// last checked
	public void setColIsChk(boolean [] cb) {
		for (int i=0; i<cb.length; i++) allColIsChk[i] = cb[i];
	}
	public boolean [] getColIsChk() { return allColIsChk;}
	
	// last order
	public void setOrderedColumns(String [] col) {
		orderedColumns = col;
	}
	public String [] getOrderedColumns() {return orderedColumns;}
	
	// query or selected libs
	public void setLibs(Vector <String> sync) {
		if (sync==null) return;
		syncLibs = new Vector <String> ();
		
		for (int i=0; i<sync.size(); i++) syncLibs.add(sync.get(i));
	}
	public Vector <String> getLibs() {return syncLibs; }
	
	// query or selected abbr
	public void setAbbr(Vector <String> sync) {
		if (sync==null || sync.size()==0) return;

		syncAbbr = new Vector <String> ();
		for (int i=0; i<sync.size(); i++) syncAbbr.add(sync.get(i));
	}
	public Vector <String> getAbbr() {return syncAbbr; }
	
	// Library group settings
	public void setType(boolean [] type) {
		for (int i=0; i<type.length; i++) typeRefAlt[i]=type[i];
	}
	public boolean getType(int index) {return typeRefAlt[index];}
	public boolean [] getType() { return typeRefAlt;}
	
	private void print (String name, Vector <String> x) {
		System.out.println(name);
		for (int i=0; i<x.size(); i++) System.out.print(x.get(i) + " ");
		System.out.println();
	}
	private ViewerFrame theViewerFrame = null;
	private String name=""; 
	
	private String [] orderedColumns = null;
	private boolean [] allColIsChk = null;
	
	private Vector <String> syncLibs = null;	 // Selected from query or column panel 
	private Vector <String> syncAbbr = null;	 // ditto, just the strain or tissue abbr
	private boolean [] typeRefAlt = new boolean [Globals.numType];
}
