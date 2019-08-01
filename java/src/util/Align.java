package util;

// modified for exact AA sequences
// practically anything that has to do with nucleotide and amino acid conversions
// is in this file, even if it has nothing to do with alignment

import java.util.HashMap;
import java.util.Vector;

public class Align {
	static char gap = '-';
	static int lineLen=100;
	
	public int getDiff(String strHorz, String strVert) {		
		if (!DPalign (strHorz, strVert, false)) {
			System.out.println("Lengths: " + strHorz.length() + " " + strVert.length());
			LogTime.PrtError("Out of memory");
		}
		buildOutput(gap);
		
		// strGapHorz and strGapVert has gap characters, so are same length		
		char [] s1 = strGapHorz.toCharArray();
		char [] s2 = strGapVert.toCharArray();
			
		int cnt=0;
		for (int i=0; i<s1.length; i++) {
			if (s1[i] != s2[i]) cnt++;
		}
		return cnt;
	}
	/*************************************************
	 * Creates an alignment but ignore gaps on end - for testing GenTrans
	 */
	int genTransCnt=0;
	public int getTransCnt() { return genTransCnt;}
	
	public String getAlignNoEnd(String strHorz, String strVert) {	
		genTransCnt=0;
		if (!DPalign (strHorz, strVert, false)) {
			System.out.println("Lengths: " + strHorz.length() + " " + strVert.length());
			LogTime.PrtError("Out of memory");
		}
		buildOutput(gap);
		
		int starta=-1, enda=-1;
		char [] s1a = strGapVert.toCharArray();
		for (int i=0; i<s1a.length && starta==-1; i++) {
			if (s1a[i]!=gap) starta=i;
		}
		for (int i=s1a.length-1; i>0 && enda==-1; i--) {
			if (s1a[i]!=gap) enda=i;
		}
		
		String align="";
		String horz = strGapHorz.substring(starta, enda);
		String vert = strGapVert.substring(starta, enda);
		char [] s1 = horz.toCharArray();
		char [] s2 = vert.toCharArray();
		int i=0, wc=0, inc=lineLen;
		while (i<horz.length()) {
			int start=i;
			int end = Math.min(i+inc, horz.length());
			align += "Ref " + horz.substring(start, end) + "\n";
			align += "Alt " + vert.substring(start, end) + "\n" + 
			         "--> ";
			for (; i<end; i++) {
				String x= " ";
				if (s1[i]==s2[i]) 						x= " ";
				else if (s1[i]==gap || s2[i]==gap) 		x= "-";
				else if (isCommonAcidSub(s1[i],s2[i])) 	x= "+";
				else 									x= "x";
				
				align += x;
				if (!x.equals(" ")) genTransCnt++;
				wc++;
			}
			align += String.format("   %d", wc);
			align += "\n";
		}
		return align;
	}
	/*************************************************
	 * Creates an alignment for output from the interface
	 */
	public String getAlign(String strHorz, String strVert) {	
		if (!DPalign (strHorz, strVert, false)) {
			System.out.println("Lengths: " + strHorz.length() + " " + strVert.length());
			LogTime.PrtError("Out of memory");
		}
		buildOutput(gap);
		
		genTransCnt=0;
		String align=">Ref " + strHorz.length() + " Alt=" + strVert.length() + "\n";
		char [] s1 = strGapHorz.toCharArray();
		char [] s2 = strGapVert.toCharArray();
		int i=0, inc=lineLen, wc=0;
		while (i<strGapHorz.length()) {
			int start=i;
			int end = Math.min(i+inc, strGapHorz.length());
			align += "Ref " + strGapHorz.substring(start, end) + "\n";
			align += "Alt " + strGapVert.substring(start, end) + "\n" + 
			         "--> ";
			for (; i<end; i++) {
				String x=" ";
				if (s1[i]==s2[i]) 						x= " ";
				else if (s1[i]==gap || s2[i]==gap) 		x="-";
				else if (isCommonAcidSub(s1[i],s2[i])) 	x= "+";
				else 									x = "x";
				if (!x.equals(" ")) genTransCnt++;
				align += x;
				wc++;
			}
			align += String.format("   %d", wc);
			align += "\n";
		}
		return align;
	}
	/**************************************
	 * If the strGapHorz has leading or trailing '-', 
	 * then strGapVert is longer on the respective end, which is chopped off
	 */
	public String getNonGapSeq2() {
		char [] s1 = strGapHorz.toCharArray();
		
		int start=-1, end=-1;
		for (int i=0; i<s1.length && start==-1; i++) {
			if (s1[i]!=gap) start=i;
		}
		for (int i=s1.length-1; i>0 && end==-1; i--) {
			if (s1[i]!=gap) end=i;
		}
		if (end==-1) end=s1.length-1;
		// strGapHorz and strGapVert are same length
		String chop = strGapVert.substring(start, end+1);
		return chop.replace("_", "");
	}
	public String getSeqGap1() {return strGapHorz;}
	public String getSeqGap2() {return strGapVert;}
	
	private void buildOutput ( char chGap )
	{       
		if ( lastGap != null && lastGap.charValue() == chGap )
			return; // called for HorzResult and VertResult
		
		// Build strGapHorz and strGapVert which have inserted gaps
		if ( bUseAffineGap )	buildAffineOutput ( chGap );
		else					buildNonAffineOutput ( chGap );   
	}
	
