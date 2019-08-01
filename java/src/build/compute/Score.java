package build.compute;

import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.Align;
import util.ErrorReport;
import util.LogTime;
import database.DBConn;

public class Score {
	private boolean STDOUT=false;
	
	public Score(DBConn m) {
		mDB = m;
		try {
			int cnt =  mDB.executeCount("Select count(*) from sequences where parent=0");
			if (cnt==0) return;
			cnt =  mDB.executeCount("Select count(*) from trans where cntMissense>0");
			if (cnt==0) return;
		}
		catch (Exception e) {ErrorReport.prtError(e, "Cannot read database for Score "); return;}
		
		long startTime = LogTime.getTime();
		LogTime.PrtDateMsg("Start RSCU/biochem computations");
		calcRSCU();
		LogTime.PrtSpMsgTime(0, "Complete codon computations", startTime);
	}	
	/********************************************
	 * get frequency of codons from the highest expressed genes
	 */
	private void calcRSCU() {
		try {		
			TreeMap <String, Integer> codonFreq = new TreeMap <String, Integer> ();
			int codonCnt=0;
			Vector <String> bestNT = new Vector <String> ();
	
			ResultSet rs = mDB.executeQuery("SELECT seq from sequences " +
					" join trans on trans.TRANSid=sequences.TRANSid " +
					" where trans.rank=1 and cntSNPCov>0 and sequences.parent=0");
			while (rs.next()) bestNT.add(rs.getString(1));
			if (bestNT.size()==0) return;
			
			for (String seq : bestNT) {
				for (int i=0; i<seq.length()-2; i+=3) {
					String codon = seq.substring(i,i+3);
					if (!codonFreq.containsKey(codon)) codonFreq.put(codon, 0);
					codonFreq.put(codon, codonFreq.get(codon)+1);
					codonCnt++;
				}
			}
			LogTime.PrtSpMsg(2, "Frequency " + codonFreq.size());
		
		// compute RSCU per codon
			// Xij/ (1/ni (for j=1 to ni do xij)
			// where Xij is the number of occurance for the jth codon of the ith aa encoded by ni syn codons.
			// if families (starting with same letter, and probably using same tRNA
			// vs synonymous codon (i.e. coding for same AA).
			
			Vector <String> fam = Align.getFamilies();
			for (int i=0; i<fam.size(); i++) {
				if (STDOUT) System.out.println("      >>> Fam " + (i+1));
				String [] sym = fam.get(i).split(":");
				
				int total=0, n=0;
				for (String codon : sym) {
					if (codonFreq.containsKey(codon)) total += codonFreq.get(codon);
					n++;
				}
				for (String codon : sym) {
					double dem = (double)total/ (double) n;
					double rscu = (double) codonFreq.get(codon)/dem;
					double freq = ((double) codonFreq.get(codon)/ (double) codonCnt)*1000;
					codonRSCU.put(codon, rscu);
					// freq is good match to http://www.kazusa.or.jp/codon/cgi-bin/showcodon.cgi?species=10090
					if (STDOUT)
						System.out.format("      %s %8d %3.2f %5.2f\n", codon, codonFreq.get(codon), rscu, freq);
				}	
			}
			LogTime.PrtSpMsg(2, "Frequency " + codonFreq.size());
		// get SNPs from trans
			
			Vector <String> info = new Vector <String> ();
			rs = mDB.executeQuery("Select SNPid, TRANSid, codons, AAs from SNPtrans");
			while (rs.next()) {
				String codons = rs.getString(3);
				if (codons!=null && !codons.equals("-"))
				   info.add(rs.getInt(1) + ":" + rs.getInt(2) + ":" + rs.getString(3) + ":" + rs.getString(4));
			}
			if (STDOUT) LogTime.PrtSpMsg(2, "SNPtrans " + info.size());
			
		// for SNPtrans.AA string (e.g. T/S or T), enter RSCU and bichem
			Pattern patCodons =   Pattern.compile("(\\w\\w\\w)/(\\w\\w\\w)"); 
			Pattern patAA =   Pattern.compile("(\\w)/(\\w)"); 
			HashMap <String, String> bioChem = Align.getAABioChem();
			int skip=0, add=0;
			
			for (String snp : info) {
				String [] tok = snp.split(":");
				if (tok.length<4) continue; 
				
				int snpid = Integer.parseInt(tok[0]);
				int transid = Integer.parseInt(tok[1]);
				String codons = tok[2];
				String aa = tok[3];
				
				// process codons for RSCU, base
				Matcher x = patCodons.matcher(codons);
				if (!x.find()) {
					if (skip<3 && STDOUT) System.out.println("    No codons " + snpid + " " + transid + " " + codons);
					skip++;
					continue;
				}
				String c1=x.group(1);
				String c2=x.group(2);
				int base = 0;
				for (int i=0; i<c1.length() && base==0; i++) {
					char c = c1.charAt(i);
					if (Character.isUpperCase(c)) base= (i+1);
				}	
				c1 = c1.toUpperCase();
				c2 = c2.toUpperCase();
				Double d1=0.0, d2=0.0;
				if (!c1.equals("-") && codonRSCU.containsKey(c1)) d1 = codonRSCU.get(c1);	
				if (!c2.equals("-") && codonRSCU.containsKey(c2)) d2 = codonRSCU.get(c2);
				String rscu = String.format("%3.2f:%3.2f", d1, d2);
				
				// process AA for biochem
				String a1="-", a2="-", b1="-", b2="-", bio="-";
				x = patAA.matcher(aa);
				if (x.find()) {
					a1 = x.group(1);
					a2 = x.group(2);
					if (bioChem.containsKey(a1)) b1 = bioChem.get(a1);
					if (bioChem.containsKey(a2)) b2 = bioChem.get(a2);
					bio = b1 + "/" + b2;
				}
				else if (bioChem.containsKey(aa)) bio = bioChem.get(aa);
				
				mDB.executeUpdate("UPDATE SNPtrans SET " +
							" rscu='" + rscu + "', bioChem='" + bio + "'" +
							" where SNPid=" + snpid + " and TRANSid= " + transid);
			}
			LogTime.PrtSpMsg(1, "Add=" + add + " skip=" + skip);
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "compute codons");
		}
	}

	 private TreeMap <String, Double> codonRSCU = new TreeMap <String, Double> ();
	 private DBConn mDB;
}
