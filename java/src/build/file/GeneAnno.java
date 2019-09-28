package build.file;

/************************************************************
 * Read file from Genbank for annotation and enter into gene table.
 * Write ncbi.seq to use for annotation in TCW (contains description entered into HW gene table)
 * 
LOCUS       NM_013715               1731 bp    mRNA    linear   ROD 17-APR-2013
DEFINITION  Mus musculus COP9 (constitutive photomorphogenic) homolog, subunit
            5 (Arabidopsis thaliana) (Cops5), transcript variant 1, mRNA.
                     /gene="Cops5"
                     /gene_synonym="AI303502; CSN5; Jab1; Mov34; Sgn5"
                     /note="COP9 (constitutive photomorphogenic) homolog,
                     subunit 5 (Arabidopsis thaliana)"
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.*;
import build.Cfg;
import database.DBConn;

public class GeneAnno {
	private String ncbiFile;
	
	public GeneAnno(DBConn dbc, Cfg c) {
		mDB = dbc; cfg = c;
		try {
			mDB.executeUpdate("Update gene set synonyms='', descript=''");
			mDB.executeUpdate("Update trans set descript=''");
		}
		catch (Exception e) {ErrorReport.die(e, "NCBI processing");}
		if (!cfg.hasNCBIFile()) {
			LogTime.PrtSpMsg(1, "No NCBI file defined in cfg file -- skipping step");
			return;
		}
		ncbiFile = cfg.getNCBI();
		
		long time = LogTime.getTime();
		LogTime.PrtDateMsg("Loading NCBI annotation " + ncbiFile);
		readFile();
		LogTime.PrtSpMsgTime(1, "Finish loading NCBI annotation ", time);
	}
	
	private void readFile () {
		Pattern patLocus = Pattern.compile("LOCUS\\s+(\\w+)");
		Pattern patDef = Pattern.compile("^DEFINITION\\s+(.*)$");
		Pattern patGene = Pattern.compile("/gene=\"(\\w+)\"");
		Pattern patSyn = Pattern.compile("/gene_synonym=\"(.*)$"); 
		Pattern patTrans = Pattern.compile("/translation=\"(.*)$");
		
		Matcher mat;
		try {		
			ResultSet rs = mDB.executeQuery("Select GENEid, geneName from gene");
			while (rs.next()) {
				geneMap.put(rs.getString(2), rs.getInt(1));
			}
      	 	rs.close();
      	 	
			BufferedReader reader = new BufferedReader ( new FileReader ( ncbiFile ) ); 
			int cnt=0, cnt1=0, done=0, nf=0;
			int numKey=5;
			String line, locus="", gene="", syn="", def="", trans="";
			boolean foundSlash=true, inDef=false, inSyn=false, inTrans=false;
			
			while ((line = reader.readLine()) != null) {	
				line = line.trim();
				if (line.equals("")) continue;
				if (cnt % 1000 == 0) 
					System.out.print(" Found " + cnt + " entered " + cnt1 + "\r");
				if (foundSlash) { // start new
					cnt++;
					mat = patLocus.matcher(line);
					if (!mat.find()) {
						System.err.println("Error: " + line);
						continue;
					}
					locus = mat.group(1);
					done=0;
					
					foundSlash=false;
					continue;
				}
				if (line.startsWith("//")) { // write current
					foundSlash=true;
					if (locus!="" && gene!="") {
						if (!geneMap.containsKey(gene)) {
							nf++;
							locus=""; done=numKey;
						}
						else {
							if (def.startsWith("Mus musculus")) def = def.substring(13);
							def = def.replace("'", "");
							syn = syn.replace("'", "");
							syn = locus + "," + syn;
							mDB.executeUpdate("UPDATE gene SET " +
									"synonyms=" + quote(syn) + "," +
									"descript=" + quote(def)+ " where GENEid=" + geneMap.get(gene));
							mDB.executeUpdate("UPDATE trans SET " + 
									"descript=" + quote(def)+ " where GENEid=" + geneMap.get(gene));
							cnt1++;
							geneMap.put(locus, 0); // writes all unfound genes with sequence of n'a
						}
					}
					locus=gene=syn=def=trans="";
					if (inSyn) System.out.println("sym " + gene);
					if (inDef) System.out.println("def " + gene);
					inSyn=inDef=inTrans=false;
					continue;
				}
				if (done==numKey) continue; 
	
				if (inDef) {
					if (line.endsWith(".")) {
						inDef=false; done++;
					}
					def += " " + line;
					continue;
				}
				
				if (inSyn) {
					if (line.endsWith("\"")) {
						inSyn=false; done++;
						line = line.substring(0, line.length()-1);
					}
					syn += " " + line;
					continue;
				}
				if (inTrans) {
					if (line.endsWith("\"")) {
						inTrans=false; done++;
						line = line.substring(0, line.length()-1);
					}
					trans += line;
					continue;
				}
				// 
				if (def.equals("")) {
					mat = patDef.matcher(line);
					if (mat.find()) {
						def = mat.group(1);
						if (!line.endsWith(".")) inDef=true;
						else done++;
						continue;
					}
				}
				if (gene.equals("")) {
					mat = patGene.matcher(line);
					if (mat.find()) {
						gene = mat.group(1);
						done++;
						continue;
					}
				}
				if (syn.equals("") && line.contains("/gene_synonym")) {
					mat = patSyn.matcher(line);
					if (mat.find()) {
						syn = mat.group(1);
						if (!syn.endsWith("\"")) inSyn = true;
						else {
							syn = syn.substring(0,syn.length()-1);
							done++;
						}
					}			
				}
				if (trans.equals("") && line.contains("/translation")) {
					mat = patTrans.matcher(line);
					if (mat.find()) {
						trans = mat.group(1);
						if (!trans.endsWith("\"")) inTrans = true;
						else {
							trans = trans.substring(0,trans.length()-1);
							done++;
						}
					}			
				}
			}
			LogTime.PrtSpMsg(2, "Found: " + cnt + "  Entered: " + cnt1 + " " + "  Not found: " + nf);
			reader.close();
		}
		catch (Exception e) {
			ErrorReport.die(e, "NCBI processing");
		}
	}
	private String quote(String word) {
		return "'" + word + "'"; 
	}
	TreeMap <String, Integer> geneMap = new TreeMap <String, Integer> ();
	private DBConn mDB;
	private Cfg cfg;
}