	/**
	* Routines for affine and non-affine dynamic programming
	*/
	
	public boolean DPalign ( String strHorz, String strVert, boolean b)
	{
		bUseAffineGap = b;
		if (bUseAffineGap) 	return matchAffine( strHorz, strVert );
		else 				return matchNonAffine( strHorz, strVert );
	}
	
	private boolean matchNonAffine (String strHorz, String strVert )
	{
		nRows = strVert.length() + 1;
		nCols = strHorz.length() + 1;
		nCells = nCols * nRows;
		lastGap = null;
		strInHorz = strHorz;
		strInVert = strVert;
		
		if (!checkAllocation ( )) return false;
		
		// Initialize top row
		for ( int i = 1; i < nCols; ++i )
		{
			if ( bFreeEndGaps ) matchRow[i] = 0;
			else matchRow[i] = - ( gapOpen * i );                    
			matchDir[i] = DIRECTION_LEFT;
		}
		
		// Initalize left column for direction arrays
		for ( int k = 1, i = nCols; k < nRows; ++k, i += nCols )
		{
			matchDir[i] = DIRECTION_UP;
		}
		
		matchRow[0] = 0;
		
		// Fill in all matricies simultaneously, row-by-row
		for ( int v = 1; v < nRows; ++v )
		{
			int i = ( v * nCols ) + 1;
			
			// Only saves two rows, the last one and current one.
			float [] temp = matchLastRow;
			matchLastRow = matchRow;
			matchRow = temp;  
			temp = gapHorzLastRow;
			gapHorzLastRow = gapHorzRow;
			gapHorzRow = temp;
			temp = gapVertLastRow;
			gapVertLastRow = gapVertRow;
			gapVertRow = temp;
			
			// Initialize column 0 for the current row
			if ( bFreeEndGaps ) matchRow[0] = 0;
			else matchRow[0] = - ( gapOpen * v );    
			
			for ( int h = 1; h < nCols; ++h, ++i )
			{
				float fMatch = cmpAA(strVert.charAt(v-1), strHorz.charAt(h-1), false);
				
				float fUp = matchLastRow[h];
				if ( !bFreeEndGaps || ( h != nCols - 1 ) ) 
				fUp -= gapOpen;
				float fDiag = matchLastRow[h-1] + fMatch;    
				float fLeft = matchRow[h-1];
				if ( !bFreeEndGaps || ( v != nRows - 1 ) ) 
				fLeft -= gapOpen;
				
				matchRow[h] = fUp;
				matchDir[i] = DIRECTION_UP;
				
				if ( fDiag > matchRow[h] )
				{
					matchRow[h] = fDiag;
					matchDir[i] = DIRECTION_DIAGONAL; 
				}
				
				if ( fLeft > matchRow[h] )
				{
					matchRow[h] = fLeft;
					matchDir[i] = DIRECTION_LEFT; 
				} 
			}
		}  
		
		return true;
	}
	private void buildNonAffineOutput ( char chGap )
	{
		strGapHorz = "";
		strGapVert = "";
		
		int i = nCells - 1;
		int v = strInVert.length() - 1;
		int h = strInHorz.length() - 1;
		
		while ( i > 0 )
		{
			switch ( matchDir[i] )
			{
				case DIRECTION_UP:
					strGapHorz = chGap  + strGapHorz;
					strGapVert = strInVert.charAt(v) + strGapVert;
					--v;
					i -= nCols;
				break;
				case DIRECTION_LEFT:
					strGapHorz = strInHorz.charAt(h) + strGapHorz;
					strGapVert = chGap + strGapVert;
					--h;
					--i;
				break;
				case DIRECTION_DIAGONAL:	
					strGapHorz = strInHorz.charAt(h)  + strGapHorz;
					strGapVert = strInVert.charAt(v) + strGapVert;
					--h;
					--v;
					i -= (nCols + 1);
				break;
				default:
					LogTime.PrtError ( "Invalid direction..." );
			}
		}       
		lastGap = new Character ( chGap );
	}
		
