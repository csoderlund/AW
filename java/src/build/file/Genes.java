package build.file;
/*************************
 * read GTF for ensembl
	** two records can have the same gene_name but different gene_id
 */

import java.awt.Point;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;

import util.*;
import build.Cfg;
import database.DBConn;

public class Genes {
	private String GTFFile;
	private boolean isEnsembl=false, hasNames=false;
	private String ensType = "protein_coding";
	public static int nonCDSframe = -1; // exons that are not translated
	
	public Genes (DBConn dbc, Cfg c) {
		mDB = dbc; cfg = c;
		GTFFile = cfg.getGTK();
		
		try {
			long startTime = LogTime.getTime();
			LogTime.PrtDateMsg("Load " + GTFFile);
			
			setType();
			readGTK();
			
			LogTime.PrtSpMsgTime(0, "Complete loading gene and transcript coordinates", startTime );
		}
		catch (Exception e) {
			ErrorReport.die(e, "doing genes");
		}
	}
	// pattern is quote, not a quote, quote; the id is required but the name is not
	private static Pattern gtfPatTransName =   Pattern.compile("transcript_name\\s+\"([^\"]*)\"");   
	private static Pattern gtfPatTransID =   Pattern.compile("transcript_id\\s+\"([^\"]*)\"");   
	private static Pattern gtfPatGeneID =   Pattern.compile("gene_id\\s+\"([^\"]*)\"");   
	private static Pattern gtfPatGeneName = Pattern.compile("gene_name\\s+\"([^\"]*)\"");
	private static Pattern gtfPatExonNum = Pattern.compile("exon_number\\s+\"([^\"]*)\"");
	private static Pattern rootPat =    Pattern.compile("(\\D+)(\\d+)");
	
	private static String strStartCodon = "start_codon";
	private static String strEndCodon = "stop_codon";			
	private static String strCDS = "CDS"; 
	private static String strExon = "exon";
	
	private static int chrCol=0;
	private static int typeCol=1; // protein_coding, snRNA,  nonsense_mediated_decay; or program name
	private static int eleCol=2; // only use CDS, start_codon, end_codon as they are required
	private static int startCol=3;
	private static int endCol=4;
	private static int strandCol=6;
	private static int frameCol=7;
	private static int namesCol=8;
	
	private HashMap <String, String> IdNameMap = new HashMap <String, String> (); // check uniqueness
	private HashSet <String> geneNameList = new HashSet <String> ();	// check uniqueness
	
