package build.compute;

/**********************************************
 * Creates the overview that is shown on Overview page of viewHW
 */
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.text.DecimalFormat; 

import build.file.VarAnno;
import database.MetaData;
import database.DBConn;

import util.ErrorReport;
import util.Globals;
import util.Format;
import util.LogTime;

public class Overview {
	private final String odSNP = ASE.odSNP;
	private final String odREP = ASE.odREP; 
	private final String odRmk = ASE.odREMARK;
	
	private final String [] strains;
	private final String [] strAbbv;
	private final String [] tisAbbv;
	private final String [] tissues;
	private final int nStrains;
	private final int nTissues;
	
	private TreeMap <String, String> xSt2Strain = new TreeMap <String, String> ();
	private TreeMap <String, String> xTi2Tissue = new TreeMap <String, String> ();
	private TreeMap <String, String> xStrain2St = new TreeMap <String, String> ();
	private TreeMap <String, String> xTissue2Ti = new TreeMap <String, String> ();
	
	public Overview (DBConn dbc, String logDir) {
		long startTime = LogTime.getTime();
		String ovFile = logDir + "/" + Globals.ovFile;
		 LogTime.PrtDateMsg("Creating overview and writing to " + ovFile);
		 meta = new MetaData(dbc);
		 strains = meta.getStrains();
		 strAbbv = meta.getStrAbbv();
		 nStrains = strains.length;
		 tissues = meta.getTissues();
		 tisAbbv = meta.getTisAbbv();
		 nTissues = tissues.length;
				 
         mDB = dbc; 
         try {
	         for (int i=0; i<nStrains; i++) {
	        	 	xSt2Strain.put(strAbbv[i], strains[i]);
	        	 	xStrain2St.put(strains[i], strAbbv[i]);
	         }
	    
	         // if no cond2, then one tissue with value ""
	         for (int i=0; i<tissues.length; i++) {
	        	 	xTi2Tissue.put(tisAbbv[i], tissues[i]);
	        	 	xTissue2Ti.put(tissues[i], tisAbbv[i]);
	         }
	        makeLibs();
	         
	     	lines.add("___________________________________________________________");	
	         
	     	makeTotals();
			lines.add("___________________________________________________________");	
	         
			makePvalue();
			lines.add("___________________________________________________________");	
	         
			makeSNPcov();
			lines.add("___________________________________________________________");	
	         
	        makeFiles();
               	 	
    		 	String text = "";
    		 	for (int i=0; i< lines.size(); i++) 
    		 		text += lines.get(i) + "\n";
       		mDB.executeUpdate("update metaData set overview = \"" + text + "\""); 
       		
       		LogTime.PrtSpMsg(1, "Total size of rep libraries will only be written to " + ovFile);
       		lines.add("");
       		makeReps();
       		
       		text = MetaData.getOverview(mDB);
		 	PrintWriter pwObj = new PrintWriter(new FileOutputStream(ovFile, false)); 
        	 	pwObj.print(text);
        	 	pwObj.close();
        	 	
        	 	LogTime.PrtSpMsgTime(0, "Complete overview", startTime);
    		 }
    		 catch (Exception e) {
    			 ErrorReport.prtError(e, "Entering overview into database");
    		 }
	}
	private void makeLibs() {
		lines.add("");
		String msg="";
		boolean newline=false;
		msg = String.format("%-10s ", Globals.condition1);
		for (int i=0; i<strains.length; i++) {
			String t = strains[i] + "(" + strAbbv[i] +")";
			msg += String.format("%-15s ", t);
			if (!newline && i>5 && strains.length>7) {
				lines.add(msg); 
				msg="          "; newline=true;
			}
		}
		lines.add(msg);
		
		if (meta.hasCond2()) {
			newline=false;
			msg = String.format("%-10s ", Globals.condition2);
			for (int i=0; i<tissues.length; i++) {
				String t = tissues[i] + "(" + tisAbbv[i] + ")";
				msg += String.format("%-15s ", t);
				if (!newline && i>5 && tissues.length>7) {; 
					lines.add(msg); 
					msg="          "; newline=true;
				}
			}
			lines.add(msg);
		}
		
		String [] libs = meta.getLibAbbr();
		newline=false;
		msg = String.format("%-10s ", "Libraries");
		for (int i=0; i<libs.length; i++) {
			msg += String.format("%s ", libs[i]);
			if (!newline && i>8 && libs.length>10) {; 
				lines.add(msg); 
				msg="           "; newline=true;
			}
		}
		lines.add(msg);
		
	}
	// total
	private void makeTotals() {
		try {
			LogTime.PrtSpMsg(1, "Make Totals");
			int genes = mDB.executeCount("Select count(*) from gene");
			int geneSNP = mDB.executeCount("Select count(*) from gene where cntSNP>0");
			int geneIDL = mDB.executeCount("Select count(*) from gene where cntIndel>0");
			
			int trans = mDB.executeCount("Select count(*) from trans");
			int transSNP = mDB.executeCount("Select count(*) from trans where cntSNP>0");
			int transIDL = mDB.executeCount("Select count(*) from trans where cntIndel>0");
			int transAI = mDB.executeCount("Select count(*) from trans where cntLibAI>0");
			
			int nSnp = mDB.executeCount("Select count(*) from SNP where isSNP=1");
			int snpCov = mDB.executeCount("Select count(*) from SNP where cntLibCov>0");
			int snpAI = mDB.executeCount("Select count(*) from SNP where cntLibAI>0");
			int snpLibCov = mDB.executeCount("Select sum(cntLibCov) from SNP where cntLibCov>0");
			int snpLibAI = mDB.executeCount("Select sum(cntLibAI) from SNP where cntLibAI>0");
			int snpCod = mDB.executeCount("Select count(*) from SNP where isCoding>0 && isSNP=1");
			
			int indel = mDB.executeCount("Select count(*) from SNP where isSNP=0");
			int indCod = mDB.executeCount("Select count(*) from SNP where isCoding>0 && isSNP=0");
			
			int hetSNPrep = mDB.executeCount("Select count(*) from SNP where remark like '%" + odREP + "%'");
			int hetTransRep = mDB.executeCount("Select count(*) from trans where odRmk like '%" + odREP + "%'");
			int hetTransSNP = mDB.executeCount("Select count(*) from trans where odRmk like '%" + odSNP + "%'");
			
			String msg="";
			msg = String.format("%5s: %6d  %9s: %6d  %10s: %6d", 
					"Genes", genes, "With SNPs", geneSNP, "With Indel", geneIDL);
			lines.add(msg); 
			LogTime.PrtSpMsg(2, msg);
			
			msg = String.format("%5s: %6d  %9s: %6d  %10s: %6d  %2s: %d   [AI=Allele Imbalance (p<" + Globals.AI_PVALUE + ")]", 
					"Trans", trans, "With SNPs", transSNP, "With Indel", transIDL, 
					"AI", transAI);
			lines.add(msg); 
			LogTime.PrtSpMsg(2, msg);
			
			lines.add("");
			msg = String.format("%5s: %6d  %9s: %6d  %10s: %6d  %2s: %d  %s: %d  %s: %d", 
			        "SNPs", nSnp, "Coding", snpCod,  "Cov(>=" + Globals.MIN_READS + ")", snpCov,
			    		"AI", snpAI, "Lib Cov", snpLibCov, "Lib AI", snpLibAI);
			lines.add(msg); 
			LogTime.PrtSpMsg(2, msg);
			
			msg = String.format("%5s: %6d  %9s: %6d", "InDel", indel, "Coding", indCod);
			lines.add(msg); 
			LogTime.PrtSpMsg(2, msg);
			
			lines.add("");
			msg = String.format(odRmk + " %s: %d  %s: %d  %s: %d", 
				    "SNPs Reps", hetSNPrep, "Trans reps", hetTransRep,  "Trans SNPs", hetTransSNP);
			lines.add(msg); 
			
			// functions and substitutions
			TreeMap <String, Integer> effMap = new TreeMap <String, Integer> ();
			HashMap <String, Integer> covMap = new HashMap <String, Integer> ();
			HashMap <String, Integer> aiMap = new HashMap <String, Integer> ();	
			HashMap <String, Integer> polyMap = new HashMap <String, Integer> ();
			polyMap.put("GA", 0); polyMap.put("CT", 0); // transistions
			polyMap.put("GT", 0); polyMap.put("CA", 0); polyMap.put("AT", 0); polyMap.put("CG", 0); // transversions
			
			ResultSet rs = mDB.executeQuery("SELECT effectList, ref, alt, isSNP, " +
					" cntLibCov, cntLibAI from SNP");
			while (rs.next()) {
				String effectList = rs.getString(1);
				String ref = rs.getString(2);
				String alt = rs.getString(3);
				boolean isSNP = rs.getBoolean(4);
				int nLibCov = rs.getInt(5);
				int nLibAI = rs.getInt(6);
				
				if (isSNP) { // count AT, AC, AG, CG, CT, 
					String di1 = ref + alt;
					String di2 = alt + ref;
					
					if (polyMap.containsKey(di1)) {
						polyMap.put(di1, polyMap.get(di1)+1);
					}
					else if (polyMap.containsKey(di2)) {
						polyMap.put(di2, polyMap.get(di2)+1);
					}
				}
				if (effectList==null) continue;
				
				// e.g. missense_variant, SIFT=tolerated(0.86); intron_variant; missense_variant, SIFT=tolerated(0.87)
				HashSet <String> found = new HashSet <String> ();
				String [] list = effectList.split(VarAnno.DELIM);
				for (int i=0; i<list.length; i++) {
					String effect = list[i];
					if (list[i].contains(VarAnno.DELIM2)) 
						effect = list[i].split(VarAnno.DELIM2)[0]; 
						
					if (found.contains(effect)) continue;
					found.add(effect);
					
					if (!effMap.containsKey(effect)) effMap.put(effect,1);
					else effMap.put(effect, effMap.get(effect)+1);
					
					if (nLibCov>0) { // counting all libs
						if (!covMap.containsKey(effect)) covMap.put(effect,nLibCov);
						else covMap.put(effect, covMap.get(effect)+nLibCov);
					}
					if (nLibAI>0) {
						if (!aiMap.containsKey(effect)) aiMap.put(effect,nLibAI);
						else aiMap.put(effect, aiMap.get(effect)+nLibAI);
					}
				}
			}
			// polymorphism  counts
		
			double pSNPs = (double) nSnp;
			msg =  "Transitions:   " +     makeDi("GA", pSNPs, polyMap.get("GA"));
			msg +=                   "/" + makeDi("CT", pSNPs, polyMap.get("CT"));
			msg += "\n";
			msg += "Transversions: " + makeDi("GT", pSNPs, polyMap.get("GT"));
			msg +=                "/" + makeDi("CA", pSNPs, polyMap.get("CA"));
			msg +=                " " + makeDi("AT", pSNPs, polyMap.get("AT"));
			msg +=                " " + makeDi("CG", pSNPs, polyMap.get("CG"));
			lines.add("");
			lines.add(msg);
			lines.add("");
			
			// effect
			lines.add(String.format("%7s %7s %12s   %s", "Total", "#Lib Cov", "#Lib AI(%)", "Effect"));
			for (String fun : effMap.keySet()) {
				String x = (fun.equals("") ? "None" : fun);
				int c = (covMap.containsKey(fun)) ? covMap.get(fun) : 0;
				int a = (aiMap.containsKey(fun)) ? aiMap.get(fun) : 0;
				if (a>0 && c >0) {
					int p = (int)((((double) a/(double) c)*100.0)+0.5);
					lines.add(String.format("%7d %7d %8d(%02d)   %s", effMap.get(fun), c, a, p, x));
				}
				else lines.add(String.format("%7d %7d %8d%4s   %s", effMap.get(fun), c, a, "", x));
			}	
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "totals");
		}
	}
	private String makeDi(String di, double pSNPs, int cnt) {
		int per = (int) (((double) cnt / pSNPs)*100.0);
		return di + ":" + cnt + "(" + per + "%)";
	}
	private void makeFiles() {
		try {
			 LogTime.PrtSpMsg(1, "Add files");
			 ResultSet rs = mDB.executeQuery("SELECT type, name, tag from files");
			 while (rs.next()) 
				 lines.add(String.format("%-12s %-5s %s", rs.getString(1), 
						 rs.getString(3), rs.getString(2)));
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "Files");
		}
	}
	// xxx use pvalue
	private void makePvalue() throws Exception {
        LogTime.PrtSpMsg(1, "Make Pvalue tables");
		
		double [] cutoff = {0.00001, 0.0001,  0.001, 0.05};
		String [] pHeaders = {"", "Ref>Alt <0.00001", "<0.0001", "<0.001", "<0.05", 
				                  "Alt>Ref <0.00001", "<0.0001", "<0.001", "<0.05"};
		int nCol = pHeaders.length;
		int nRow = nTissues+1;
		int [] pJustify =  new int [nCol]; 
		pJustify[0] = 1;
		for (int i=1; i<nCol; i++) pJustify[i] = 0;
		rows = new String[nRow][nCol];
		
		int [] pCnt =  new int [cutoff.length]; 
		int [] nCnt =  new int [cutoff.length]; 
		int [] tpCnt =  new int [cutoff.length]; 
		int [] tnCnt =  new int [cutoff.length]; 
		
		MetaData meta = new MetaData(mDB);
		
		String[] titles = { "SNP Coverage Pvalue:",
				            "Gene Count Pvalue:"};
		String [] table = {"SNP", "gene"};
		
		for (int loop = 0; loop <= 1; loop++) {
			if (loop==1 && !meta.hasReadCnt()) break;
			lines.add(titles[loop]);
		
			for (int s=0; s<nStrains; s++) { 	// table per strain
				for (int i=0; i<cutoff.length; i++) tpCnt[i]= tnCnt[i] = 0;
				
				for (int t=0; t<nTissues; t++) {
					for (int i=0; i<cutoff.length; i++) pCnt[i] = nCnt[i] = 0;
					
					String lib = xStrain2St.get(strains[s]) + tisAbbv[t];
					String pcol = lib;
					if (loop>0) pcol += Globals.SUF_TOTCNT;
					String Sref = Globals.PRE_REFCNT + pcol; 	
					String Salt = Globals.PRE_ALTCNT + pcol; 	
					
					ResultSet rs = mDB.executeQuery("select " + Sref + "," + Salt + "," + pcol + 
							" from " + table[loop] + " where " + pcol + " !=" + Globals.NO_PVALUE);
					while (rs.next())
					{
						int ref = rs.getInt(1);
						int alt = rs.getInt(2);
						double p = rs.getDouble(3);
						
						for (int i=0; i<cutoff.length; i++) {
							if (p < cutoff[i]) {
								if (ref>alt) pCnt[i]++; 
								else nCnt[i]++;
							}
						}
					}
					rs.close();
					rows[t][0] = lib;
					int col=1;
					for (int i=0; i<cutoff.length; i++) {
						rows[t][col++] = format(pCnt[i]);
						tpCnt[i] += pCnt[i];
					}
					for (int i=0; i<cutoff.length; i++) {
						rows[t][col++] = format(nCnt[i]);
						tnCnt[i] += nCnt[i];
					}
				}	
				int row=nTissues, col=1;
				rows[row][0] = "Total";
				for (int i=0; i<cutoff.length; i++) rows[row][col++] = format(tpCnt[i]);
				for (int i=0; i<cutoff.length; i++) rows[row][col++] = format(tnCnt[i]);
				makeTable(nRow, nCol,  pHeaders, pJustify); 
				lines.add("");
			}
		}				
	}
	
	////////////////////////////////////////////////////////////////////////
	//  Expression count
	/// for each strain, make table of tissue where count #ref and #alt are from gene
	/// 
	private void makeSNPcov() {	
	    LogTime.PrtSpMsg(1, "Make SNP coverage table");
		String [] headers = {"", "Ref", "Alt", "Ref-Avg", "Alt-Avg", "Ref-Max", "Alt-Max", "Total"};
		int [] just =   {1,   0,   0,      0,      0,        0,        0,         0};
		int nCol = just.length;
		int nRow = nTissues+1;
	
		rows = new String[nRow][nCol];
		lines.add("SNP coverage counts");
		try {				
			for (int s=0; s<nStrains; s++) {
				long allTotRef=0, allTotAlt=0, allTotal=0;
				int r=0;
				for (int i=0; i<nCol; i++) 
					for (int j=0; j<nRow; j++) rows[j][i]="";
				
				for (int t=0; t<nTissues; t++) {
					long total=0, totRef=0, totAlt=0;
					int maxRef=0, maxAlt=0, cnt=0;
					
					String lib = strAbbv[s] + tisAbbv[t];
					ResultSet rs = mDB.executeQuery("Select refCount, altCount " +
						"from SNPlib where libName=" + quote(lib) + " and repNum=0");

					while (rs.next()) {
						int refCount = rs.getInt(1);
						int altCount = rs.getInt(2);
						total += refCount + altCount;
						totRef += refCount;
						totAlt += altCount;
						if (maxRef<refCount) maxRef=refCount;
						if (maxAlt<altCount) maxAlt=altCount;
						cnt++;
					}
					allTotRef += totRef;
					allTotAlt += totAlt;
					allTotal += total;
					
					int c=0;
					rows[r][c++] = tissues[t];
					rows[r][c++] =  df.format(totRef) + " (" + Format.percent(totRef,total) + ")";
					rows[r][c++] =  df.format(totAlt) + " (" + Format.percent(totAlt,total) + ")";
					
					rows[r][c++] =  String.format("%3.1f", ((double) totRef/ (double) cnt));
					rows[r][c++] =  String.format("%3.1f", ((double) totAlt/ (double) cnt));
				
					rows[r][c++] = df.format(maxRef);
					rows[r][c++] = df.format(maxAlt);
					rows[r][c++] = df.format(total);
					r++;
				}
				int c=0;
				rows[r][c++] = "Total";
				rows[r][c++] =  df.format(allTotRef) + " (" + Format.percent(allTotRef,allTotal) + ")";
				rows[r][c++] =  df.format(allTotAlt) + " (" + Format.percent(allTotAlt,allTotal) + ")";
				
				rows[r][c++] =  "-";
				rows[r][c++] =  "-";
				rows[r][c++] = "-";
				rows[r][c++] = "-";
				rows[r][c++] = df.format(allTotal);
				if (s>0) lines.add("");
				lines.add(strains[s]);
				makeTable(nRow, nCol, headers, just); 
			}	
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "create function summary");
		}
	}
	
	
	// This makes a table of total ref and alt counts for each individual sample
	private void makeReps() {
	    LogTime.PrtSpMsg(2, "Make Replicate count table");
		String [] headers = {"Lib", "SNP-Rep", "SNP-Alt", "Read-Rep", "Read-Alt"};
		int [] just =        {1,   0,   0, 0, 0};
		int nCol = just.length;
		int nRow = 5 * nStrains * nTissues;
		rows = new String[nRow][nCol];
		
		try {			
			int r=0;
			for (int s=0; s<nStrains; s++) {			
				
				for (int t=0; t<nTissues; t++) {
					String lib = strAbbv[s] + tisAbbv[t];
					// Do the totals first (rep=0)
					ResultSet rs = mDB.executeQuery("Select sum(refCount), sum(altCount), sum(refCount2), sum(altCount2) " +
							"from transLib where libName=" + quote(lib) + " and repNum=0");
					rs.first();
					rows[r][0] = lib ;
					rows[r][1] = format(rs.getInt(1));
					rows[r][2] = format(rs.getInt(2));
					rows[r][3] = format(rs.getInt(3));
					rows[r][4] = format(rs.getInt(4));
					r++;
				}
			}
			
			for (int s=0; s<nStrains; s++) {			
				
				for (int t=0; t<nTissues; t++) {
					String lib = strAbbv[s] + tisAbbv[t];
					for (int i=1; i<=4; i++ ) {
						ResultSet rs = mDB.executeQuery("Select sum(refCount), sum(altCount), sum(refCount2), sum(altCount2) " +
						"from transLib where libName=" + quote(lib) + " and repNum=" + i);
						rs.first();
						rows[r][0] = lib + i;
						rows[r][1] = format(rs.getInt(1));
						rows[r][2] = format(rs.getInt(2));
						rows[r][3] = format(rs.getInt(3));
						rows[r][4] = format(rs.getInt(4));
						r++;
					}			
				}
			}
			makeTable(nRow, nCol,  headers, just); 
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "create function summary");
		}
	}
	
	private void makeTable(int nRow, int nCol, String[] headers, int [] justify)
	{
		int c, r;
		String line;
		String space = "  "; // between columns
		String bspace = "   "; // beginning space
		
		// compute column lengths
		int []collen = new int [nCol];
		for (c=0; c < nCol; c++) collen[c] = 0;
		
        for (c=0; c< nCol; c++) {
            for (r=0; r<nRow && rows[r][c] != null; r++) {
            		if (rows[r][c] == null) rows[r][c] = "";
            		if (rows[r][c].length() > collen[c]) 
            			collen[c] = rows[r][c].length();
            }
        }
        if (headers != null) {
			for (c=0; c < nCol; c++) {
				if (collen[c] > 0) {
					if (headers[c].length() > collen[c]) 
						collen[c]=headers[c].length();
				}
			}
	        // output headings
	        line = bspace;
	        for (c=0; c< nCol; c++) 
	        		if (collen[c] > 0) 
	        			line += pad(headers[c],collen[c],1) + space;
	        lines.add(line);
        }
        // output rows
        for (r=0; r<nRow; r++) {
        		line = bspace;
            for (c=0; c<nCol; c++) {
                 if (collen[c] > 0) 
                	 	line += pad(rows[r][c],collen[c],justify[c]) + space;
                 rows[r][c] = ""; // so wouldn't reuse in next table
            }
            lines.add(line);
        }
	}
	
    private String pad(String s, int width, int o)
    {
    			if (s == null) return " ";
            if (s.length() > width) {
                String t = s.substring(0, width-1);
                LogTime.warn("'" + s + "' truncated to '" + t + "'");
                s = t;
                s += " ";
            }
            else if (o == 0) { // left
                String t="";
                width -= s.length();
                while (width-- > 0) t += " ";
                s = t + s;
            }
            else {
                width -= s.length();
                while (width-- > 0) s += " ";
            }
            return s;
    }
    
	private String quote(String word) {return "\"" + word + "\""; }
	 
	private String format(int n) {
		double d;
		String s;
		if (n > 1000000) {
			d = (double)n/1000000;
			s = String.format("%.1fM", d);
		}
		else s = Integer.toString(n);
		return s;
	}	
	/***********************************************************/
	private DBConn mDB;
	private MetaData meta;
	private String [][] rows = null;
	private Vector <String> lines = new Vector <String> ();
	private DecimalFormat df = new DecimalFormat("###,###,###,###");
}