	private boolean matchAffine (String strHorz, String strVert )
	{
		nRows = strVert.length() + 1;
		nCols = strHorz.length() + 1;
		nCells = nCols * nRows;
		lastGap = null;
		strInHorz = strHorz;
		strInVert = strVert;
		
		float fTotalGapOpen = - ( gapOpen + gapExtend );
		
		if (!checkAllocation ( )) return false;
		
		// Initialize top row
		for ( int i = 1; i < nCols; ++i )
		{
			matchRow[i] = -Float.MAX_VALUE;
			matchDir[i] = DIRECTION_DIAGONAL;
			gapHorzRow[i] = -Float.MAX_VALUE;
			gapHorzDir[i] = DIRECTION_UP;
			if ( bFreeEndGaps )
				gapVertRow[i] = 0;
			else
				gapVertRow[i] =  -( gapOpen + (i-1) * gapExtend );
				gapVertDir[i] = DIRECTION_LEFT;
		}
		
		// Initalize left column for direction arrays
		for ( int k = 1, i = nCols; k < nRows; ++k, i += nCols )
		{
			matchDir[i] = DIRECTION_DIAGONAL;
			gapHorzDir[i] = DIRECTION_UP;
			gapVertDir[i] = DIRECTION_LEFT;
		}
		
		matchRow[0] = 0;
		
		// Fill in all matricies simultaneously, row-by-row
		for ( int v = 1; v < nRows; ++v )
		{
			int i = ( v * nCols ) + 1;
			
			// "Rotate" the score arrays.  The current row is now uninitialized, but the
			// last row is completely filled in.
			float [] temp = matchLastRow;
			matchLastRow = matchRow;
			matchRow = temp;  
			temp = gapHorzLastRow;
			gapHorzLastRow = gapHorzRow;
			gapHorzRow = temp;
			temp = gapVertLastRow;
			gapVertLastRow = gapVertRow;
			gapVertRow = temp;
			
			// Initialize column 0 for the current row of all "rotating" arrays        
			matchRow[0] = -Float.MAX_VALUE;
			if ( bFreeEndGaps )
			gapHorzRow[0] = 0;
			else
			gapHorzRow[0] = - ( gapOpen + (v - 1) * gapExtend );     
			gapVertRow[0] = -Float.MAX_VALUE;
		
			for ( int h = 1; h < nCols; ++h, ++i)
			{
				float fMatch = cmpAA(strVert.charAt(v-1), strHorz.charAt(h-1), false);
				
				// Match matrix. Compare with the value one cell up and one to the left.
				findBest ( h - 1, false, fMatch, fMatch, fMatch );     
				matchRow [h] = fLastBest;
				matchDir [i] = chLastBest;
				
				// Horizonal gap matrix. Compare with the value one cell up.
				if ( bFreeEndGaps && ( h == nCols - 1 ) )
					findBest ( h, false, 0, 0, 0 );
				else
					findBest ( h, false, -gapExtend, fTotalGapOpen, fTotalGapOpen );
				gapHorzRow [h] = fLastBest;
				gapHorzDir [i] = chLastBest;
				
				// Vertical gap matrix.  Compare with the value one cell to the left.
				if ( bFreeEndGaps && ( v == (nRows - 1) ) )
					findBest ( h-1, true, 0, 0, 0 );
				else
					findBest ( h-1, true, fTotalGapOpen, fTotalGapOpen, -gapExtend );
				gapVertRow [h] = fLastBest;
				gapVertDir [i] = chLastBest;
			}
		}      
		// Set the starting "pointer" for building the output strings
		findBest ( nCols - 1, true, 0, 0, 0 );
		startDir = chLastBest;
		return true;
	}
	
	private void findBest ( int i, boolean bCurRow, float fDUp, float fDDiag, float fDLeft )
	{
		// Choose the best choice with the arbitrary tie break of up, diagonal, left       
		// Note: the inversion between the direction in the matrix and the gap is correct
		float fUp, fDiag, fLeft;
		
		if ( bCurRow )
		{
			fUp = fDUp + gapHorzRow [i];
			fDiag = fDDiag + matchRow [i];
			fLeft = fDLeft + gapVertRow [i];
		}
		else
		{
			fUp = fDUp + gapHorzLastRow [i];
			fDiag = fDDiag + matchLastRow [i];
			fLeft = fDLeft + gapVertLastRow [i];
		}
		
		fLastBest = fUp;
		chLastBest = DIRECTION_UP;
		if ( fDiag > fLastBest )
		{
			fLastBest = fDiag;
			chLastBest = DIRECTION_DIAGONAL;
		}
		if ( fLeft > fLastBest )
		{
			fLastBest = fLeft;
			chLastBest = DIRECTION_LEFT;
		}
	}
	
	private void buildAffineOutput ( char chGap )
	{      
		strGapHorz = "";
		strGapVert = "";
		
		int i = nCells - 1;
		int v = strInVert.length() - 1;
		int h = strInHorz.length() - 1;
		
		char chNextHop = startDir;       
		
		while ( i > 0 )
		{
			switch ( chNextHop )
			{
			case DIRECTION_UP:
				chNextHop = gapHorzDir [i];
				strGapHorz = chGap  + strGapHorz;
				strGapVert = strInVert.charAt(v) + strGapVert;
				--v;
				i -= nCols;
			break;
			case DIRECTION_LEFT:
				chNextHop = gapVertDir [i];
				strGapHorz = strInHorz.charAt(h) + strGapHorz;
				strGapVert = chGap + strGapVert;
				--h;
				--i;
			break;
			case DIRECTION_DIAGONAL:
				chNextHop = matchDir[i];
				strGapHorz = strInHorz.charAt(h)  + strGapHorz;
				strGapVert = strInVert.charAt(v) + strGapVert;
				--h;
				--v;
				i -= (nCols + 1);
			break;
			default:
				LogTime.PrtError( "Invalid direction..." );
			}  
		}      
		lastGap = new Character ( chGap );
	}
	