	private Gene curGene= new Gene();
	private Trans curTrans;
	private int cntDup=0, cntErr=0, addGene=0, addTrans=0;
	/***************************************************************/
	private void readGTK() {	
		try {	
			BufferedReader reader = new BufferedReader ( new FileReader ( GTFFile ) ); 
			Matcher x;
			int cntRead=0, cntPos=0, cntNeg=0, geneCntTrans=0;
			String line="";
			ArrayList <String> transNameExists = new ArrayList <String> ();
			String dupTransName=null;
			
			while ((line = reader.readLine()) != null) {
				String [] tok = line.split("\t");
				if (tok.length==0) continue;
				if (tok.length<namesCol) {
					LogTime.warn("Ignore: " + line);		
					continue;
				}
				cntRead++;
				if (isEnsembl && !tok[typeCol].equals(ensType)) continue;
				
				String geneId="";
				x = gtfPatGeneID.matcher(tok[namesCol]);
				if (x.find()) geneId = x.group(1);
				else ErrorReport.die("Invalid id: " + tok[namesCol]);
								
				// new gene, write last one and start new one
				if (!curGene.id.equals(geneId)) {
					if (geneCntTrans>0) addGeneToDB();
					geneCntTrans=0;
						
					String geneName="";
					x = gtfPatGeneName.matcher(tok[namesCol]);
					if (x.find()) geneName = x.group(1);
					else geneName = "Gene" + (addGene+1);
					
					String chr = tok[chrCol];
					if (!chrRoot.equals("")) {
						if (chr.startsWith(chrRoot)) chr = chr.substring(chrRoot.length());
						else LogTime.PrtWarn("First column does not start with " + chrRoot + "\nLine: " + line);
					}
					
					curGene.restart(geneName,  geneId, chr, tok[strandCol], tok[typeCol]);
					if (tok[strandCol].equals("+")) cntPos++; else cntNeg++;
				}
				// find coords of current entry
				int start = Integer.parseInt(tok[startCol]);
				int end = Integer.parseInt(tok[endCol]);	
				curGene.setCoords(start, end); // no matter what it is, want extent of gene
				
				if (!tok[eleCol].equals(strCDS)  && !tok[eleCol].equals(strExon) &&
					!tok[eleCol].equals(strStartCodon) && !tok[eleCol].equals(strEndCodon)) 
						continue;
					
				// transcript
				String transId="", exonNum="";
				x = gtfPatTransID.matcher(tok[namesCol]);
				if (x.find()) transId = x.group(1);
				else ErrorReport.die("Invalid transid: " + tok[namesCol]);
				
				x = gtfPatExonNum.matcher(tok[namesCol]);
				if (x.find()) exonNum = x.group(1);
				else ErrorReport.die("Invalid exon number: " + tok[namesCol]);	
				
				if (curTrans==null || !curTrans.id.equals(transId)) {	
					String transName;
					geneCntTrans++;
					
					x = gtfPatTransName.matcher(tok[namesCol]);
					if (x.find()) transName = x.group(1);
					else transName = "Trans" + (addGene+1) + "." + geneCntTrans;
					
					if (transNameExists.contains(transName)) {
						if (dupTransName==null)
							LogTime.warn("Duplicate transName " + transName + " ignoring");
						dupTransName = transName;
						continue;
					}
					else {
						transNameExists.add(transName);
						dupTransName=null;
					}
					curGene.addTrans(transName, transId);
				}	
				curTrans.updateTransCoords(start, end);
				
				if (tok[eleCol].equals(strStartCodon)) {
					curTrans.setStartCodon(start, end);
				}
				else if (tok[eleCol].equals(strEndCodon)) {
					curTrans.setEndCodon(start, end);
				}	
				else if (tok[eleCol].equals(strExon)) {
					curTrans.addExon(start, end, nonCDSframe, exonNum);
				}
				else if (tok[eleCol].equals(strCDS)) {
					int f=-1;
					if (Format.isInteger(tok[frameCol])) f=Integer.parseInt(tok[frameCol]);
					curTrans.addExon(start, end, f, exonNum);
				}
				
				if (cntRead % 10000 == 0) 
					LogTime.r("   Read " + cntRead + "; Genes " + addGene + 
							"; dup gene " + cntDup + "; Trans " + addTrans );
			}	
			addGeneToDB();
			reader.close();
			LogTime.PrtSpMsg(2, "Read: " + cntRead + " Genes: " + addGene + " Trans: " + addTrans + "                 ");
			LogTime.PrtSpMsg(2, "Dup gene: " + cntDup + " Pos strand: " + cntPos + " Neg strand: " + cntNeg);
			if (cntErr>0) LogTime.PrtSpMsg(2, "Errors on length: " + cntErr);
			if (addGene==0 && addTrans==0) ErrorReport.die("No genes or transcripts added");
			
			int xx = (hasNames) ? 1 : 0;
			mDB.executeUpdate("Update metaData set hasNames=" + xx + ", chrRoot='" + chrRoot + "'");
		}
		catch (Exception e) {ErrorReport.die(e, "reading " + GTFFile);}
	}
	/*******************************************************
	 * Add this gene with its transcripts and exons to database
	 */
	private void addGeneToDB() {
		curGene.finish();
		int GENEid = addGene();
		addTrans(GENEid);
	}
	private int addGene() {
		try {
			int GENEid=0;
			if (IdNameMap.containsKey(curGene.id)) { // geneIds should be unique 
				ErrorReport.die("Duplicate geneID " + curGene.id + " for " + curTrans.id);
			}
			int s=1;
			String tmp = curGene.name;
			String low = tmp.toLowerCase();
			String name= curGene.name;
			while (geneNameList.contains(low)) { // geneNames can be duplicated
				name = tmp + "_" + s;
				low = name.toLowerCase();
				s++;
				cntDup++;
			}
			geneNameList.add(low);
			IdNameMap.put(curGene.id, name);
			
			mDB.executeUpdate("INSERT gene SET " +		
					" geneName=" 	+ quote(name) +  
					",geneIden="	+ quote(curGene.id) +
					",chr=" 		+ quote(curGene.chr) + 
					",start=" 	+ curGene.start + 
					",end=" 		+ curGene.end + 
					",strand="	+ quote(curGene.strand) + 
					",cntUniqueExons=" + curGene.nExons +
					",cntTrans=" + curGene.trans.size() +
					",type="		+ quote(curGene.type)
			);
	
			ResultSet rs = mDB.executeQuery("select last_insert_id() as pid");  
	        if (rs.next()) GENEid = rs.getInt("pid");
	        else ErrorReport.die("Cannot get last_insert_id in GENEs");	
	        rs.close();
	        
	        addGene++;
	        return GENEid;
		}
		catch (Exception e) {ErrorReport.die(e, "insert gene " + curGene.id);}
		return 0;
	}
	private void addTrans(int GENEid) {
		String trName="";
		
		try { // compute UTR in genTrans because introns may be spliced in non-coding exons
			PreparedStatement ps0 = mDB.prepareStatement("INSERT trans SET " +
				" geneid="	+ GENEid + ", chr='" + curGene.chr + "',strand='" + curGene.strand + "'" +
				",transName=?, transIden=?, start=?, end=?, startCodon=?, endCodon=?, cntExon=?");
				
			// mDB.openTransaction(); screws up insert on mariaDB, even with two loops
			for (Trans tr : curGene.trans) {
				trName = tr.name;
				ps0.setString(1, tr.name);
				ps0.setString(2, tr.id);
				ps0.setInt(3, tr.start);
				ps0.setInt(4, tr.end);
				ps0.setInt(5, tr.startCodon);
				ps0.setInt(6, tr.endCodon);
				ps0.setInt(7, tr.exonList.size());
				ps0.executeUpdate();
				
				ResultSet rs = mDB.executeQuery("select last_insert_id() as pid");  
		        if (rs.next()) tr.sqlID = rs.getInt("pid");
		        else ErrorReport.die("Cannot get last_insert_id in GENEs");
		        rs.close();
				addTrans++;
			
				tr.finishExons();
				StringBuilder sb = new StringBuilder();
				sb.ensureCapacity(1000);
				sb.append("INSERT INTO transExon (TRANSid, transName, cStart, cEnd, frame, intron, nExon,  chr) VALUES");
				boolean first=true;
				for (Exon ex : tr.exonList) {
					if (!first) sb.append(",");
					first = false;
					sb.append("("); 
					sb.append(tr.sqlID);			sb.append(",");
					sb.append(quote(tr.name)); 	sb.append(",");
					sb.append(ex.start);			sb.append(","); 
					sb.append(ex.end);			sb.append(","); 
					sb.append(ex.frame);			sb.append(","); 
					sb.append(ex.intron); 		sb.append(",");
					sb.append(ex.num);			sb.append(","); 
					sb.append(quote(curGene.chr)); 
					sb.append(")");
				}	
				mDB.executeUpdate(sb.toString());
			}
		}
		catch (Exception e) {ErrorReport.die(e, "insert trans " + trName);}
	}
	/***********************************************
	 * Ensembl has many types, e.g. protein coding, pseudogenes, etc. Only want protein coding
	 * But others may be all protein coding and the name can be based on the source program
	 */
	private void setType() {
		try {	
			Matcher m;
			BufferedReader reader = new BufferedReader ( new FileReader ( GTFFile ) ); 
			String line="";
			int cnt=0;
			while ((line = reader.readLine()) != null) {
				String [] tok = line.split("\t");
				if (tok.length==0) continue;
				if (tok.length<namesCol) continue;
				
				if (chrRoot.equals("")) {
					m = rootPat.matcher(tok[chrCol]);
					if (m.find()) chrRoot = m.group(1);
				}
				m = gtfPatTransID.matcher(tok[namesCol]);
				if (m.find()) hasNames=true;
				else hasNames=false;	
				
				if (tok[typeCol].equals(ensType)) isEnsembl=true;
				cnt++;
				if ((isEnsembl || cnt>10000) && !chrRoot.equals("")) break;
			}
			reader.close();
			if (isEnsembl) LogTime.PrtSpMsg(1, "GTF file is probably from Ensembl");
			else           LogTime.PrtSpMsg(1, "GTF file is from unknown source");
			if (!chrRoot.equals(""))  
				           LogTime.PrtSpMsg(1, "Seqname prefix is '" + chrRoot + "'");
			else           LogTime.PrtWarn("Could not find a root (e.g. chr) for the column");
		}
		catch (Exception e) {ErrorReport.die("Reading " + GTFFile);}
	}
	private String check(String t, String n) {
		if (n.length() >Globals.nameLen) {
			cntErr++;
			if (cntErr<10)       LogTime.PrtError(t + " name over " + Globals.nameLen + " chars: " + n + "  truncating...");
			else if (cntErr==10) LogTime.PrtError("Surpressing errors on length");
			return n.substring(0,Globals.nameLen);
		}
		return n;
	}
	/************* XXX Data structures *********************/
    // One current gene
	private class Gene {
		String name="", id="", chr, strand, type;
		int start, end, nExons; 
		boolean isPos;
		ArrayList<Trans> trans = new ArrayList<Trans> (); 
		
