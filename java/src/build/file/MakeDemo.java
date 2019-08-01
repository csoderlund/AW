package build.file;

/**********************************************
 *
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import database.DBConn;

import build.Cfg;
import util.*;

public class MakeDemo {
	private Vector <String> chrStr = new Vector <String> ();
	private Vector <String> chrNum = new Vector <String> ();
	
	public MakeDemo (DBConn dbc, Cfg c, int action) {
		cfg = c;
		chrNum.add("19"); 
		chrStr.add("chr19"); 
		if (action==1) {
			//gsGTF();
			//variants();
			//varCov();
			//varSnpEff();
			ncbi(dbc);
		}
		else ncbi(dbc);
	}
	private void gsGTF() {
		String ensType = "protein_coding";
		String outFile = cfg.getGTK();
		String inFile = reverse(outFile);
		System.out.println("Write " + outFile);
		try {
			FileWriter out = new FileWriter(outFile, false);
			BufferedReader in = new BufferedReader ( new FileReader ( inFile ) ); 
			String line;
			int cnt=0, cnt1=0;
			while ((line = in.readLine()) != null) {
			
				String [] tok = line.split("\t");
				if (tok.length==0) continue;
				if (!tok[1].equals(ensType)) continue;
				cnt++;
				
				for (String chr : chrStr) {
					if (tok[0].equals(chr)) {
						out.write(line + "\n");
						cnt1++;
						break;
					}
				}
			}
			System.out.println("   Read " + cnt + " write " + cnt1);
			in.close();
			out.close();
		}
		catch (Exception e) {ErrorReport.die(e, "gsGTF");}
	}
	private void variants() {
		Vector <String> varFiles = cfg.getVarVec();

		for (String outFile : varFiles) { 
			String inFile = reverse(outFile);
			System.out.println("Write " + outFile);
			int cnt=0, cnt1=0;
			try {
				FileWriter out = new FileWriter(outFile, false);
				BufferedReader in = new BufferedReader ( new FileReader ( inFile ) ); 
				String line;
				while ((line = in.readLine()) != null) {
					cnt++;
					if (line.startsWith("#")) {
						out.write(line + "\n");
						continue;
					}
					String [] tok = line.split("\t");
					for (String chr : chrStr) {
						if (tok[0].equals(chr)) {
							out.write(line+ "\n");
							cnt1++;
							break;
						}
					}
				}
				System.out.println("   Read " + cnt + " write " + cnt1);
				in.close();
				out.close();
			}
			catch (Exception e) {ErrorReport.die(e, "variants");}
		}
	}
	private void varCov() {
		Vector <String> varFiles = cfg.getVarCovVec();
		String varDir = cfg.getVarCovDir();

		for (String list : varFiles) {
			String [] tok = list.split(":");
			String outFile =  varDir + "/" + tok[0];
			String inFile = reverse(outFile);
			int cnt=0, cnt1=0;
			System.out.println("Write " + outFile);
			try {
				FileWriter out = new FileWriter(outFile, false);
				BufferedReader in = new BufferedReader ( new FileReader ( inFile ) ); 
				String line;
				while ((line = in.readLine()) != null) {
					cnt++;
					tok = line.split("\t");
					for (String chr : chrStr) {
						if (tok[0].equals(chr)) {
							out.write(line+ "\n");
							cnt1++;
							break;
						}
					}
				}
				in.close();
				out.close();
				System.out.println("   Read " + cnt + " write " + cnt1);
			}
			catch (Exception e) {ErrorReport.die(e, "varCov");}
		}
	}
	private void varSnpEff() {
		Vector <String> varFiles = cfg.getVarAnnoVec();

		for (String outFile : varFiles) { 
			int cnt=0, cnt1=0;
			System.out.println("Write " + outFile);
			String inFile = reverse(outFile);
			
			try {
				FileWriter out = new FileWriter(outFile, false);
				BufferedReader in = new BufferedReader ( new FileReader ( inFile ) ); 
				String line;
				while ((line = in.readLine()) != null) {
					if (line.startsWith("#")) {
						out.write(line+ "\n");
						continue;
					}
					cnt++;
					if (line.contains("EFF=INTERGENIC")) continue;
					
					String [] tok = line.split("\t");
					for (String chr : chrStr) {
						if (tok[0].equals(chr)) {
							out.write(line+ "\n");
							cnt1++;
							break;
						}
					}
				}
				in.close();
				out.close();
				System.out.println("   Read " + cnt + " write " + cnt1);
			}
			catch (Exception e) {ErrorReport.die(e, "varSnpEff");}
		}
	}
	private void ncbi(DBConn mDB) {
		String outFile = cfg.getNCBI();
		String inFile = reverse(outFile);
		System.out.println("Write " + outFile);
		
		Pattern patLocus = Pattern.compile("LOCUS\\s+(\\w+)");
		Pattern patGene = Pattern.compile("/gene=\"(\\w+)\"");
	
		Matcher mat;
		int cnt=0, cnt1=0, done=0;
		int numKey=4;
		String line, locusLine="", geneLine="", synLine="", defLine="", gene="";
		boolean foundSlash=true, inDef=false, inSyn=false;
		
		try {
			Vector <String> genes = new Vector <String> ();
			for (String chr : chrNum) {
				ResultSet rs = mDB.executeQuery("Select geneName from gene where chr='" + chr + "'");
				while (rs.next()) genes.add(rs.getString(1));
			}
			FileWriter out = new FileWriter(outFile, false);
			BufferedReader in = new BufferedReader ( new FileReader ( inFile ) ); 
			
			while ((line = in.readLine()) != null) {
				if (line.equals("")) continue;
				if (foundSlash) { // start new
					cnt++;
					mat = patLocus.matcher(line);
					if (!mat.find()) {
						System.err.println("Error: " + line);
						continue;
					}
					locusLine = line;
					done=0;
					
					foundSlash=false;
					continue;
				}
				if (line.startsWith("//")) { // write current
					foundSlash=true;
					if (locusLine!="" && geneLine!="") {
						if (genes.contains(gene)) {
							out.write(locusLine+ "\n");
							out.write(defLine+ "\n");
							out.write(geneLine+ "\n");
							out.write(synLine+ "\n");
							out.write("//"+ "\n");
							cnt1++;
						}
					}
					locusLine=geneLine=synLine=defLine=gene="";
					if (inSyn) System.out.println("sym " + synLine);
					if (inDef) System.out.println("def " + defLine);
					inSyn=inDef=false;
					continue;
				}
				if (done==numKey) continue; 
	
				if (inDef) {
					if (line.endsWith(".")) {
						inDef=false; done++;
					}
					defLine += " " + line;
					continue;
				}
				
				if (inSyn) {
					if (line.endsWith("\"")) {
						inSyn=false; done++;
					}
					synLine += " " + line;
					continue;
				}
	
				if (defLine.equals("") && line.startsWith("DEFINI")) {
					defLine = line;
					if (!line.endsWith(".")) inDef=true;
					else done++;
				}
				else if (geneLine.equals("")) {
					mat = patGene.matcher(line);
					if (mat.find()) {
						gene = mat.group(1);
						geneLine = line;
						done++;
						continue;
					}
				}
				else if (synLine.equals("") && line.contains("/gene_synonym")) {
					synLine = line;
					if (!synLine.endsWith("\"")) inSyn = true;
					else done++;				
				}
				cnt++;
				if (cnt % 1000 == 0)  System.out.print(" Found " + cnt + " wrote " + cnt1 + "\r");
			}
			in.close();
			out.close();
		}
		catch (Exception e) {ErrorReport.die(e, "ncbi");}
	}
	private String reverse(String file) {
		String old = file + ".old";
		new File(file).renameTo(new File(old));
		return old;
	}
	Cfg cfg;
}