	private boolean checkAllocation ( )
	{
		if (nCells > maxCells) {
			System.err.println("Not enough memory to align sequences - need " + nCells + "kb");
			return false;
		}
		fLastBest = -Float.MAX_VALUE;
		chLastBest = DIRECTION_DIAGONAL;
		
		try {
			if ( matchDir == null || matchDir.length < nCells )
			{
				matchDir = new char [nCells];
				if ( bUseAffineGap )
				{
					gapHorzDir = new char [nCells];
					gapVertDir = new char [nCells];        
				}
			}
			else {
				for (int i=0; i< matchDir.length; i++) matchDir[i] = ' ';
				if ( bUseAffineGap )
					for (int i=0; i< matchDir.length; i++) 
						gapHorzDir[i] =  gapVertDir[i] = ' ';
			}
			int max = (nRows > nCols ) ? nRows : nCols;
			
			if ( matchRow == null || matchRow.length < max )
			{
				matchRow = new float [max];
				matchLastRow = new float [max];  
				if ( bUseAffineGap )
				{
					gapHorzRow = new float [max];
					gapHorzLastRow = new float [max];
					gapVertRow = new float [max];
					gapVertLastRow = new float [max];
				}
			}
			else {
				for (int i=0; i< matchRow.length; i++) matchRow[i] = 0.0f;
				if ( bUseAffineGap )
					for (int i=0; i< matchRow.length; i++) 
						gapHorzRow[i] =  gapVertRow[i] = 
							gapHorzLastRow[i] =  gapVertLastRow[i] = 0.0f;
			}
		}
		catch (OutOfMemoryError E) {
			matchDir = null;
			maxCells = nCells;
			System.err.println("Not enough memory to align sequences - need " + nCells + "kb");
			return false;
		}
		return true;
	}

	public void clear() {
		matchLastRow = null;
		matchRow = null;
		matchDir = null;
		gapHorzRow = null;
		gapHorzLastRow = null;
		gapHorzDir = null;
		gapVertRow = null;
		gapVertLastRow = null;
		gapVertDir = null;
	}
		
	public float cmpAA(char x, char y, boolean useExact) {
		if (useExact) {
			if (x==y) return 1.0f;
			else return 0.0f;
		}
		boolean blosum = true; // this was done to test blosum
		
		if (blosum) {		
			int i=0, j=0;
			i = AA2index(x);
			j = AA2index(y);
			return (float) blossum[i][j];
		}
		
		if (isCommonAcidSub(x, y)) return matchScore;
		else return mismatchScore;
	}
	/**************************************************************
	 * variables for the Align object 	
	 */
	private String strInHorz = null;
	private String strInVert = null;
	private String strGapHorz = null;
	private String strGapVert = null;
	private Character lastGap = null;
	
	private static final char DIRECTION_UP = '^';
	private static final char DIRECTION_LEFT = '<';
	private static final char DIRECTION_DIAGONAL = '\\';
	
	private float matchScore = 1.8f;
	private float mismatchScore = -1.0f;
	private float gapOpen = 3.0f;	// 3.0f changed to neg in code
	private float gapExtend = 0.7f;	// lowered gaps and was worse
	private boolean bFreeEndGaps = true;
	private boolean bUseAffineGap = false;  // tested, works better on both aa & nt
	
	int nCells, nRows, nCols = Integer.MIN_VALUE;
	private float [] matchLastRow = null;
	private float [] matchRow = null;
	private char []  matchDir = null;
	private float [] gapHorzRow = null;
	private float [] gapHorzLastRow = null;
	private char []  gapHorzDir = null;
	private float [] gapVertRow = null;
	private float [] gapVertLastRow = null;
	private char []  gapVertDir = null;
	
	private char startDir;
	private float fLastBest = -Float.MAX_VALUE;
	private char chLastBest = DIRECTION_DIAGONAL;
	