		public void restart(String n, String i, String c, String s, String t) {
			name = check("Name", n);
			id = check("ID", i);
			chr = check("Seqname (e.g. chr)", c);
			strand = s;
			type = check("Source", t);  
			isPos = (s.equals("+")) ? true : false;
			trans = new ArrayList<Trans> (); 
			start = end = -1;
		}
		public void setCoords(int s, int e) {
			if (isPos) {
				if (start==-1 || start > s) start=s;
				if (end < e) end = e;
			}
			else {
				if (end==-1 || end > s) end = s;
				if (start < e) start = e;
			}
		}
		public void addTrans(String n, String i) {
			Trans tr = new Trans(n, i, isPos);
			trans.add(tr);
			curTrans = tr;
		}
		public void finish() {
			ArrayList<Point> exons = new ArrayList<Point> ();
			
			for (Trans tr : trans) {
				// determine number of unique exons
				for (Exon ex : tr.exonList) {
					int estart = ex.start;
					int eend = ex.end;
					
					Point thisExon = new Point(estart,eend);
					boolean add=true;
					for (Point p : exons) 
						if (p.x == estart && p.y == eend) {
							add=false; break;
						}
					if (add) exons.add(thisExon);
				}
			}
			nExons = exons.size();
		}
	}
	// trans class - many per gene
	private class Trans {
		String name="", id="";
		int start=0, end=0, startCodon=0, endCodon=0;
		boolean isPos;
		ArrayList<Exon> exonList = new ArrayList<Exon> ();
		int sqlID=0;
	
