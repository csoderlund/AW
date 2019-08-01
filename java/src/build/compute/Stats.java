package build.compute;

import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import database.DBConn;
import database.MetaData;
import util.ErrorReport;
import util.Globals;
import util.LogTime;

public class Stats {
		
	// called in HetCount for the geneLib table. Called here for the Gene table. Values will be the same.
	static public String ratio(int ref, int alt) {

		String to="-";
		if (ref==0 && alt==0) return "-";
		if (ref+alt < 10) return "--";
		
		double r = (ref==0) ? 1.0 : (double) ref;
		double a = (alt==0) ? 1.0 : (double) alt;
		double frac = (r < a) ? -(r/a) : (a/r);
		
		double diff=1000000;

		for (int i=0; i<Globals.ratioFreq.length; i++) {
			double d = Math.abs(Globals.ratioFreq[i]-frac);
			if (d < diff) {
				diff = d;
				to = Globals.ratioLabels[i];
			}
		}
		return to;
	}
	static public double ratioFraq(int ref, int alt) {
		if (ref==0 && alt==0) return 100.0;
		double r = (ref==0) ? 1.0 : (double) ref;
		double a = (alt==0) ? 1.0 : (double) alt;
		double frac = (r <= a) ? -(r/a) : (a/r);
		return frac;
	}
	static public double chiSqr(double a, double b, double c, double d) {
		double [] disPval =   
{0.99, 0.95,  0.90, 0.80, 0.70, 0.50, 0.30, 0.20, 0.10, 0.09, 0.08, 0.07, 0.06, 0.05, 0.04, 0.03, 0.02, 0.01, 0.001};
		double [] chiSqr  = 
{0.00, 0.004, 0.02, 0.06, 0.15, 0.46, 1.07, 1.64, 2.71, 2.87, 3.06, 3.28, 3.54, 3.84, 4.22, 4.71, 5.41, 6.63, 10.83};
				
		// this isn't called if a+b <10
		if (a + b < 10.0 || c + d < 10.0) return 2.0;
		
		if (a==0.0) a++; if (b==0.0) b++;
		if (c==0.0) c++; if (d==0.0) d++;
		
		double x1 = (a * d) - (b * c);
		x1 = x1 * x1;
		double x2 = (a + b + c + d);
		double x3 = (a+b) * (c+d) * (b+d) * (a+c);
		double chi = (x1 * x2)/x3;
	
		double p=100.0;
		if (chi >= 10.83) p=0.0001;
		else if (chi <= 0.00) p = 0.99;
		else {
			for (int k=0; k<chiSqr.length-1; k++) {
				if (chi > chiSqr[k] && chi < chiSqr[k+1]) {
					double p0 = disPval[k];
					double p1 = disPval[k+1];
					double c0 = chiSqr[k];
					double c1 = chiSqr[k+1];
					p = p0 + (p1-p0) * ((chi-c0)/(c1-c0));
				}
			}	
		}
		chiSquare = chi;
		return p;
	}
	/***********************************************************
	 * 1. Are rank=1 isoforms that have damaged SNPs more highly associated with ASE vs those with SNPs?
	 * 2. Are rank=1 isoforms that have missense SNPs more highly associated with ASE vs those with SNPs?
	 * -. can't compare ASE of isoforms with/without SNPs because we do not know ASE if no SNPs
	 * 3. 
	 */
	public static void prtStats(DBConn mDB) {
		prtChiSqrDam(mDB, "Dam", " and trans.cntDamage>0"); // damaging, snp only
		prtChiSqrDam(mDB, "Mis", " and trans.cntMissense>0");// missense, snp only
		//prtRSCUASE(mDB);
	}
	/*******************************************************
	 * Total is all isoforms with rank=1 and cntSNP>0
	 * of these, are Dam/Mis associated with ASE (DE<0.05) 
	 */
	public static void  prtChiSqrDam(DBConn mDB, String key, String clause) {
		MetaData theMetaData = new MetaData(mDB);
		String [] strains = theMetaData.getStrAbbv();
		String [] tissues = theMetaData.getTisAbbv();	
		double pCutoff=0.05; // both DE cutoff and chi-square pval 
		
		try {
			int total = mDB.executeCount("Select count(*) from trans where rank=1 and cntSNP>0");
			int cntAllDam = mDB.executeCount("Select count(*) from trans where rank=1 and cntSNP>0" + clause);
			
			System.out.println("\nOne Trans per gene (with SNPs) " + total + " " + key + " " + cntAllDam);		
			
			System.out.printf("%6s %8s %8s %8s %8s %7s       %s %s\n",
					"lib", ("+ASE+"+key) , ("!ASE+"+key), ("+ASE!"+key), ("!ASE!"+key), "pval", "ASE", "CHI");
					
			for (int s=0; s<strains.length; s++) {
				if (strains[s].startsWith("B") || strains[s].startsWith("Y")) continue;
				
				for (int t=0; t<tissues.length; t++) {
					String lib = strains[s] + tissues[t]; // using SNP de (not read)
					int cntASE = mDB.executeCount("Select count(*) from transPval " +
							" join trans on trans.TRANSid=transPval.TRANSid " +
							" where abs(" + lib + ")<" + pCutoff + " and trans.rank=1 and cntSNP>0");
					
					int cntASEDam = 
						mDB.executeCount("Select count(*) from transPval " +
							" join trans on trans.TRANSid=transPval.TRANSid " +
							" where abs(transPval." + lib + ")<" + pCutoff + " and trans.rank=1 and cntSNP>0" + clause);
					
					int cntNoASEDam = cntAllDam-cntASEDam;
					int cntASENoDam = cntASE-cntASEDam;
					int cntNoASENoDam = total - cntASEDam - cntASENoDam -  cntNoASEDam;
					double p = Stats.chiSqr(cntASEDam, cntNoASEDam, cntASENoDam, cntNoASENoDam);
					String x = (p<pCutoff) ? "*" : " ";
					System.out.printf("%6s %8d %8d %8d %8d  %6.4f%s      %3d %3.2f\n",
							lib, cntASEDam, cntNoASEDam, cntASENoDam, cntNoASENoDam, p,x, cntASE, chiSquare);
				}
			}		
		}
		catch (Exception e) {ErrorReport.prtError(e, "Codon"); }
	}
	/***************************************************************
	 * 1. is RSCU associated with SNP DE? Codon 1 > Codon 2 and Inbred 1 > Inbred 2 ( or vice versa)
	 *  THIS INCLUDES direction
	 * 	
	 * output two columns per trans=1 and SNP>0
	 *   r1/(r1+r2) c1/(c1+c2) (or de?)
	 *   r1 and r2 are from RSCU column (select SNPtrans join SNPtrans.transid=trans.transid
	 *   		where trans.rank=1 and SNPtrans.rscu not null)
	 *   c1 and c2 are ref and alt counts (Select SNPlib.refCount and SNPlib.altCount where
	 *   		SNPlib.LIBid=libid and SNPLib.SNPid=SNPtrans.SNPid and SNPtrans.transid=trans.transid
	 *   	
	 *   select SNPtrans.rscu, SNPlib.refcount, SNPlib.altCount 
	 *   	from SNPtrans
	 *   	join SNPlib on SNPtrans.SNPid=SNPlib.SNPid
	 *   	where SNPtrans.transid=tranids[id]
	 *
	 */
	public static void  prtRSCUASE(DBConn mDB) {
		MetaData theMetaData = new MetaData(mDB);
		String [] strains = theMetaData.getStrAbbv();
		String [] tissues = theMetaData.getTisAbbv();	
		Pattern patRSCU =   Pattern.compile("(\\d+.\\d+):(\\d+.\\d+)"); // e.g. 1.3:0.5
		ResultSet rs;
		
		try {
			int nTrans = mDB.executeCount("Select count(*) from trans where rank=1 and cntSNP>0");
			int [] transid = new int [nTrans];
			rs = mDB.executeQuery("Select transid from trans where rank=1 and cntSNP>0");
			int id=0;
			while (rs.next()) transid[id++] = rs.getInt(1);
			
			HashMap <String, Integer> libMap = new MetaData(mDB).getLibMap();
			
			System.out.println("C=Cov  R=RSCU");
			for (int s=0; s<strains.length; s++) {
				if (strains[s].startsWith("B") || strains[s].startsWith("Y")) continue;
				
				for (int t=0; t<tissues.length; t++) {
					String lib = strains[s] + tissues[t]; // use reads
					int libid = libMap.get(lib);
					
					// process library
					//String fileName = "data/p_" + lib + ".xls";
					//PrintWriter pwObj = new PrintWriter(new FileOutputStream(fileName, false)); 
					//pwObj.format("Cov RSCU\n");
					int cnt=0, crUp=0, cNrUp=0, NcrUp=0, NcNrUp=0;
					int crDw=0, cNrDw=0, NcrDw=0, NcNrDw=0;
					for (id=0; id<transid.length; id++) {
						rs = mDB.executeQuery("select SNPtrans.rscu, SNPlib.refcount, SNPlib.altCount, SNPtrans.SNPid " +
							" from SNPtrans " +
							" join SNPlib on SNPtrans.SNPid=SNPlib.SNPid " + 
							" where SNPtrans.transid=" + transid[id] + " and SNPlib.LIBid=" + libid +
								" and SNPtrans.rscu is not NULL and SNPlib.repnum=0 ");
						while (rs.next()) {
							 String rscu = rs.getString(1);
							 double cRef = rs.getDouble(2);
							 double cAlt = rs.getDouble(3);
							 int SNPid = rs.getInt(4);
							 
							 Matcher x = patRSCU.matcher(rscu);
							 if (!x.find()) LogTime.PrtError("prt RSCU: Can't parse " + rscu);
							 
							 double rRef = Double.parseDouble(x.group(1));
							 double rAlt = Double.parseDouble(x.group(2));
							 
							 if (cRef==0) cRef=0.1;
							 double cScore = cRef/(cRef+cAlt);
							 double rScore = rRef/(rRef+rAlt);
							 
							 if (cScore>0.7 && rScore>0.7) crUp++;
							 if (cScore>0.7 && rScore<=0.7) cNrUp++;
							 if (cScore<=0.7 && rScore>0.7) NcrUp++;
							 if (cScore<=0.7 && rScore<=0.7) NcNrUp++;
							 
							 if (cScore<0.3 && rScore<0.3) crDw++;
							 if (cScore<0.3 && rScore>=0.3) cNrDw++;
							 if (cScore>=0.3 && rScore<0.3) NcrDw++;
							 if (cScore>=0.3 && rScore>=0.3) NcNrDw++;
							 
							 cnt++;
							 if (cnt %1000 == 0) System.out.print("    wrote " + cnt + "\r");
						}
					}
					double p = Stats.chiSqr(crUp, cNrUp, NcrUp, NcNrUp);
					String x = (p<0.05) ? "*" : " ";
					System.out.format("Lib=%s   total=%d\n", lib, cnt);
					System.out.format("  UP   CR=%4d C!R=%4d !CR=%4d !C!R=%5d  p=%5.4f%s \n",
							crUp, cNrUp, NcrUp, NcNrUp, p, x);
					
					p = Stats.chiSqr(crDw, cNrDw, NcrDw, NcNrDw);
					x = (p<0.05) ? "*" : " ";
					System.out.format("  DW   CR=%4d C!R=%4d !CR=%4d !C!R=%5d  p=%5.4f%s\n",
							crDw, cNrDw, NcrDw, NcNrDw, p,x);
					//pwObj.close();
				}
			}		
		}
		catch (Exception e) {ErrorReport.prtError(e, "Codon"); }
	}
	