	private long maxCells = Long.MAX_VALUE;
	/****************************************************************/
	/************************************************
	 * XXX STATIC All the AA and codon translations -- not necessarily used in the above
	 *************************************************************/
	static public char getBaseComplement(char chBase) {
		switch (chBase) {
		case 'a': return 't'; case 'A': return 'T';
		case 'c': return 'g'; case 'C': return 'G';
		case 'g': return 'c'; case 'G': return 'C';
		case 't': return 'a'; case 'T': return 'A';
		case 'R': return 'Y'; case 'Y': return 'R';
		case 'M': return 'K'; case 'K': return 'M';
		case 'H': return 'D'; case 'B': return 'V';
		case 'V': return 'B'; case 'D': return 'H';
		case 'W': case 'S': case 'N': case 'n': 
		case '.': return chBase;
		default: // CAS 9/7/12 will find '*' when writing Protein ORFs		
			return chBase;
		}
	}
	static public boolean isCommonAcidSub(char aa1, char aa2) {
		// Return true anytime the BLOSUM62 matrix value is >= 1. This seems to
		// be how blast places '+' for a likely substitution in it's alignment.
		if (aa1 == aa2)
			return true;

		switch (aa1) {
		case 'Z': // Glx (not in blossum)
			return (aa2 == 'D' || aa2 == 'Q' || aa2 == 'E' || aa2 == 'K' || aa2 == 'B');
		case 'B': // Asx (not in blossum)
			return  (aa2 == 'N' || aa2 == 'D' || aa2 == 'E' || aa2 == 'Z');
		case 'A': // Ala
			return (aa2 == 'S');  // 
		case 'R': // Arg
			return (aa2 == 'Q' || aa2 == 'K');
		case 'N': // Asn
			return aa2 == 'D' || aa2 == 'H' || aa2 == 'S' || aa2 == 'B';
		case 'D': // Asp 
			return aa2 == 'N' || aa2 == 'E' || aa2 == 'B' || aa2 == 'Z';
		case 'Q': // Gln
			return aa2 == 'R' || aa2 == 'E' || aa2 == 'K' || aa2 == 'Z';
		case 'E': // Glu
			return aa2 == 'D' || aa2 == 'Q' || aa2 == 'K' || aa2 == 'B' || aa2 == 'Z';
		case 'H': // His
			return aa2 == 'N' || aa2 == 'Y';
		case 'I': // Ile
			return aa2 == 'L' || aa2 == 'M' || aa2 == 'V';
		case 'L': // Leu
			return aa2 == 'I' || aa2 == 'M' || aa2 == 'V';
		case 'K': // Lys
			return aa2 == 'R' || aa2 == 'Q' || aa2 == 'E' || aa2 == 'Z';
		case 'M': // Met
			return aa2 == 'I' || aa2 == 'L' || aa2 == 'V';
		case 'F': // Phe
			return aa2 == 'W' || aa2 == 'Y';
		case 'S': // Ser
			return aa2 == 'A' || aa2 == 'N' || aa2 == 'T';
		case 'T': // Thr P, G, D, 
			return aa2 == 'S';
		case 'W': // Trp
			return aa2 == 'F' || aa2 == 'Y';
		case 'Y': // Try
			return aa2 == 'H' || aa2 == 'F' || aa2 == 'W';
		case 'V': // Val
			return aa2 == 'I' || aa2 == 'L' || aa2 == 'M';
		case 'P': // Pro
		case 'C': // Cys = no synonymous
		case 'G': // Gly
		default:
			return false;
		}
	}
	static public HashMap <String, String> getAABioChem() {
		HashMap <String, String> aa = new HashMap <String, String> ();
		aa.put("I", "nonpolar");
		aa.put("L", "nonpolar");
		aa.put("V", "nonpolar");
		aa.put("F", "nonpolar");
		aa.put("M", "nonpolar");
		aa.put("C", "polar");
		aa.put("A", "nonpolar");
		aa.put("G", "nonpolar");
		aa.put("P", "nonpolar");
		aa.put("T", "polar");
		aa.put("S", "polar");
		aa.put("Y", "polar");
		aa.put("W", "nonpolar");
		aa.put("Q", "polar");
		aa.put("N", "polar");
		aa.put("H", "basic");
		aa.put("E", "acidic");
		aa.put("D", "acidic");
		aa.put("K", "basic");
		aa.put("R", "basic");
		aa.put("*", "STOP");		
		return aa;
	}
	static public HashMap <String, String> getSynCodonsbyAA() {
		HashMap <String, String> aa = new HashMap <String, String> ();
		aa.put("I", "ATT:ATC:ATA");
		aa.put("L", "CTT:CTC:CTA:CTG:TTA:TTG");
		aa.put("V", "GTT:GTC:GTA:GTG");
		aa.put("F", "TTT:TTC");
		aa.put("M", "ATG");
		aa.put("C", "TGT:TGC");
		aa.put("A", "GGT:GGC:GGA:GGG");
		aa.put("G", "GGT:GGC:GGA:GGG");
		aa.put("P", "CCT:CCC:CCA:CCG");
		aa.put("T", "ACT:ACC:ACA:ACG");
		aa.put("S", "TCT:TCC:TCA:TCG:AGT:AGC");
		aa.put("Y", "TAT:TAC");
		aa.put("W", "TGG");
		aa.put("Q", "CAA:CAG");
		aa.put("N", "AAT:AAC");
		aa.put("H", "CAT:CAC");
		aa.put("E", "GAA:GAG");
		aa.put("D", "GAT:GAC");
		aa.put("K", "AAA:AAG");
		aa.put("R", "CGT:CGC:CGA:CGG:AGA:AGG");
		aa.put("*", "TAA:TAG:TGA");		
		return aa;
	}
	static public Vector <String> getFamilies() {
		Vector <String> fam = new Vector <String> ();
		fam.add("ATT:ATC:ATA");
		fam.add("CTT:CTC:CTA:CTG");
		fam.add("TTA:TTG");
		fam.add("CTT:CTC:CTA:CTG");
		fam.add("GTT:GTC:GTA:GTG");
		fam.add("TTT:TTC");
		fam.add("TGT:TGC");
		fam.add("GCT:GCC:GCA:GCG");
		fam.add("GGT:GGC:GGA:GGG");
		fam.add("CCT:CCC:CCA:CCG");
		fam.add("ACT:ACC:ACA:ACG");
		fam.add("TCT:TCC:TCA:TCG:AGT:AGC");
		fam.add("AGT:AGC");
		fam.add("AGT:AGC");
		fam.add("TAT:TAC");
		fam.add("CAA:CAG");
		fam.add("AAT:AAC");
		fam.add("CAT:CAC");
		fam.add("GAA:GAG");
		fam.add("GAT:GAC");
		fam.add("AAA:AAG");
		fam.add("CGT:CGC:CGA:CGG");
		fam.add("AGA:AGG");
		fam.add("AGA:AGG");
		// whether these 3 should be added depends on usages
		// right now, just using to get RSCU over all characters, where the STOP is not in the ORF
		fam.add("ATG:");
		fam.add("TGG:");
		//fam.add("TAA:TAG:TGA");
		return fam;
	}
	static public Vector <String> getSynCodons() {
		Vector <String> fam = new Vector <String> ();
		fam.add("ATT:ATC:ATA");
		fam.add("CTT:CTC:CTA:CTG");
		fam.add("TTA:TTG");
		fam.add("CTT:CTC:CTA:CTG");
		fam.add("GTT:GTC:GTA:GTG");
		fam.add("TTT:TTC");
		fam.add("TGT:TGC");
		fam.add("GCT:GCC:GCA:GCG");
		fam.add("GGT:GGC:GGA:GGG");
		fam.add("CCT:CCC:CCA:CCG");
		fam.add("ACT:ACC:ACA:ACG");
		fam.add("TCT:TCC:TCA:TCG:AGT:AGC");
		fam.add("AGT:AGC");
		fam.add("AGT:AGC");
		fam.add("TAT:TAC");
		fam.add("CAA:CAG");
		fam.add("AAT:AAC");
		fam.add("CAT:CAC");
		fam.add("GAA:GAG");
		fam.add("GAT:GAC");
		fam.add("AAA:AAG");
		fam.add("CGT:CGC:CGA:CGG");
		fam.add("AGA:AGG");
		fam.add("AGA:AGG");
		fam.add("ATG");
		fam.add("TGG");
		fam.add("TAA:TAG:TGA");
		return fam;
	}
	