		public Trans(String n, String i, boolean ip) {
			name = check("transName",n);
			id = check("TransID", i);
			isPos=ip;
		}
		public void setStartCodon(int s, int e) {
			startCodon = (isPos) ? s : e;
		}
		public void setEndCodon(int s, int e) {
			endCodon= (isPos) ? s : e;
		}
		public void finishExons () {
			for (int e=1; e<exonList.size(); e++) {
				 Exon ex0 = exonList.get(e-1);
				 Exon ex1 = exonList.get(e);
				
				 if (ex1.end >= ex0.start && ex1.start <= ex0.end) ex1.ignore=true;
				 else if (ex0.start >= ex1.end && ex0.end <= ex1.start) ex0.ignore=true;
			 }
			if (isPos) {
				for (int e=0; e<exonList.size()-1; e++) {
					Exon ex0 = exonList.get(e);
					Exon ex1 = exonList.get(e+1);
					ex0.intron = Math.abs((ex0.end-ex1.start)+1);
				}
			}
			else {
				for (int e=exonList.size()-1; e>1;  e--) {
					Exon ex0 = exonList.get(e);
					Exon ex1 = exonList.get(e-1);
					ex0.intron = Math.abs((ex0.end-ex1.start)+1);
				}
			}
		}
		
		public void addExon(int s, int e, int f, String n) {
			for (Exon ex : exonList) {
				if (ex.start==s && ex.end==e) {
					if (f!=nonCDSframe) ex.frame=f;
					return;
				}
			}
			exonList.add(new Exon(s,e,f, n));
		}
		public void updateTransCoords(int s, int e) {
			if (isPos) {
				if (start==0 || start > s) start=s;
				if (end < e) end = e;
			}
			else {
				if (end==0 || end > s) end = s;
				if (start < e) start = e;
			}
		}
	}
	private class Exon {
		public Exon (int s, int e, int f, String n) {
			start = s;
			end = e;
			frame = f;
			num = n;
			intron=0;
		}
		int start, end, frame, intron;
		String num;
		boolean ignore=false;
	}

	private String quote(String word) {return "'" + word + "'"; }
	private String chrRoot="";
	private DBConn mDB;
	private Cfg cfg;
}
