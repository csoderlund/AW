package build.file;
/********************************************************
 * Create ref protein and alt protein sequences
 * Compute codon->codon, A->A, and function
 * 
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Vector;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import build.Cfg;
import util.Align;
import util.ErrorReport;
import util.Globals;
import util.LogTime;
import database.DBConn;
import database.MetaData;

public class GenTrans {
	private final String DELIM = VarAnno.DELIM; // ;
	private final String DELIM2 = VarAnno.DELIM2;  // ,
	private final String HIGH = VarAnno.HIGH; // snpEFF

	// outputs in project directory
	private final String outputDir = "AW_output";
	private final String ntCDSFileName = "ntRefCDS.fa"; // TCW nucleotide (coding only)
	private final String ntRefFileName = "ntRef.fa";    // full transcript
	private final String ntAltFileName = "ntAlt.fa";
	private final String aaRefFileName = "aaRef.fa";    // TCW amino acid
	private final String aaAltFileName = "aaAlt.fa";
	private final boolean ISREF=true;
	
	private boolean OFFSET1=true; // change to false if offset is zero; not complete or tested
	private boolean FULLRMK=false; // ambiguous remarks are left off for release
	private boolean hasEffect=false;
	private boolean hasCDSpos=false;
	
	/***********************
	 * testing: compare 
	 * 	refNT with Mus_musculus.GRCm38.72.cdna.all.fa	doNT -- perfect matches for  ALL mus
	 *  refAA with Mus_musculus.GRCm38.72.pep.all.fa		doAA -- no
	 *  		no ATG misses the first, no Stop misses the last, two have no good translations
	 *  altNT with rsem Bc.transcripts.fa				doRsem, doNT	 -- perfect matched for chr10
	 *  		if one is longer than the other, no considered problem because rsem adds AAAAAAAAAAAA
	 */
	private boolean TEST=false; // one chr only, only coding trans, print trace
	private boolean TESTCMP=false, TESTEFFECT=false;  // do cmpSeqs, do effect
	private boolean doRsem=false, doAlt=false, doNt=false, doAA=false; // for cmpSeqs testing2
	private boolean checkReverse=false; // for sequences without a good AA translation; made no difference for mouse
	
	public GenTrans(DBConn db, Cfg cf, String projDir) {
		if (!cf.hasGenomeDir()) {
			LogTime.PrtSpMsg(1, "No genome directory defined in AW.cfg file -- skipping step\n");
			return;
		}
		cfg = cf;
		mDB = db;
	
		Long startTime = LogTime.getTime();
		
		String genomeDir = cfg.getGenomeDir();
		Vector <String> chrFiles = cfg.getGenomeVec();
		Vector <String> chrNums = new MetaData(mDB).getChr();
		LogTime.PrtDateMsg("Create transcripts from  " + genomeDir);	
		if (TEST || TESTCMP || TESTEFFECT) 
			System.out.println("TEST=" + TEST + " CMP=" + TESTCMP + " EFFECT=" + TESTEFFECT);
		
		if (cfg.isEVP() || cfg.isSnpEFF()) {
			hasEffect=true;
			if (cfg.isEVP()) hasCDSpos=true;
			else LogTime.PrtSpMsg(2, "Compute cDNApos (effects loaded from file)");
		}
		else LogTime.PrtSpMsg(2, "Compute cDNApos and effects");
		
		
		if (TESTEFFECT) hasEffect=false;
	
		try {	
			if (!hasEffect) {
				mDB.executeUpdate("UPDATE trans set cntMissense=0, cntDamage=0, gtfRmk='', UTR5=0, UTR3=0," +
				   		"refProLen=0, altProLen=0, nProDiff=0, ntLen=0");
				mDB.executeUpdate("TRUNCATE TABLE sequences");
				mDB.executeUpdate("UPDATE SNPtrans SET " +
					"effect='', CDSpos=0, cDNApos=0, AApos=0, AAs='', codons=''");
				mDB.executeUpdate("UPDATE transExon set remark=''");
				mDB.executeUpdate("UPDATE gene set cntMissense=0");
				mDB.executeUpdate("UPDATE SNP set isDamaging=0, effectList=''");
			}
			else { // snpEFF or EVP were loaded, don't overwrite (depends on order of execution)
				mDB.executeUpdate("UPDATE trans set gtfRmk='',  UTR5=0, UTR3=0," +
				   		"refProLen=0, altProLen=0, nProDiff=0, ntLen=0");
				mDB.executeUpdate("UPDATE transExon set remark=''");
				mDB.executeUpdate("TRUNCATE TABLE sequences");
				if (!hasCDSpos)
					mDB.executeUpdate("UPDATE SNPtrans SET cDNApos=0, CDSpos=0");
			}
			
			String dir = Globals.projDir + "/" + projDir + "/" + outputDir;
			File fdir = new File(dir);
			if (!fdir.exists() || !fdir.isDirectory()) {
				LogTime.PrtSpMsg(1, "Create output directory " + dir);
				try {fdir.mkdir();} catch(Exception e) {ErrorReport.die("Cannot make " + fdir);}
			}
			ntCDSFile = new BufferedWriter(new FileWriter(dir + "/" + ntCDSFileName, false));
			ntRefFile = new BufferedWriter(new FileWriter(dir + "/" + ntRefFileName, false));
			ntAltFile = new BufferedWriter(new FileWriter(dir + "/" + ntAltFileName, false));
			aaRefFile = new BufferedWriter(new FileWriter(dir + "/" + aaRefFileName, false));
			aaAltFile = new BufferedWriter(new FileWriter(dir + "/" + aaAltFileName, false));
			int cnt=0;
			for (String file : chrFiles) {
				String chr = getChr(file, chrNums);
				if (chr==null || (TEST && chr.equals("X"))) continue;
				cnt++;
				System.out.print(cnt + ". Processing chr" + chr + "                   \r");
				
				readDB(chr);
	
				readChrFile(genomeDir + "/" + file);	
				for (Trans tr: transList) tr.shiftToZeroStart();
				
				createAltSeq(); 							
				
				createSpliceTrans(ISREF , ntRefFile); 		
				
				createSpliceTrans(!ISREF, ntAltFile);			
				
				createAASeqs_2DB();
				
				if (!hasEffect || !hasCDSpos || TESTEFFECT) varEffects_2DB(); //createAASeq fixes frame when necessary 
				
				if (TESTCMP) cmpSeqs(dir, chr); 
				if (TEST) break;
				transList = null; 	// restarts for each chromosome
				System.gc();
			}
			if (FULLRMK) {
				LogTime.PrtSpMsg(1, "AA translation Trans:  Change frame: " + cntWrongFrame + 
						" (Wrong start: " + cntWrongStart + ")  No translation: " + cntNoFrame);
				if (checkReverse)LogTime.PrtSpMsg(1," Reversed: " + cntReverse);
			}
			LogTime.PrtSpMsg(1, "No start_codon: " + cntNoATG + " no end_codon: " + cntNoStop);
			LogTime.PrtSpMsg(1,  "Add exon remarks: " + cntAddExonRmk + " Write aaRef: " + cntWriteAA);
			if (!hasEffect) LogTime.PrtSpMsg(1, "Missense: " + cntMissense);
			
			ntRefFile.close(); ntAltFile.close(); ntCDSFile.close();
			aaRefFile.close(); aaAltFile.close();
		}
		catch (Exception e) {ErrorReport.prtError(e, "GenTrans");}
		LogTime.PrtSpMsgTimeMem(0, "End generate sequences", startTime);
	}

	private String getChr(String file, Vector <String> chrNums) {
		String root = file.substring(0, file.indexOf("."));
		Pattern chrPat = Pattern.compile("(\\D+)(\\d+)"); // using \\w+ will match chr1 from chr10
		Matcher m = chrPat.matcher(root);
		String chr="";
		if (m.find()) {
			chr = m.group(2);
			for (String c : chrNums) 
				if (c.equals(chr)) return chr;
		}
		else {
			for (String c : chrNums) 
				if (root.endsWith(c.trim())) return c;
		}
		LogTime.PrtError("Ignore file " + file + " does not end with a chromosome number/letter from the database");
		System.out.println(chr + " " + root);
		return null;
	}
	/*******************************************
	 * Get coords from database 
	 */
	private void readDB(String chr) {
		System.out.print("1 Reading database for coords " + chr +" \r");
		ResultSet rs;
		int cntTr=0, cntExon=0, cntVar=0;
		try {
			
		/** get transcripts for chromosome                        **/
			String where = "where chr='" + chr + "' and start>0 and end>0"; 
			if (TEST) where+= " and (cntCoding+cntIDCoding)>0";
			
			int cntTrans = mDB.executeCount("Select count(*) from trans " + where);
			transList = new Trans [cntTrans];
			
			rs = mDB.executeQuery("Select TRANSid, transIden, strand, " +
					" start, end, startCodon, endCodon, transName" +
					" from trans " + where); 
			
			while (rs.next()) {
				transList[cntTr] = new Trans (rs.getInt(1), rs.getString(2), rs.getString(3), 
						rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8));
				cntTr++;
			}
		/** get exons and variants for each transcript **/	
			for (Trans tr : transList) {
				rs = mDB.executeQuery("Select cStart, cEnd, frame,  nExon from transExon " +
						"where TRANSid=" + tr.transid + " order by cStart"); 
				while (rs.next()) { 
					tr.setExon(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
					cntExon++;
				}	
				tr.adjustCoords();
				tr.findIgnoredExons();
			
				rs = mDB.executeQuery("Select SNPtrans.SNPid, SNPtrans.effect, SNPtrans.nExon, " +
						" SNP.rsID, SNP.pos, SNP.isSNP, SNP.ref, SNP.alt, SNPtrans.isCoding " +
						" from SNPtrans " +
						" join SNP on SNPtrans.SNPid=SNP.SNPid " +
						" where SNPtrans.TRANSid=" + tr.transid + 
						" order by SNP.pos");
				while (rs.next()) {
					tr.setVar(rs.getInt(1), rs.getString(4), rs.getInt(5),
						rs.getBoolean(6), rs.getString(7), rs.getString(8), 
						rs.getString(2), rs.getInt(3), rs.getBoolean(9));
					cntVar++;
				}	
			}	
			Arrays.sort(transList);
			if (TEST) LogTime.PrtSpMsg(3, "Trans: " + cntTr + "   Exons: " + cntExon + "   Variants: " + cntVar);
		}
		catch (Exception e) {ErrorReport.die(e, "GenTrans: readDB"); }
	}
	/*******************************************************
	 * read chromomome file and assign from ATG to STOP the entire transcript (non-spliced)
	 */
	 private void readChrFile(String fileName) {
		System.out.print("2 Read genome chromosome file and create unspliced transcripts\r");
		File f = new File(fileName);
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			StringBuffer buffer= new StringBuffer();
			int cntLine=0, bufOffset=0, tIdx=0;
			int nTrans = transList.length;
			String line = br.readLine(); // remove >chrN
			
			while ((line = br.readLine()) != null) {
				cntLine++;
				line = line.trim();
				buffer.append(line);
				int bufLen = buffer.length();
				int nextTrStart=0;
				
				while (nextTrStart==0 && tIdx<nTrans) {
					Trans tr = transList[tIdx]; 
					
					int trStart = tr.start-bufOffset; 
					int trEnd=    (tr.end-bufOffset); 
					if (trStart<0 || trEnd<0) 
						ErrorReport.die(tr.ensID + " buf=" + bufOffset + " start=" + trStart + " end=" + trEnd);
		
					if (trEnd+1 < buffer.length() && trStart < buffer.length()){
						String seq = buffer.substring(trStart, trEnd+1); 
						tr.setRefSeq(seq.toUpperCase());
						tIdx++;
					}
					else nextTrStart = tr.start;
				}
				if (tIdx==nTrans) break; 
					
				if (nextTrStart > (bufOffset+bufLen)) {
					bufOffset += bufLen;
					buffer.delete(0, buffer.length());
				}	
				if (cntLine %100000 ==0) 
					System.err.print("      line=" + cntLine + " #trans=" + tIdx + " buf=" + buffer.length() + "               \r");
			}
			System.out.print("                                                                 \r");
			if (TEST) LogTime.PrtSpMsg(3, "Lines: " + cntLine + " Trans: " + tIdx + "              ");
		}
		catch (Exception e) {
			ErrorReport.die(e, "GenTrans: readChrFile");}
	}
	 /*********************************************
	  * Create alt sequences by inserting SNPs and Indels and update Alt Exons
	  * Alt exons need to be altered for splicing, i.e. a deletion could shift coords
	  * Variants also need shifting in case the variant annotation computation is needed
	  */
	 private void createAltSeq() {
		System.out.print("3 Create alt transcript seqs                              \r");
		int cntReplace=0, cntIndel=0;
		try {
			for (Trans tr : transList) {
				StringBuilder altSeq = new StringBuilder(tr.refSeq);
				
				// Go through variants replace them in alt seq
				for (int sIdx=0; sIdx<tr.varList.size(); sIdx++) {
					Variant var = tr.varList.get(sIdx);
					int varLen = var.ref.length();
					if (!tr.refSeq.substring(var.refPos, var.refPos + varLen).equals(var.ref)) {	
						ErrorReport.die(cntReplace + " createAltSeq: " + tr.isPos + " " + 
								tr.altSeq.substring(var.refPos, var.refPos + varLen) + " " + var.ref);
					}
					int altPos = var.altPos;
					altSeq.replace(altPos, altPos+varLen, var.alt);
					cntReplace++;
					
					if (var.isSNP) continue;
					
					// shift everything for indel
					cntIndel++;
					int diff = var.alt.length() - var.ref.length();
				
					for (Exon ex : tr.exonList) { 
						 if (altPos<ex.altSta) 
							 ex.updateAltExon(ex.altSta+diff, ex.altEnd+diff);
						 else if (altPos>ex.altSta && altPos<ex.altEnd) 
							 ex.updateAltExon(ex.altSta, ex.altEnd+diff);
						 
						 createAltSeqExonCheck(tr.name, var.ID, var.refPos, diff, ex.altSta, ex.altEnd);
					}
					for (int v=sIdx+1; v < tr.cntVar; v++) {
						Variant v2 = tr.varList.get(v);
						v2.updateAltPos(v2.altPos+diff);
					}
					if (altPos < tr.altStaCodon) tr.updateAltStaCodon(tr.altStaCodon+diff);
					if (altPos < tr.altEndCodon) tr.updateAltEndCodon(tr.altEndCodon+diff);
				}
				tr.setAltSeq(altSeq.toString());
			}
			if (TEST) LogTime.PrtSpMsg(3, "Replacements: " + cntReplace + " Indels: " + cntIndel);
		}
		catch (Exception e) {ErrorReport.die(e, "GenTrans: create Alt sequence"); }
	}
	
	 // XXX NOTE: I haven't handled these cases and want to how ensembl handled
	 // could an exon be lost? probably
	private boolean createAltSeqExonCheck(String name, int varID, int pos, int diff, int start, int end) {
		 boolean rc=true;
		 if (!FULLRMK) return rc;
		 if (pos<start && (pos+diff) > start) { rc=false;
			 LogTime.PrtSpMsg(3,  "Warn: Variant spans exon start=" + start + " variant=" + 
					 varID + " pos=" + pos +" to=" + (pos+diff) + " " + name);}
		 else if (pos<end && (pos+diff) > end) { rc=false;
			 LogTime.PrtSpMsg(3,  "Warn: Variant spans exon end=" + end + " variant=" + 
					 varID + " pos=" + pos +" to=" + (pos+diff)+ " " + name);}
		 else if (pos==start) { rc=false;
			 LogTime.PrtSpMsg(3,  "Warn: Variant on start=" + start + " variant=" + 
					 varID + " pos=" + pos +" to=" + (pos+diff)+ " " + name);}
		 else if (pos==end) { rc=false;
			 LogTime.PrtSpMsg(3,  "Warn: Variant on end=" + end + " variant=" + 
					 varID + " pos=" + pos +" to=" + (pos+diff)+ " " + name);}
		 return rc;
	}
	/********************************************************
	* creates ntAlt and ntRef spliced sequences
	* and writes them to files 
	* - strand is not reverse complemented until after splicing because exon coords are for positive strand
	 */
	private void createSpliceTrans(boolean isRef, BufferedWriter bw) {
		System.out.print("4 Create spliced transcripts and shift Variants isRef=" + isRef + "\r");
		int cntAppend=0;
		try {
			for (int tIdx=0; tIdx<transList.length; tIdx++) {
				Trans tr = transList[tIdx]; 
			
				String seq = (isRef) ? tr.refSeq : tr.altSeq;	
				StringBuffer coding= new StringBuffer();
			
				int eIdx0=-1, offset=0;
				for (int eIdx=0; eIdx<tr.exonList.size(); eIdx++) {
					Exon ex = tr.exonList.get(eIdx);
					if (ex.ignore) continue;
					
					int exSta = (isRef) ? ex.refSta : ex.altSta; 
					int exEnd = (isRef) ? ex.refEnd : ex.altEnd;
					
					if (exEnd < seq.length()) exEnd++; // exEnd is inclusive, need exclusive for substring
					if (exSta>exEnd || exSta<0 || exEnd > seq.length()) {
						LogTime.PrtError("Create Splice trans: " + tr.ensID + " " + exSta + " " + exEnd);
						continue;
					}
					coding.append(seq.substring(exSta, exEnd));
					cntAppend++;
					
					if (eIdx0 > -1) offset += spliceShiftIntron(isRef, tr, eIdx0, eIdx, offset);
					eIdx0=eIdx;
				}
				String cs = coding.toString();
				if (isRef) tr.setRefSplSeq(cs); // for translation
				else tr.setAltSplSeq(cs);
				
				// REVERSE - strand
				String ss = (tr.isPos) ? cs : reverseSeq(cs);
				bw.write(tr.getInfo() + "\n" +  ss  + "\n");
				coding.delete(0, coding.length());			
				if (tIdx % 100 == 0) 
					System.err.print("   spliced " + tIdx + "                                     \r");
			}
			System.out.print("                                                            \r");
			bw.flush();
			if (TEST) LogTime.PrtSpMsg(3, "Spliced: " + cntAppend);
		}
		catch (Exception e) {ErrorReport.die(e, "GenTrans: create spliced transcripts"); }
	}
	/****************************************************************
	 * update variant positions with introns removed
	 * exon coords do not get shifted
	 */
	private int spliceShiftIntron(boolean isRef, Trans tr, int eIdx0, int eIdx, int shift) {
		try {
			Exon e0 = tr.exonList.get(eIdx0);
			Exon e1 = tr.exonList.get(eIdx);
			int eEnd0 = (isRef) ? e0.refEnd : e0.altEnd; 
			int eStart1 = (isRef) ? e1.refSta : e1.altSta;
			int intronLen = (eStart1-eEnd0) -1;
			
			for (Variant var: tr.varList) {
				int posSp = (isRef) ? var.refPos : var.altPos;
				if (posSp+shift > eStart1) {
					posSp -= intronLen;
					
					if (isRef) var.updateRefPos(posSp);
					else var.updateAltPos(posSp);
				}
			}
		
			if (isRef) { // TODO is > correct for end?
				tr.updateRefEnd(tr.refEnd-intronLen);
				if (tr.refStaCodon+shift >= eStart1) tr.updateRefStaCodon(tr.refStaCodon-intronLen);
				if (tr.refEndCodon+shift > eStart1) tr.updateRefEndCodon(tr.refEndCodon-intronLen);
			}
			else {
				tr.updateAltEnd(tr.altEnd-intronLen);
				if (tr.altStaCodon+shift >= eStart1) tr.updateAltStaCodon(tr.altStaCodon - intronLen);
				if (tr.altEndCodon+shift > eStart1) tr.updateAltEndCodon(tr.altEndCodon - intronLen);
			}
			return intronLen;
		}
		catch (Exception e) {ErrorReport.die(e, "GenTrans: shiftAllVarPos"); return 0; }
	}
	
	/*************************************************************
	 * compute AA seqs
	 */
	private void createAASeqs_2DB() {
		System.out.print("5 Compute AA and enter into database\r");
	
		try {
			for (int t=0; t<transList.length; t++) {
				String stopMsg="";
				Trans tr = transList[t];
				int nRef=0, nAlt=0;
				if (FULLRMK) {
					if ((tr.refEndCodon-tr.refStaCodon) % 3 !=0) {
						if (tr.gtfRmk.equals("")) tr.gtfRmk = "!%3";
						else tr.gtfRmk += DELIM2 + " !%3";
					}
				}
				String aaSeq = 
					getTranslated(ISREF, tr, tr.refStaCodon, tr.refEndCodon, tr.refSplSeq).trim();
				cntWriteAA++;
				aaRefFile.write(tr.getInfo() + "\n" + aaSeq + "\n");
				if (aaSeq.contains("*")) stopMsg = "Ref";
				nRef= aaSeq.length();
				
				mDB.executeUpdate("Insert into sequences set parent=0, seq='" + tr.refSplSeq + "'" +
						", TRANSid=" + tr.transid);
				mDB.executeUpdate("Insert into sequences set parent=1, seq='" + aaSeq + "'" +
						", TRANSid=" + tr.transid);
				
				if (tr.cntVar==0) {
					aaAltFile.write(tr.getInfo() + "\n" + aaSeq + "\n");
					nAlt=nRef;
				}
				else { 
					aaSeq = getTranslated(!ISREF, tr, tr.altStaCodon, tr.altEndCodon, tr.altSplSeq);
					aaAltFile.write(tr.getInfo() + "\n" + aaSeq + "\n");
					if (aaSeq.contains("*")) stopMsg += "Alt";
					
					mDB.executeUpdate("Insert into sequences set parent=2, seq='" + aaSeq + "'" +
							", TRANSid=" + tr.transid);
					nAlt = aaSeq.length();
				}
				// add gtfRmk and exon remarks
				if (!stopMsg.equals("")) {
					stopMsg =  "BADAA " + stopMsg;
					tr.gtfRmk = (tr.gtfRmk.equals("")) ? stopMsg : (tr.gtfRmk + DELIM2 + " " + stopMsg);
				}
				int nDiff = Math.abs(nRef-nAlt);
				int UTR5 = tr.refStaCodon;
				int UTR3 = tr.refEnd - tr.refEndCodon +1;
				if (UTR3<0) {
					if (TEST) LogTime.PrtWarn("End problem: " + tr.ensID + " " + tr.strand + 
							" end: " + tr.refEnd + " EndCodon: " + tr.refEndCodon);
					UTR3=0;
					if (tr.gtfRmk.equals("")) tr.gtfRmk="Problem UTR3";
					else tr.gtfRmk += DELIM2 + " Problem UTR3";
				}
				String msg = addRemarkExon(tr.transid, tr.atg, tr.stop, tr.isPos);
				if (!msg.equals("")) {
					if (tr.gtfRmk.equals("")) tr.gtfRmk=msg;
					else tr.gtfRmk += DELIM2 + " " + msg;
				}
				mDB.executeUpdate("UPDATE trans SET UTR5=" + UTR5 + ", UTR3=" + UTR3 + 
						", refProLen=" + nRef + ", altProLen=" + nAlt + ",nProDiff="+nDiff + 
						",ntLen=" + tr.refSplSeq.length() + ",gtfRmk='" + tr.gtfRmk + "'" +
						" where TRANSid=" + tr.transid);
				
				if (t%100 == 0) System.out.print("         aaSeqs #" + t + "                                  \r");
			}
			aaRefFile.flush();
		}
		catch (Exception e) {ErrorReport.die(e, "create AAseqs");}
	}
	
	private String addRemarkExon(int tid, int atg, int stop, boolean isPos) {
		try {
			String transMsg="";
			
			int cntExons = mDB.executeCount("Select count(*) from transExon where TRANSid=" + tid);
			int [] eid = new int [cntExons+1];
			int [] cStart = new int [cntExons+1];
			int [] cEnd = new int [cntExons+1];
			int [] frame = new int [cntExons+1];
			String [] exonMsg = new String [cntExons+1];
			
			ResultSet rs = mDB.executeQuery("Select EXONid, cStart, cEnd, frame from transExon " +
					"where TRANSid=" + tid + " order by cStart");
			int cnt=0, eidAtg=-1, eidStop=-1;
			while (rs.next()) {
				eid[cnt] = rs.getInt(1);
				cStart[cnt] = rs.getInt(2)-1;
				cEnd[cnt] = rs.getInt(3)-1;
				frame[cnt] = rs.getInt(4);
				exonMsg[cnt]="";
				cnt++;
			}
			
			for (int i=0; i<cnt; i++) {
				if (frame[i]==-1) {
					exonMsg[i] = "!CDS";
					continue;
				}
				if (isPos) {
					if (atg==cStart[i]) 		{
						eidAtg=eid[i];
						exonMsg[i] = "ATG at Start";
					}
					if (stop-1==cEnd[i]) 		{
						eidStop=eid[i];
						exonMsg[i] = "Stop after End";
					}
				}
				else  {
					if (atg==cEnd[i]) 	{
						eidAtg=eid[i];
						exonMsg[i] = "ATG at End";
					}
					if (stop+1 == cStart[i]) 	{
						eidStop = eid[i];
						exonMsg[i] = "Stop before Start";
					}
				}
			}
					
			if (atg>0 && eidAtg == -1) {
				transMsg ="Unusual ATG";
				for (int i=0; i<cnt; i++) {
					if (frame[i]!=1 && atg>cStart[i] && atg<cEnd[i]) {
						exonMsg[i] = "Contains ATG";
						break;
					}
				}
			}	
			if (atg>0 && eidAtg == -1) transMsg = "Problem ATG";
			
			if (stop > 0 && eidStop == -1) {
				transMsg = (transMsg.equals("")) ? "Unusual stop" :  transMsg + ", Unusual stop";	
				for (int i=0; i<cnt; i++) {
					if (frame[i]!=1 && stop>cStart[i] && stop<cEnd[i]) {
						exonMsg[i] = "Contains Stop";
						break;
					}
				}
			}
			if (atg>0 && eidAtg == -1)
				transMsg = (transMsg.equals("")) ? "Problem stop" :  transMsg + ", Problem stop";	
			
			for (int i=0; i<cnt; i++)
				if (!exonMsg[i].equals("")) {
					mDB.executeUpdate("Update transExon set remark='" + exonMsg[i] + "'" +
							" where EXONid=" + eid[i] + " and TRANSid=" + tid);
					cntAddExonRmk++;
				}
			return transMsg;
		}
		catch (Exception e) {ErrorReport.prtError(e, "add Exon Codon Remarks");}
		return "";
	}
	/*********************************************
	 * translates sequence to amino acid
	 */
	private int cntWrongStart=0, cntReverse=0, cntNoFrame=0, cntWrongFrame=0;
	private String getTranslated(boolean isRef, Trans tr, int sC, int eC, String seq) {
		try {
			int sCodon=sC, eCodon=eC;
			if (eCodon > seq.length()) {	
				if (TEST) LogTime.PrtWarn(tr.ensID + " endCodon: " + eCodon + " " + seq.length());
				eCodon = seq.length(); // see ENSMUST00000103007
			}
			if (sCodon > eCodon) {
				if (TEST) LogTime.PrtWarn(tr.ensID + " translating startCodon: " + sCodon + " endCodon: " + eCodon + " " + seq.length());
				return "";
			}
			String [] aaSeq = new String [6];
			int endLoop = (checkReverse) ? 4 : 1;
			
			for (int o=0; o<endLoop; o+=3) {
				sCodon=sC; eCodon=eC;
				if (eCodon > seq.length())  eCodon = seq.length();
				boolean isNeg = (o==0) ? tr.isNeg : !tr.isNeg;
				String revMsg = (o==0) ? "" : " Reverse";
				
				// Frame 1 - should be this one (orient 0)
				aaSeq[0+o] = Align.getTranslated( isNeg, seq.substring(sCodon, eCodon));
				if (aaSeq[0+o].endsWith("*")) aaSeq[o+0] = aaSeq[0+o].substring(0, aaSeq[0+o].length()-1);
				
				if (!aaSeq[0+o].contains("*")) {
					if (isRef) {
						String cds= seq.substring(sCodon, eCodon);
						if (tr.isNeg) cds = reverseSeq(cds); // ignore o(orient) as the nt is always right for mus
						ntCDSFile.write(tr.getInfo() + revMsg + "\n" + cds + "\n");
						
						if (o>0) cntReverse++;
					}
					return aaSeq[0+o];
				}
				// Try frame 2
				if (tr.isPos) sCodon++; 
				else eCodon--;
				aaSeq[1+o] = Align.getTranslated(isNeg, seq.substring(sCodon, eCodon));
				if (aaSeq[1+o].endsWith("*")) aaSeq[1+o] = aaSeq[1+o].substring(0, aaSeq[1+o].length()-1);
				
				if (!aaSeq[1+o].contains("*")) {
					if (isRef) {
						String cds= seq.substring(sCodon, eCodon);
						if (tr.isNeg) cds = reverseSeq(cds);
						ntCDSFile.write(tr.getInfo() + revMsg + " Frame 2\n" + cds + "\n");
						
						tr.refStaCodon=sCodon; // so that the effect finder starts in right frame.
						tr.refEndCodon=eCodon;
						if (FULLRMK) {
							if (tr.gtfRmk.equals("")) tr.gtfRmk = revMsg + " Frame 2";
							else tr.gtfRmk += DELIM2 + " " + revMsg + " Frame 2";
						}
						if (o>0) cntReverse++;
						cntWrongFrame++;
						if (tr.atg>0 && tr.stop>0) {
							cntWrongStart++;
							if (TEST) LogTime.PrtWarn("Start frame2: " + tr.getInfo());
						}
					}
					return aaSeq[1+o];
				}
				
				// Frame 3
				if (tr.isPos) sCodon++; 
				else eCodon--;
				aaSeq[2+o] =  Align.getTranslated(isNeg, seq.substring(sCodon, eCodon));
				if (aaSeq[2+o].endsWith("*")) aaSeq[2+o] = aaSeq[2+o].substring(0, aaSeq[2+o].length()-1);
				
				if (!aaSeq[2+o].contains("*")) {
					if (isRef) {
						String cds= seq.substring(sCodon, eCodon);
						if (tr.isNeg) cds = reverseSeq(cds);
						ntCDSFile.write(tr.getInfo() + revMsg + " Frame 3\n" + cds + "\n");
						
						tr.refStaCodon=sCodon; 
						tr.refEndCodon=eCodon;
						if (FULLRMK) {
							if (tr.gtfRmk.equals("")) tr.gtfRmk = revMsg + " Frame 3";
							else tr.gtfRmk += DELIM2 + " " + revMsg + " Frame 3";
						}
						if (o>0) cntReverse++;
						cntWrongFrame++;
					}
					if (tr.atg>0 && tr.stop>0) {
						cntWrongStart++;
						if (TEST) LogTime.PrtWarn("Start frame3: " + tr.getInfo());
					}
					return aaSeq[2+o];
				}
			}
		
			int bestCnt=10000, bestO=0;
			endLoop = (checkReverse) ? 6 : 3;
			for (int o=0; o<endLoop; o++) {
				int cnt=0;
				for (int i=0; i<aaSeq[o].length(); i++) 
					if (aaSeq[o].charAt(i)=='*') cnt++;
				if (cnt<bestCnt) {
					bestCnt=cnt; 
					bestO=o;
				}
			}
			if (isRef) {
				String revMsg = (bestO>2) ? " Reverse" : "";
				int end = (eCodon> seq.length()) ? seq.length() : eCodon;
				ntCDSFile.write(tr.getInfo() + revMsg + " No frame\n" 
							+ seq.substring(sC, end) + "\n");
				if (tr.gtfRmk.equals("")) tr.gtfRmk = revMsg + " No frame";
				else tr.gtfRmk += DELIM2 + " " + revMsg + " No frame";
				cntNoFrame++;
			}
			return aaSeq[bestO];
		}
		catch (Exception e) {ErrorReport.prtError(e, "Writing amino acid file"); return "";}
	}
	 public String reverseSeq(String refSeq) {
			StringBuffer comp = new StringBuffer (refSeq.length()); 
			char [] seq = refSeq.toCharArray();
			for (int i = refSeq.length() - 1; i >= 0; --i) {
				char nt='n';
				if (seq[i]=='A') nt='T';
				else if (seq[i]=='T') nt='A';
				else if (seq[i]=='C') nt='G';
				else if (seq[i]=='G') nt='C';
				
				else if (seq[i]=='Y') nt='R';
				else if (seq[i]=='R') nt='Y';
				else if (seq[i]=='K') nt='M';
				else if (seq[i]=='M') nt='K';
				else if (seq[i]=='B') nt='V';
				else if (seq[i]=='V') nt='B';
				else if (seq[i]=='D') nt='H';
				else if (seq[i]=='H') nt='D';
				
				comp.append(nt);
			}
			return comp.toString();
		 }
	/********************* XXX Compute Variant Annotation if no annotation exists *********************/
	/** Based on snpEFF effects/functions **/
	private final String [] VARTYPES = {"DONT KNOW", 
			 "upstream_gene_variant", "5_prime_utr_variant", 
			 "intron_variant", 
			 "synonymous_variant", "start_retained", "stop_retained_variant",
			 "missense_variant",  "start_codon_gain_variant",  
			 "start_lost", "stop_gained", "stop_lost",
			 "3_prime_utr_variant", "downstream",
			 "frameshift_variant", 
			 "inframe_deletion", "inframe_insertion"
	};
	private final int EMPTY=0, 
			UP=1, UTR5=2, 
			INTRON=3, 
			SYN=4, SYNSTART=5, SYNSTOP=6, 
			MIS=7, ATGGAIN=8, ATGLOST=9, STOPGAIN=10, STOPLOST=11, 
			UTR3=12, DOWN=13, 
			FRA=14,  DEL=15, INS=16;
	
	/*************************************************************
	 * determine Var codons, AA and missense
	 * whether a variant is in coding is marked in Variants.java based on
	 * whether they fall into an exon.
	 */
	private void varEffects_2DB() {
		System.out.print("6 Annotate variants and enter into database\r");
		
		try {	
			int cntSNP=0, cntOther=0, cntIndel=0;
			HashMap<Integer,String> snpAnno = new HashMap<Integer,String>();
			
			for (Trans tr : transList) {
				int TRANSid = tr.transid;
				String seq = tr.refSplSeq;
				
				for (Variant vr : tr.varList) {
					int SNPid = vr.ID;
					int relPos = vr.refPos; 
					String effect;
					
					if (!vr.isCoding) {
						effect = varEffectNonCode(vr, tr, TRANSid,  seq, SNPid, relPos);
						cntOther++;
					}
					else if (!vr.isSNP) {
						effect = varEffectIndel(vr, tr, TRANSid,  seq, SNPid, relPos);
						cntIndel++;
					}
					else {
						effect = varEffectSNP(vr, tr, TRANSid, seq, SNPid, relPos);
						cntSNP++;
					}
					if (effect != null) {
						if (!snpAnno.containsKey(SNPid))
							snpAnno.put(SNPid, effect);
						else {
							String e = snpAnno.get(SNPid);
							if (!e.contains(effect))
								snpAnno.put(SNPid, e + DELIM + effect);
						}
					}
				} 
			} 
			if (TEST) LogTime.PrtSpMsg(3, "SNP=" + cntSNP + " Indel=" + cntIndel + " Non-coding=" + cntOther);
			if (TESTCMP) return;
			
			System.out.print("Update mySQL Variant tables                        \r");
			PreparedStatement ps = mDB.prepareStatement("update SNP set effectList=? where snpid=?");
			int batch2 = 0;
			for (int snpid : snpAnno.keySet())
			{
				ps.setInt(2, snpid);
				ps.setString(1,snpAnno.get(snpid));
				ps.addBatch();
				batch2++;
				if (batch2 % 1000 == 0) {
					ps.executeBatch(); 
					System.err.print("   Finalize variants " + batch2 + "                   \r");
				}
			}
			if (batch2 > 0) ps.executeBatch();
		}
		catch (Exception e) {ErrorReport.die(e, "GenTrans: varAnnoToDB"); }
	}
	// noncoding
	 public String varEffectNonCode(Variant vr, Trans tr, int TRANSid,  String seq, int SNPid, int pos) {
		 try {
			 String effect=null;
			int cdnaPos = pos+1;
			if (tr.isNeg) cdnaPos= seq.length()-pos;
			 
			 // the first two will not happen until up/down stream variants are included
			 if (tr.isPos) {
				      if (pos < 0)  effect = VARTYPES[UP];
				 else if (pos > tr.end)  effect = VARTYPES[DOWN];
				 else if (pos < tr.refStaCodon) {
					 effect = VARTYPES[UTR5];	
				 }
				 else if (pos > tr.refEndCodon) effect = VARTYPES[UTR3];	
				 else effect = VARTYPES[INTRON]; 
			 }
			 else {
			      if (pos > tr.end)  effect = VARTYPES[UP];
			      else if (pos < 0)  effect = VARTYPES[DOWN];
			      else if (pos > tr.refEndCodon) effect = VARTYPES[UTR5];	
			      else if (pos < tr.refStaCodon) effect = VARTYPES[UTR3];	
			      else effect = VARTYPES[INTRON];
			 }
			 if (hasEffect) 
				mDB.executeUpdate("UPDATE SNPtrans SET" +
					" cDNApos=" + cdnaPos + 
					" where TRANSid=" + TRANSid + " and SNPid=" + SNPid); 
			 mDB.executeUpdate("UPDATE SNPtrans SET" +
					" cDNApos=" + cdnaPos + ", effect='" + effect +"'" +
					" where TRANSid=" + TRANSid + " and SNPid=" + SNPid); 
			 return effect;
		 }
		catch (Exception e) {ErrorReport.prtError(e, "NonCoding variant"); return null;}
	}
	// varAnnoIndel
	private String varEffectIndel(Variant vr, Trans tr, int TRANSid,  String seq, int SNPid, int pos) {
		try {	
			String codons = vr.ref + "/" + vr.alt;
			if (tr.isNeg) codons = reverseSeq(vr.ref) + "/" + reverseSeq(vr.alt);
			String effect="", aas="";
			int aapos=0;
			int rlen = vr.ref.length()-1;
			int alen = vr.alt.length()-1;
			if (rlen%3==0 && alen%3==0) {
				if (rlen<alen) effect=VARTYPES[INS];
				else effect=VARTYPES[DEL];
			}
			else effect = VARTYPES[FRA]+DELIM2 + HIGH;
			effect=""; // TODO its not quite right yet
			
			int cdsPos = pos-tr.refStaCodon;
			int cdnaPos = pos+1;
			if (tr.isNeg) {
				cdsPos = tr.refEndCodon-pos-1;
				cdnaPos= seq.length()-pos;
			}
			
			if (hasEffect) 
				mDB.executeUpdate("UPDATE SNPtrans SET" +
					" cDNApos=" + cdnaPos +", CDSpos=" + cdsPos  + 
					" where TRANSid=" + TRANSid + " and SNPid=" + SNPid); 
			else
				mDB.executeUpdate("UPDATE SNPtrans SET" +
					" cDNApos=" + cdnaPos +", CDSpos=" + cdsPos  +  ", AApos=" + aapos + 
					", codons='" + codons + "', AAs='" + aas + "'" + 
					", effect='" + effect +"'" +
					" where TRANSid=" + TRANSid + " and SNPid=" + SNPid); 
			return effect;
		}
		catch (Exception e) {ErrorReport.die(e, "GenTrans: varAnnoIndel"); return null; }
	}
	// varAnnoSNP -- assign missense/synonymous, codons and AA

	private String varEffectSNP(Variant vr, Trans tr, int TRANSid, String seq, int SNPid, int pos) {
		try {	
			if (tr.refEndCodon > seq.length()) tr.refEndCodon=seq.length();
			if (pos>tr.refEndCodon) return "";
			
			String  cds = seq.substring(tr.refStaCodon, tr.refEndCodon);
			int cdsPos = pos-tr.refStaCodon;
			int cdnaPos = pos+1;
			if (tr.isNeg) {
				cds = reverseSeq(cds);
				cdsPos = tr.refEndCodon-pos-1;
				cdnaPos= seq.length()-pos;
			}
			String refCodon="", altCodon="";
			String altSNP = vr.alt; 
			if (tr.isPos) altSNP = altSNP.toUpperCase();
			else altSNP = reverseSeq(altSNP).toUpperCase();
			int base = (cdsPos%3)+1;	
			
			if (!varEffectSNPCheck(tr.isPos, base, cdsPos, cds.length())) {
				if (TEST) {
					LogTime.PrtWarn("Effect Check: " + tr.ensID + " Base " + base + " cdsPos: " + cdsPos + "      " + vr.getInfo());
					cntErr++;
					if (cntErr>100)ErrorReport.die("Too many errors, passing SNPs: " + cntGood);
				}
				return null;
			}
			cntGood++;
			String [] x;	
			
			// CAS 3/1/18 Java v9 results in x have length 3, where pre-9 was length 4
			if (base==1)  { 
				x = cds.substring(cdsPos, cdsPos+3).split("");
				int off = (x.length==3) ? 0 : 1;
				refCodon = x[off].toUpperCase() + x[off+1].toLowerCase() + x[off+2].toLowerCase();
				altCodon = altSNP + x[off+1].toLowerCase() + x[off+2].toLowerCase();
			}
			else if (base==2)  {
				x = cds.substring(cdsPos-1, cdsPos+2).split("");
				int off = (x.length==3) ? 0 : 1;
				refCodon = x[off].toLowerCase() + x[off+1].toUpperCase() + x[off+2].toLowerCase();
				altCodon = x[off].toLowerCase() + altSNP + x[off+2].toLowerCase();
			}
			else if (base==3)  {
				x = cds.substring(cdsPos-2, cdsPos+1).split("");
				int off = (x.length==3) ? 0 : 1;
				refCodon = x[off].toLowerCase() + x[off+1].toLowerCase() + x[off+2].toUpperCase();
				altCodon = x[off].toLowerCase() + x[off+1].toLowerCase() + altSNP;
			}	
			String codons = refCodon + "/" + altCodon;
			char refAA = Align.getAAforCodon(refCodon);
			char altAA = Align.getAAforCodon(altCodon);
			String aas = refAA + "";
			
			String effect="";
			if (refAA != altAA) {
				cntMissense++;
				effect = VARTYPES[MIS];
				aas = refAA + "/" + altAA;
				if (cdsPos <= 3) {
					if (refCodon.toLowerCase().equals("atg")) effect=  VARTYPES[ATGLOST]+ DELIM2 +HIGH;
					else if (altCodon.toLowerCase().equals("atg")) effect=VARTYPES[ATGGAIN]+ DELIM2+HIGH;
				}
				else if (refAA=='*') effect = VARTYPES[STOPLOST]+ DELIM2 + HIGH;
				else if (altAA=='*') effect = VARTYPES[STOPGAIN]+ DELIM2 + HIGH;
			}
			else {
				effect = VARTYPES[SYN];
				// TODO SYNSTOP & SYNSTART - not big deal though
			}
			cdsPos++; // genome sequence starts at 1, put 1 back on
			double p = (double) cdsPos;
			int aapos = (int) Math.round(p/3.0);
			
			if (hasEffect)
				mDB.executeUpdate("UPDATE SNPtrans SET" +
					" cDNApos=" + cdnaPos +", CDSpos=" + cdsPos  + 
					" where TRANSid=" + TRANSid + " and SNPid=" + SNPid); 
			else 
				mDB.executeUpdate("UPDATE SNPtrans SET" +
					" cDNApos=" + cdnaPos + ", CDSpos=" + cdsPos + ", AApos=" + aapos + 
					", codons='" + codons + "', AAs='" + aas + "'" + 
					", effect='" + effect +  "'" +
					" where TRANSid=" + TRANSid + " and SNPid=" + SNPid); 
			return effect;
		}
		catch (Exception e) {
			ErrorReport.prtError(e, "GenTrans: varAnnoSNP " + tr.getInfo()); return null;
		}
	}
	// varAnnoSNPCheck
	private boolean varEffectSNPCheck(boolean isPos, int base, int relPos, int seqLen) {
		
		if (base==1)  {
			if (relPos+3 >= seqLen || relPos<0) return false;
		}
		else if (base==2)  {
			if (relPos+2 >= seqLen || relPos-1<0) return false;
		}	
		else if (base==3)  {
			if (relPos+1 >= seqLen  || relPos-2<0) return false;	
		}	
		else return false;
			
		return true;
	}
	/**************************** end compute new annotation *******************************/
	/********************************************************
	 * compare with sequences from ensembl -- for testing
	 */
	private void cmpSeqs(String dir, String chr) {
		try {	
			String ntRsem = "./data/Bc.transcripts.fa";
			String ntEns = "./data/Mus_musculus.GRCm38.72.cdna.all.fa";
			String aaEns = "./data/Mus_musculus.GRCm38.72.pep.all.fa";
			String ntDiff = "./data/ntDiff";
			String aaDiff = "./data/aaDiff";
			Pattern pepPatID =   Pattern.compile("transcript:(\\w+)"); 
			
			HashMap <String, Trans> transMap = new HashMap <String, Trans> ();
			for (int t=0; t<transList.length; t++)  transMap.put(transList[t].ensID, transList[t]);
			System.out.println("Transcripts: " + transMap.size());
			
			for (int i=0; i<2; i++) {
				if (!doNt && i==0) continue;
				if (!doAA && i==1) break;
			
				int cntfound=0, cntAlignP=0, cntAlignN=0, cntAll=0, cntMatchN=0, skip=0, cntOne=0;
				int cntNo=0;
				String line, ensSeq="", name="";
				BufferedReader in;
				BufferedWriter out;
				if (i==0) {
					System.out.println("Compare nt");
					if (doRsem)in = new BufferedReader(new FileReader(ntRsem));
					else in = new BufferedReader(new FileReader(ntEns));
					out = new BufferedWriter(new FileWriter(ntDiff+ "." + chr, false));
				}
				else {
					System.out.println("Compare aa");
					in = new BufferedReader(new FileReader(aaEns));
					out = new BufferedWriter(new FileWriter(aaDiff+ "." + chr, false));
				}
				
				while ((line = in.readLine()) != null) {
					if (!line.startsWith(">")) {
						ensSeq += line;
						continue;
					}
					cntAll++;
					if (transMap.containsKey(name)) { 
						cntfound++;
						Trans tr = transMap.get(name);
						
						String genSeq;
						if (i==0) {
							genSeq = tr.refSplSeq; 
							if (doRsem || doAlt) genSeq = tr.altSplSeq;
							if (tr.isNeg) genSeq = reverseSeq(genSeq);
						}
						else // getTranslated reverses sequences before translation
							genSeq = getTranslated(ISREF, tr, tr.refStaCodon, tr.refEndCodon, tr.refSplSeq);
			
						String content="";
						int cnt=0;
						if (!genSeq.equals(ensSeq)) {
							Align alignObj = new Align();
							if (doRsem) content = alignObj.getAlignNoEnd(ensSeq, genSeq) + "\n"; 
							else content = alignObj.getAlign(ensSeq, genSeq) + "\n";
							cnt = alignObj.getTransCnt();
							
							             // for Alt, just want to just see indels alignments 
							if (cnt>1) { //|| (doAlt && cnt>0 &&  cnt!=tr.cntVar)) { 
								if (tr.isPos) {
									cntAlignP++; 
									out.write("POS " + tr.getInfo() + " mismatch: " + cnt + "\n" + content + "\n");
								}
								else {
									cntAlignN++; 
									out.write("NEG " + tr.getInfo() + " mismatch: " + cnt + "\n" + content + "\n");
								}
							}
							else {
								if (cnt==1) {
									cntOne++;
									if (tr.gtfRmk.contains("No")) cntNo++;
								}
								else if (!tr.isPos) cntMatchN++;
							}
						} else skip++;
					}
					
					if (i==0) {
						name = line.substring(1, line.length());
						if (!doRsem) name = name.substring(0, name.indexOf(" "));
					}
					else {
						Matcher  x = pepPatID.matcher(line);
						if (x.find()) name = x.group(1);
						else name = line.substring(1);
					}
					ensSeq = "";
					if (cntAll%20 == 0) 
						System.out.print("Found " + cntfound + " skip " + skip + 
					" align+ " + cntAlignP  + " align- " + cntAlignN  + "\r");				
				}
				System.out.print("Found " + cntfound + " perfect match " + skip + 
						" one off " + cntOne + "("+ cntNo + ")" +
						" bad+ " + cntAlignP  + " bad- " + cntAlignN  +  "\n\n");		
				out.close(); in.close();
			}
		}
		catch (Exception e) {ErrorReport.prtError(e, "codon");}
	}
	/**********************************************************
	 * XXX class for transcript and its exons
	 */
	 private class Trans implements Comparable<Trans>{
		 
		 public Trans(int i, String eid, String sd, int s, int e, int a, int st, String n) {
			 transid = i;
			 ensID = eid;
			 name = n;
			 isPos =  (sd.equals("+")) ? true : false;
			 isNeg = !isPos;
			 strand = sd;
			 start = s;
			 end = e;
			 atg=refStaCodon = a;
			 stop=refEndCodon = st;
			 if (OFFSET1) {start--; end--; refStaCodon--; refEndCodon--;atg--;stop--;}
			 gtfRmk = "";
		 }
		 public void setExon(int s, int e, int frame, int num) {
			 exonList.add(new Exon (s, e, frame, num));
		 }	
		
		 public void setVar(int id, String rs, int p, boolean isS, String ref, String alt, 
				 String fun, int nExon, boolean isCoding) {
			 varList.add(new Variant(id, rs, p, isS, ref, alt,  fun, nExon, isCoding));
			 cntVar++;
			 if (!isS) cntIndel++;
		 }
		 public int compareTo(Trans tr) {
			 if (start < tr.start) return -1;
			 else return 1;
		 }	
		 public void setRefSeq(String s) {refSeq = s;}
		 public void setAltSeq(String s) {altSeq = s;}
		 public void setRefSplSeq(String s) {refSplSeq = s;}
		 public void setAltSplSeq(String s) {altSplSeq = s;}
		 
		 public void updateRefEnd(int i) {refEnd = i;}
		 public void updateRefStaCodon(int i) {refStaCodon = i;}
		 public void updateRefEndCodon(int i) {refEndCodon = i;}
		 public void updateAltEnd(int i) {altEnd = i;}
		 public void updateAltStaCodon(int i) {altStaCodon = i;}
		 public void updateAltEndCodon(int i) {altEndCodon = i;}
		 
		 // executed after DB transcript coords are read, before reading genome and shifting to start=0
		 public void adjustCoords() { 
			 if (refStaCodon <=0) { //XXX
				 refStaCodon=start; 
				 gtfRmk = "No ATG";
				 cntNoATG++;
			 }
			 if (refEndCodon <=0) {
				 refEndCodon=end; 
				 gtfRmk = (gtfRmk.equals("")) ? "No Stop" :  (gtfRmk + DELIM2 + " No Stop");
				 cntNoStop++;
			 }
		
			 if (start>end) { // coordinates relative to forward strand
				 int t = start;
				 start = end;
				 end = t;
				 
				 t = refStaCodon;
				 refStaCodon = refEndCodon+1;
				 refEndCodon = t+1;
			 }
			 altStaCodon=refStaCodon;
			 altEndCodon=refEndCodon;
			 refEnd=altEnd=end;
		 }
		 // executed after the above adjustments and after reading the genome sequence,
		 // but allows all coords to be relative to the transcribed sequence
		 public void shiftToZeroStart() {
			 if (refEnd-start<0) 
				 LogTime.PrtError(ensID + " Problem with coordinates start: " + start + " ref end: " + refEnd);
			 if (refStaCodon-start<0) 
				 LogTime.PrtError(ensID + " Problem with coordinates start: " + start + " ref Start Codon: " + refStaCodon);
			 if (refEndCodon-start<0)
				 LogTime.PrtError(ensID + " Problem with coordinates start: " + start + " ref End Codon: " + refEndCodon);
			 
			 updateRefEnd(refEnd-start);
			 updateRefStaCodon(refStaCodon-start);
			 updateRefEndCodon(refEndCodon-start);
			 
			 updateAltEnd(altEnd-start);		
			 updateAltStaCodon(altStaCodon-start);		
			 updateAltEndCodon(altEndCodon-start);
			
			 for (Exon ex: exonList) {
				 if (ex.refSta-start<0 || ex.refEnd-start<0)
					 LogTime.PrtError(ensID + " Problem with coordinates start: " + start + 
							 " exon start: " + ex.refSta + " exon end: " + ex.refEnd);
				 ex.updateRefExon(ex.refSta-start, ex.refEnd-start);
				 ex.updateAltExon(ex.altSta-start, ex.altEnd-start);
			 }
			
			 for (Variant var: varList) {
				 if (var.refPos-start<0)
					 LogTime.PrtError(ensID + " Problem with coordinates start: " + start + 
							 " variant: " + var.refPos);
				 var.updateRefPos(var.refPos-start);
				 var.updateAltPos(var.altPos-start);
			 }
		 }
		 /*******************************************
		  * Generally:
		  * The exon with the start codon is contained within the previous exon with the UTR5
		  * The exon with the end codon is contained within the next exon with the UTR3
		  */
		 public void findIgnoredExons() {
			 for (int e=1; e<exonList.size(); e++) {
				 Exon ex0 = exonList.get(e-1);
				 Exon ex1 = exonList.get(e);
				
				 if (ex1.refSta >= ex0.refSta && ex1.refEnd <= ex0.refEnd) {
					 ex1.ignore=true;
					 if (ex0.ignore) System.out.println(ensID + " ignoreExon1 " + ex1.refSta + " " +
							 ex1.refEnd + " " +ex0.refSta +" " + ex0.refEnd);
				 }
				 else if (ex0.refSta >= ex1.refSta && ex0.refEnd <= ex1.refEnd) {
					 ex0.ignore=true;
					 if (ex1.ignore) System.out.println(ensID + " ignoreExon2 " + ex1.refSta + " " +
							 ex1.refEnd + " " +ex0.refSta +" " + ex0.refEnd);
				 } 
			 }
			 for (Exon ex : exonList) if (ex.frame!=-1) cntExon++;
		 }
		 private String ensID="",  gtfRmk="", strand="", name="";
		 private String refSeq="", altSeq="", refSplSeq="", altSplSeq="";
		 private int transid=-1;
		 private int start=-1,  end=-1, atg=-1, stop=-1; // these stay the original values
		 private int altEnd=-1, altStaCodon=-1, altEndCodon=-1, refEnd=-1, refStaCodon=-1, refEndCodon=-1; 
		 private boolean isPos, isNeg;
		 
		 private int cntExon=0, cntVar=0, cntIndel=0;
		 private ArrayList <Exon> exonList = new ArrayList <Exon> ();
		 private ArrayList <Variant> varList = new ArrayList <Variant> ();	 
		 
		 // this gets written to the sequence files as the header line for each sequence
		 public String getInfo() {
			 int utr5=0, utr3=0;
			 if (isPos) { utr5 = refStaCodon; utr3 = refSplSeq.length()-refEndCodon; }
			 else {       utr3 = refStaCodon; utr5 = refSplSeq.length()-refEndCodon; }
			 
			 String aaSeq="", x="";
			 if (!refSplSeq.equals("") && refStaCodon<refEndCodon && refEndCodon<=refSplSeq.length()) {
				 aaSeq = refSplSeq.substring(refStaCodon, refEndCodon);
				 if (aaSeq.length()%3!=0) x="*"; 
			 }
			 else { 
				 x="****"; // a few weird coordiantes. Trans is remarked in createAAseq
			 } 
			 String id = (ensID.equals(name)) ? "" : " " + ensID + " ";

			 return ">" + name  + id + " transcript=" + refSplSeq.length() +
				" 5UTR=" + utr5 + " 3UTR=" + utr3 + " coding: " 
				+ aaSeq.length() + x +  " " + strand + " " + gtfRmk + " " +
				" #Exons=" + cntExon + "/" + exonList.size() + 
				" #Variants=" + cntVar + " #Indel=" + cntIndel;
		}
	 }
	 /// Exon
	 private class Exon {
		public Exon (int s, int e, int f, int c) {
			refSta = altSta = s;
			refEnd= altEnd = e;
			if (OFFSET1) {refSta--; refEnd--; altSta--; altEnd--;}
			frame = f;
		}
		public void updateRefExon(int s, int e) {
			refSta = s;
			refEnd = e;
		}
		public void updateAltExon(int s, int e) {
			altSta = s;
			altEnd = e;
		}
		int refSta, refEnd, altSta, altEnd, frame;
		boolean ignore=false;
	}
	 /// Variant
	 private class Variant {
		 public Variant(int id, String rs, int p, boolean isS, String r, String a, String f, int n, boolean is) {
			 ID=id;
			 rsID=rs;
			 pos= refPos = altPos = p;
			 if (OFFSET1) {refPos--; altPos--;}
			 isSNP=isS;
			 ref = r;
			 alt = a;
			 fun = f;
			 nExon=n;
			 isCoding = is;
		 }
		 public void updateRefPos(int p) {refPos = p;}
		 public void updateAltPos(int p) {altPos=p;}
		 public String getInfo() {
			 return rsID + " pos: " + pos + " transPos:" + refPos + " isSNP:" + isSNP + " nExon:" + nExon;
		 }
		 private boolean isSNP, isCoding;
		 private String ref, alt, rsID, fun;
		 
		 private int ID,  type, nExon;
		 private int pos, refPos,  altPos; 
	 }
	
	 private Trans [] transList;
	 private DBConn mDB;	
	 private Cfg cfg;
	 private BufferedWriter ntRefFile=null, ntAltFile=null, ntCDSFile=null;
	 private BufferedWriter aaRefFile=null, aaAltFile=null;
	 private int cntNoATG=0, cntNoStop=0;
	private int cntErr=0, cntGood=0, cntMissense=0, cntAddExonRmk=0, cntWriteAA=0;
}