	static public char getAAforCodon(String s) {
		String c = s.toUpperCase();
		if (c.equals("ATT") || c.equals("ATC") || c.equals("ATA"))return 'I'; // Ile			
		if (c.equals("CTT") || c.equals( "CTC") || c.equals("CTA") || c.equals("CTG") || c.equals("TTA") || c.equals("TTG")) return 'L';	// Leu						
		if (c.equals("GTT")|| c.equals("GTC")|| c.equals("GTA")|| c.equals("GTG")) return 'V'; // Val			
		if (c.equals("TTT")|| c.equals("TTC"))return 'F'; // Phe			
		if (c.equals("ATG"))return 'M'; // M Start			
		if (c.equals("TGT")||c.equals("TGC")) return 'C'; // Cys			
		if (c.equals("GCT")||c.equals("GCC")||c.equals("GCA")||c.equals("GCG"))return 'A'; // Ala			
		if (c.equals("GGT")||c.equals("GGC")||c.equals("GGA")||c.equals("GGG"))return 'G'; // Gly				
		if (c.equals("CCT")||c.equals("CCC")||c.equals("CCA")||c.equals("CCG")) return 'P'; //Pro			
		if (c.equals("ACT")||c.equals("ACC")||c.equals("ACA")||c.equals("ACG")) return 'T'; // Thr			
		if (c.equals("TCT")||c.equals("TCC")||c.equals("TCA")||c.equals("TCG")||c.equals("AGT")||c.equals("AGC")) return 'S';	// Ser					
		if (c.equals("TAT")||c.equals("TAC")) return 'Y'; // Y			
		if (c.equals("TGG")) return 'W'; // Trp	
		if (c.equals("CAA")||c.equals("CAG")) return 'Q'; // Gln						
		if (c.equals("AAT")||c.equals("AAC")) return 'N'; // Asn			
		if (c.equals("CAT")||c.equals("CAC")) return 'H'; // His			
		if (c.equals("GAA")||c.equals("GAG")) return 'E'; // Glu					
		if (c.equals("GAT")||c.equals("GAC")) return 'D'; // Asp (Asparagine)			
		if (c.equals("AAA") ||c.equals("AAG")) return 'K'; // Lys			
		if (c.equals("CGT")||c.equals("CGC")||c.equals("CGA")||c.equals("CGG")||c.equals("AGA")||c.equals("AGG")) return 'R'; // Arg				
		if (c.equals("TAA")||c.equals("TAG")||c.equals("TGA")) return '*';	// * Stop	
		if (c.equals("XXX")) return '?';
		return '?';
	}
	// Brain's code to convert to number than to amino acid
	public static char getAminoAcidFor(char chA, char chB, char chC) {
		int a,b,c;
		
		if 		(chA=='a' || chA=='A') a = 2;
		else if (chA=='c' || chA=='C') a = 1;
		else if (chA=='g' || chA=='G') a = 3;
		else if (chA=='t' || chA=='T') a = 0;
		else return 'X';
		
		if 		(chB=='a' || chB=='A') b = 2;
		else if (chB=='c' || chB=='C') b = 1;
		else if (chB=='g' || chB=='G') b = 3;
		else if (chB=='t' || chB=='T') b = 0;
		else return 'X';
		
		if 		(chC=='a' || chC=='A') c = 2;
		else if (chC=='c' || chC=='C') c = 1;
		else if (chC=='g' || chC=='G') c = 3;
		else if (chC=='t' || chC=='T') c = 0;
		else return 'X';
		
		return codonToAminoAcid(a * 100 + b * 10 + c);
	}
	