	// Chi square code allowing calculation of lower p-values than in tables
	// From http://www.codeproject.com/Articles/432194/How-to-Calculate-the-Chi-Squared-P-Value
	static public double chisqr(int Dof, double Cv)
	{
	    if(Cv < 0 || Dof < 1)
	    {
	        return 0.0;
	    }
	    double K = ((double)Dof) * 0.5;
	    double X = Cv * 0.5;
	    if(Dof == 2)
	    {
	    		return Math.exp(-1.0 * X);
	    }

	    Double PValue = igf(K, X);
	    if (PValue.isNaN())
	    {
	    		return Double.MIN_VALUE;
	    }
	    PValue /= gamma(K);
	    if (PValue.isNaN())
	    {
	    		return Double.MIN_VALUE;
	    }
	    
	    return (1.0 - PValue);
	}
	// Incomplete gamma function
	static double igf(double S, double Z)
	{
	    if(Z < 0.0)
	    {
	    	return 0.0;
	    }
	    double Sc = (1.0 / S);
	    Sc *= Math.pow(Z, S);
	    Sc *= Math.exp(-Z);
	 
	    double Sum = 1.0;
	    double Nom = 1.0;
	    double Denom = 1.0;
	 
	    for(int I = 0; I < 200; I++)
	    {
			Nom *= Z;
			S++;
			Denom *= S;
			Sum += (Nom / Denom);
	    }
	 
	    return Sum * Sc;
	}
	// Gamma function, "Spouge's approximation"
	static double gamma(double N)
	{
	    double SQRT2PI = 2.5066282746310005024157652848110452530069867406099383;
	 
	    double A = 10.0; // Arbitrary integer, relative error goes like 2PI^(-A)
	    double Z = (double)N;
	    double Sc = Math.pow((Z + A), (Z + 0.5));
	    Sc *= Math.exp(-1.0 * (Z + A));
	    Sc /= Z;
	 
	    double F = 1.0;
	    double Ck;
	    double Sum = SQRT2PI;
	 

	    for(int K = 1; K < A; K++)
	    {
	        Z++;
			Ck = Math.pow(A - K, K - 0.5);
			Ck *= Math.exp(A - K);
			Ck /= F;
		 
			Sum += (Ck / Z);
		 
			F *= (-1.0 * K);
	    }
	 
	    return (double)(Sum * Sc);
	}
	// Calculate binomial exact pvalue for r=0.5, with no approximation.
	// It's easy to approximate as described below but the time is dominated by database upload anyway.
	// Of course if we weren't going to use the approximate, it would have made more sense to 
	// program it for any value of r. 
	//
	// Approximating for r=0.5 is easy because the exponentials r^K r^(N-k) is just 2^-N and factors out
	// leaving a sum of combinatorial symbols (N k) which can be cut off easily
	// since (N m) = ((m+1)/(N-m))*(N m+1) so the remainder is always less than a
	// geometric series with factor f=(m+1)/(N-m)
	
	static double binomialHalf(int N, int K)
	{
		if (K < 0 || K > N)
		{
			System.err.println("Invalid binomial values:" + N + "," + K);
			return 1.0;
		}
		if (K > N/2)
		{
			K = N - K;
		}
		double pvalue = 1.0;
		double factorSum = 1.0;
		double factorPartial = 1.0;
		for (double m = K-1; m >= 0; m--)
		{
			double f = (m+1)/(N-m);
			factorPartial *= f;
			factorSum += factorPartial;
		}
		double logCombNK = 0;
		for (double m = 1; m <= K; m++)
		{
			logCombNK += Math.log((N-m+1)/m);
		}
		pvalue = Math.exp(-N*Math.log(2) + logCombNK + Math.log(factorSum));
		return pvalue;
	}

	static double chiSquare=0.0;
}
