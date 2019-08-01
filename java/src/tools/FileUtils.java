package tools;
/************************************************
 * readGTK output the coordinates for a single gene (prompted for)
 * I don't know what extract was for.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.ErrorReport;
import util.LogTime;
import database.DBConn;

public class FileUtils {

	public FileUtils(String mode)
	{
		try
		{
			if (mode.equals("extract"))
			{
				File listFile = assertExists(Tmain.selList);
				File b6File = assertExists(Tmain.b6Trans);
				File b6File2 = assertExists(Tmain.b6Trans2);
				File bcFile = assertExists(Tmain.bcTrans);
				File outFile = newFile(Tmain.outTrans);			
				extract(listFile, b6File, b6File2, bcFile, outFile);
			}
			else {
				readGTK(Tmain.ensmFile);
			}
		}
		catch (Exception e) {
			ErrorReport.die(e, "FileUtil");
		}
	}
	/**************************
	 * Write out the records for a single gene, i.e. element_type start end
	 */
	private void readGTK(String gtf) {	
		String searchStr = LogTime.getStrStdin("Enter transcript name", "Ighmbp2-003");
			
		Pattern gtfPatID =   Pattern.compile("transcript_id\\s+\"(\\w+)\"");
		Pattern gtfPatName = Pattern.compile("transcript_name\\s+\"([^\"]*)\"");
		Pattern exonPatNum = Pattern.compile("exon_number\\s+\"([^\"]*)\"");
		int chrCol=0;
		int typeCol=1;
		int eleCol=2;
		int startCol=3;
		int endCol=4;
		int strandCol=6;
		int frameCol=7;
		int namesCol=8;
		
		try {	
			BufferedReader rGtf = new BufferedReader ( new FileReader ( gtf ) );
					
			String line, name="", exon="";
			
			boolean foundRec=false;
			while ((line = rGtf.readLine()) != null) {	
				line = line.trim();
				if (line.equals("")) continue;
				
				String [] tok = line.split("\t");
				//if (tok[eleCol].equals("exon")) continue;
				
				Matcher x = gtfPatName.matcher(tok[namesCol]);
				if (x.find()) name = x.group(1);
				else die("Invalid name: " + tok[namesCol]);
				
				x = exonPatNum.matcher(tok[namesCol]);
				if (x.find()) exon = x.group(1);
				else die("Invalid exon: " + tok[namesCol]);
				
				if (!searchStr.equals(name)) {
					if (foundRec) System.exit(0);
					else continue;
				}
				else if (!foundRec) {
					foundRec=true;
					System.out.println(">> " + searchStr);
				}
				
				System.out.format("\t%s %-20s %2s %10s %10s %s %s\n", tok[chrCol], tok[eleCol], 
						exon, tok[startCol], tok[endCol], tok[frameCol], tok[strandCol]);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**************************************************************
	 * Extract sequences and created a B6/Bc sequence list.
	 */
	private void extract(File listF, File b6F, File b6F2, File bcF, File outF) {
		try {		
			// read list of ensembl identifiers 
			BufferedReader br = new BufferedReader(new FileReader(listF));	
			ArrayList <String> selList = new ArrayList <String> ();
			ArrayList <String> selCom = new ArrayList <String> ();
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#") || line.length()==0) continue;
				String id, comment;
				if (line.contains("#")) {
					id = line.substring(0, line.indexOf("#"));
					comment = line.substring(line.indexOf("#"));
				}
				else {
					id = line;
					comment = "#";
				}
				selList.add(id.trim());
				selCom.add(comment);
			}
			br.close();
			int nSeq = selList.size();
			System.out.println("Candidate " + nSeq + " proteins");
			String [] B6seq = new String [nSeq];
			String [] B6seq2 = new String [nSeq];
			String [] Bcseq = new String [nSeq];
			for (int i=0; i<nSeq; i++) B6seq[i]=Bcseq[i]=B6seq2[i]="";
					
			// read both inbred files for the same ensembl identifiers
			Pattern pepPatID =   Pattern.compile("transcript:(\\w+)"); 
			String name="";
			for (int f=0; f<3; f++) {
				String [] seq;
				if (f==0) {
					System.out.println("Reading B6 ensembl file");
					br = new BufferedReader(new FileReader(b6F));
					seq = B6seq;
				}
				else if (f==1) {
					System.out.println("Reading B6 rsem file");
					br = new BufferedReader(new FileReader(b6F2));
					seq = B6seq2;
				}
				else  {
					System.out.println("Reading Bc rsem file");
					br = new BufferedReader(new FileReader(bcF));	
					seq = Bcseq;
				}
				int index=0, cnt=0;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.startsWith("#") || line.length()==0) continue;
					
					if (line.startsWith(">")) {
						Matcher  x = pepPatID.matcher(line);
						if (x.find()) name = x.group(1);
						else name = line.substring(1);
						
						index = -1;
						for (int i=0; i<nSeq && index== -1; i++) {
							if (selList.get(i).equals(name)) index = i;
						}
						if (index!=-1) {			
							cnt++; 
							System.out.println("    " + cnt + " " + name);
						}
					}
					else if (index!=-1) {
						seq[index] += line;
					}
				}
				br.close();		
			}
			System.out.println("Write to file");
			BufferedWriter bw = new BufferedWriter(new FileWriter(outF, true));
			for (int i=0; i<nSeq; i++) {
				name = selList.get(i);
				String comment = selCom.get(i);
				
				if (B6seq[i] !="") 
					output("B6", name, comment, B6seq[i], bw);
				else if (B6seq2[i]!="") 
					output("B6", name, comment, B6seq2[i], bw);
				else System.out.println("No B6 " + name + " " + comment); 
				
				if (Bcseq[i].endsWith("*")) 
					Bcseq[i] = Bcseq[i].substring(0,Bcseq[i].length()-1);
				
				if (Bcseq[i]!="") 
					output("Bc", name, comment, Bcseq[i], bw);
				else System.out.println("No Bc " + name + " " + comment); 
				
				compare(">", name, comment, B6seq[i], Bcseq[i]);
				//if (B6seq[i]!="" && B6seq2[i]!="") 	compare(" ", name, B6seq[i], B6seq2[i]);
			}
			bw.close();
		}
		catch (Exception e) {
			ErrorReport.die(e, "extract");
		}
	}
	private void output(String prefix, String name, String comment, String seq, BufferedWriter bw) {
		try {		
			bw.write(">" + prefix + "_" + name + "   " + comment + "\n");
			int inc=80, len=seq.length();
			for (int i=0, j=inc; i< len; i+=inc, j+=inc) 
				if (j<len) bw.write(seq.substring(i, j) + "\n");
				else  bw.write(seq.substring(i)+"\n");
		}
		catch (Exception e) {
			ErrorReport.die(e, "output");
		}
	}
	private void compare(String msg, String name, String comment, String seq1, String seq2) {
		if (seq1.length()==seq2.length())
			System.out.format("   %s EQ %20s %5d %s\n   ", msg, name, seq1.length(), comment);
		else 
			System.out.format("   %s NE %20s %5d %5d %s\n   ", 
					msg, name, seq1.length(), seq2.length(), comment);
		int cnt=0;
		char [] s1 = seq1.toCharArray();
		char [] s2 = seq2.toCharArray();
		for (int i=0; i<seq1.length() && i<seq2.length(); i++) {
			if (s1[i]!=s2[i]) {
				cnt++;
				if (cnt<8) System.out.format("   %5d %c %c", i, s1[i], s2[i]);
			}
		}
		System.out.println("\n      Diff: " + cnt);
	}
	File assertExists(String fs)
	{
		File f = new File(fs);
		if (!f.exists()) 
		{
			System.err.println("File/Dir " + fs + " not found"); 
			System.exit(0);
		}	
		return f;
	}
	File newFile(String fs) throws Exception
	{
		File f = new File(fs);
		if (f.exists()) f.delete();
		f.createNewFile();
		if (!f.isFile()) 
		{
			System.err.println("File " + fs + " could not be created"); 
			System.exit(0);
		}	
		return f;
	}
	
	private void die(String msg) {
		System.out.println(msg);
		System.exit(0);
	}
}