	static public char codonToAminoAcid(int nCodon) {
		switch (nCodon) {
		// T = 0;  C = 1;  A = 2  G = 3

		case 200:// 200 ATT Ile
		case 201:// 201 ATC
		case 202:// 202 ATA
			return 'I'; // Ile
			
		case 100:	// 100 CTT 
		case 101:	// 101 CTC
		case 102:	// 102 CTA
		case 103:	// 103 CTG
		case 2:		// 002 TTA 
		case 3:		// 003 TTG
			return 'L';	// Leu
						
		case 300:// 300 GTT Val
		case 301:// 301 GTC
		case 302:// 302 GTA
		case 303:// 303 GTG
			return 'V'; // Val
			
		case 0: 		// 000 TTT 
		case 1:		// 001 TTC
			return 'F'; // Phe
			
		case 203:// 203 ATG Met
			return 'M'; // M Start
			
		case 30:	// 030 TGT 
		case 31:	// 031 TGC
			return 'C'; // Cys
			
		case 310:// 310 GCT Ala
		case 311:// 311 GCC
		case 312:// 312 GCA
		case 313:// 313 GCG
			return 'A'; // Ala
			
		case 330:// 320 GGT Gly
		case 331:// 321 GGC
		case 332:// 322 GGA
		case 333:// 322 GGG
			return 'G'; // Gly
				
		case 110:// 110 CCT Pro
		case 111:// 111 CCC
		case 112:// 112 CCA
		case 113:// 113 CCG
			return 'P'; //Pro
			
		case 210:// 210 ACT Thr
		case 211:// 211 ACC
		case 212:// 212 ACA
		case 213:// 213 ACG
			return 'T'; // Thr
			
		case 10:	// 010 TCT 
		case 11:	// 011 TCC
		case 12:	// 012 TCA
		case 13:	// 013 TCG
		case 230:	// 230 AGT 
		case 231:	// 231 AGC
			return 'S';	// Ser
					
		case 20:	// 020 TAT Tyr
		case 21:	// 021 TAC
			return 'Y'; // Y
			
		case 33:	// 033 TGG 
			return 'W'; // Trp	

		case 122:// 122 CAA Gln
		case 123:// 123 CAG
			return 'Q'; // Gln
						
		case 220:// 220 AAT Asn
		case 221:// 221 AAC
			return 'N'; // Asn
			
		case 120:// 120 CAT His
		case 121:// 121 CAC
			return 'H'; // His
			
		case 322:// 322 GAA Glu
		case 323:// 322 GAG
			return 'E'; // Glu		
			
		case 320:// 320 GAT Asp
		case 321:// 321 GAC
			return 'D'; // Asp (Asparagine)
			
		case 222:// 222 AAA Lys
		case 223:// 223 AAG
			return 'K'; // Lys
			
		case 130:// 130 CGT Arg
		case 131:// 131 CGC
		case 132:// 132 CGA
		case 133:// 133 CGG
		case 232:// 232 AGA 
		case 233:// 233 AGG
			return 'R'; // Arg
				
		case 22:	// 022 TAA 
		case 23:	// 023 TAG
		case 32:	// 032 TGA 
			return '*';	// * Stop
					
		default:
			return 'X';
		}
	}
	// contains UTRs
	static public String getTranslated(int start, int end, boolean isNeg, String seq) {
		StringBuffer pepSeq= new StringBuffer(end-start);
		String ntSeq= (isNeg) ? getSequenceReverseComplement(seq) : seq;
		
		char [] s = ntSeq.toCharArray();
		for (int i = start-1; i < end && i+2 < seq.length(); i+=3) 
    		{
    			pepSeq.append(getAminoAcidFor(s[i],s[i+1],s[i+2]));
    		}
		return pepSeq.toString();
	}
	// does not contain UTR
	static public String getTranslated(boolean isNeg, String seq) {
		StringBuffer pepSeq= new StringBuffer(seq.length());
		String ntSeq= (isNeg) ? getSequenceReverseComplement(seq) : seq;
		
		char [] s = ntSeq.toCharArray();
		for (int i = 0; i+2 < seq.length(); i+=3) 
    		{
    			pepSeq.append(getAminoAcidFor(s[i],s[i+1],s[i+2]));
    		}
		return pepSeq.toString();
	}
	static public String getCoding(int start, int end, boolean isNeg, String seq) {
		StringBuffer codeSeq= new StringBuffer(seq.length());
		String ntSeq= (isNeg) ? getSequenceReverseComplement(seq) : seq;
		
		for (int i = start-1; i < end && i+2 < seq.length(); i+=3) 
    		{
    			codeSeq.append(ntSeq.substring(i, i+3));
    		}
		return codeSeq.toString();
	}
	static public String getSequenceReverseComplement(String seqIn) {
		String compSeq = "";
		for (int i = seqIn.length() - 1; i >= 0; --i) {
			compSeq += getBaseComplement(seqIn.charAt(i));
		}
		return compSeq;
	}
	// index into blossum matrix
	static public int AA2index(char aa) {
		int i=0;
		switch (aa) {
			case 'A': i = 0; break; case 'R': i = 1; break; case 'N': i = 2; break; case 'D': i = 3; break;
			case 'C': i = 4; break; case 'Q': i = 5; break; case 'E': i = 6; break; case 'G': i = 7; break;
			case 'H': i = 8; break; case 'I': i = 9; break; case 'L': i = 10;break; case 'K': i = 11; break;
			case 'M': i = 12;break; case 'F': i = 13;break; case 'P': i = 14;break; case 'S': i = 15; break;
			case 'T': i = 16;break; case 'W': i = 17;break; case 'Y': i = 18;break; case 'V': i = 19;break;
			case 'B': i = 20;break; case 'J': i = 21;break; case 'Z': i = 22;break; case 'X': i =  23; break;
			case '*': i = 24;break;
		}
		return i;
	}
	static public char Index2AA(int x) {
		char i=' ';
		switch (x) {
		case 0:  i = 'A'; break; case 1: i = 'R'; break; case 2:  i = 'N'; break; case 3: i = 'D'; break;
		case 4:  i = 'C'; break; case 5: i = 'Q'; break; case 6:  i = 'E'; break; case 7: i = 'G'; break;
		case 8:  i = 'H'; break; case 9: i = 'I'; break; case 10: i = 'L';break; case 11: i = 'K'; break;
		case 12: i = 'M'; break; case 13: i = 'F';break; case 14: i = 'P';break; case 15: i = 'S'; break;
		case 16: i = 'T'; break; case 17: i = 'W';break; case 18: i = 'Y';break; case 19: i = 'V';break;
		case 20: i = 'B'; break; case 21: i = 'J';break; case 22: i = 'Z';break; case 23: i =  'X'; break;
		case 24: i = '*'; break;
		}
		return i;
	}
	
	// blosum62 from blastall 
	int blossum[][] = new int [][] {
		{4,-1,-2,-2,	0,-1,-1,	0,-2,-1,	-1,-1,-1,-2,	-1,1,0,-3,-2,0,-2,-1,-1,-1,-4},	
		{-1,	5,0,	-2,-3,1,	0,-2,0,-3,-2,2,-1,-3,-2,-1,-1,-3,-2,-3,-1,-2,0,-1,-4},	
		{-2,	0,6,	1,-3,0,0,0,1,-3,	-3,0,-2,	-3,-2,1,	0,-4,-2,	-3,4,-3,0,-1,-4},	
		{-2,	-2,1,6,-3,0,	2,-1,-1,	-3,-4,-1,-3,	-3,-1,0,	-1,-4,-3,-3,4,-3,1,-1,-4},	
		{0,-3,-3,-3,	9,-3,-4,	-3,-3,-1,-1,	-3,-1,-2,-3,	-1,-1,-2,-2,	-1,-3,-1,-3,-1,-4},	
		{-1,	1,0,0,-3,5,2,-2,	0,-3,-2,	1,0,	-3,-1,0,	-1,-2,-1,-2,0,-2,4,-1,-4},	
		{-1,	0,0,	2,-4,2,5,-2,	0,-3,-3,	1,-2,-3,	-1,0,-1,	-3,	-2,	-2,1,-3,4,-1,-4},	
		{0,-2,0,-1,-3,-2,-2,	6,-2,-4,	-4,-2,-3,-3,	-2,0,-2,	-2,-3,-3,-1,-4,-2,-1,-4},	
		{-2,0,1,	-1,-3,0,	0,-2,8,-3,-3,-1,	-2,-1,-2,-1,	-2,-2,2,	-3,0,-3,0,-1,-4},	
		{-1,	-3,-3,-3,-1,	-3,-3,-4,-3,	4,2,	-3,1,0,-3,-2,-1,	-3,-1,3,-3,3,-3,-1,-4},	
		{-1,	-2,-3,-4,-1,	-2,-3,-4,-3,	2,4,	-2,2,0,-3,-2,-1,	-2,-1,1,-4,3,-3,-1,-4},	
		{-1,	2,0,	-1,-3,1,	1,-2,-1,	-3,-2,5,	-1,-3,-1,0,-1,-3,-2,	-2,0,-3,1,-1,-4},	
		{-1,	-1,-2,-3,-1,	0,-2,-3,	-2,1,2,-1,5,	0,-2,-1,	-1,-1,-1,1,-3,2,-1,-1,-4},	
		{-2,	-3,-3,-3,-2,	-3,-3,-3,-1,	0,0,	-3,0,6,-4,-2,-2,	1,3,	-1,-3,0,-3,-1,-4},	
		{-1,	-2,-2,-1,-3,	-1,-1,-2,-2,	-3,-3,-1,-2,	-4,7,-1,	-1,-4,-3,-2,-2,-3,-1,-1,-4},	
		{1,-1,1,	0,-1,0,0,0,-1,-2,-2,	0,-1,-2,	-1,4,1,-3,-2,-2,0,-2,0,-1,-4},	
		{0,-1,0,	-1,1,-1,	-1,-2,-2,-1,	-1,-1,-1,-2,	-1,1,5,-2,-2,0,-1,-1,-1,-1,-4},	
		{-3,	-3,-4,-4,-2,	-2,-3,-2,-2,	-3,-2,-3,-1,	1,-4,-3,	-2,11,2,	-3,-4,-2,-2,-1,-4},	
		{-2,	-2,-2,-3,-2,	-1,-2,-3,2,-1,-1,-2,	-1,3,-3,	-2,-2,2,	7,-1,-3,-1,-2,-1,-4},	
		{0,-3,-3,-3,	-1,-2,-2,-3,	-3,3,1,-2,1,	-1,-2,-2,0,-3,-1,4,-3,2,-2,-1,-4},
		{-2,-1,4,4,-3,0,1,-1,0,-3,-4,0,-3,-3,-2,0,-1,-4,-3,-3,4,-3,0,-1,-4},
		{-1,-2,-3,-3,-1,-2,-3,-4,-3,3,3,-3,2,0,-3,-2,-1,-2,-1,2,-3,3,-3,-1,-4},
		{-1,0,0,1,-3,4,4,-2,0,-3,-3,1,-1,-3,-1,0,-1,-2,-2,-2,0,-3,4,-1,-4},
		{-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-4},
		{-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4,-4, 1}
	};
	
}
