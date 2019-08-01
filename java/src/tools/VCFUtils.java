package tools;
/***********************************************
 * Will - would you put a one liner explaining what each method does
 * and where it is used in the pipeline.
 * I think:
 * altGenome - creates the alternative genome from the reference genome and variant viles
 * maskGenome - creates a masked genome from the variant files
 * cull? - maybe make a subset file from the variant fiels
 * TCW?
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.ErrorReport;
import util.Format;

import tools.Tmain;
import database.DBConn;

public class VCFUtils 
{
	DBConn mDB = null;
	
	public VCFUtils(DBConn db, String mode)
	{
		try
		{
			mDB = db;
			
			if (mode.equals("cull"))
			{
				File inFile = assertExists(Tmain.sangerSNP);
				File outFile = newFile(Tmain.snpFullFile);			
				cull(inFile,outFile);
				
				inFile = assertExists(Tmain.sangerIndel);
				outFile = newFile(Tmain.indelFullFile);			
				cull(inFile,outFile);
				
			}
			else if (mode.equals("altGenome"))
			{
				Vector<String> vcfFiles = new Vector<String>();
				vcfFiles.add(Tmain.snpFile);
				vcfFiles.add(Tmain.indelFile);
				vcfFiles.add(Tmain.snpNewFile);
				
				applyToGenome(Tmain.genomeFile,Tmain.altGenome,Tmain.ensmFile, Tmain.altAnnot,vcfFiles, Tmain.refBed, Tmain.altBed);				
			}
			else if (mode.equals("TCW"))
			{
				makeTCWFiles(Tmain.tcwDir, Tmain.exprDir,Tmain.mapFile,
						Tmain.ensPep, Tmain.tcwPep);
			}
			else if (mode.equals("maskGenome"))
			{
				Vector<String> vcfFiles = new Vector<String>();
				vcfFiles.add(Tmain.snpFile);
				vcfFiles.add(Tmain.snpNewFile);
				
				maskGenome(Tmain.genomeFile,Tmain.maskedGenome,vcfFiles);				
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	// 
	void cull(File inF, File outF) throws Exception
	{
		System.err.println("Culling " + inF.getName() + " to " + outF.getName());
		String strain = Tmain.sangerStrain;
		int strainCol = -1;
		
		BufferedReader br = new BufferedReader(new FileReader(inF));
		BufferedWriter bw = new BufferedWriter(new FileWriter(outF));
		
		// Copy the header and get the col number for our strain
		while (br.ready())
		{
			String line = br.readLine();
			if (line.startsWith("#CHROM"))
			{
				String[] f = line.split("\\t");
				for (int i = 0; i < f.length; i++) 
				{
					if (f[i].equals(strain))
					{
						strainCol = i;
						System.out.println("Strain: " + strain + " column " + i);
					}
				}
				bw.write(f[0] + "\t" + f[1] + "\t" + f[2] + "\t" + 
						f[3] + "\t" + f[4] + "\t" + f[5] + "\t" + 
						f[6] + "\t" + f[7] + "\n"); // + f[8] + "\t" + 
						//f[strainCol] + "\n");
				break;
			}
			else
			{
				bw.write(line + "\n");
			}
		}
		if (strainCol == -1)
		{
			System.err.println("Did not find column for " + strain);
			return;
		}
		int n = 0;
		int keep = 0;
		int multi = 0;
		while (br.ready())
		{
			n++;
			System.err.print(n + " " + keep + " " + multi + "           \r");
			String line = br.readLine();
			String[] f = line.split("\\t");

			String filt = f[6];
			if (!filt.equals("PASS")) continue;

			String[] alts = f[4].split(",");
			int nAlt = alts.length;
			String gtfield = f[strainCol];
			String gt = gtfield.split(":")[0];
			int gtnum = -1;
			for (int i = 1; i <= nAlt; i++)
			{
				if (gt.equals(i +"/" + i))
				{
					gtnum = i;
					break;
				}
			}
			if (gtnum == -1) continue; // doesn't have the snp or is shown as heterozygous
			
			String alt = alts[gtnum-1];	
			
			String chr = f[0];
			String pos = f[1];
			String ID = f[2];
			String ref = f[3];
			String qual = f[5];
			String info = f[7];
			String format = f[8];
			// CAS was a '.' for info, changed to gtfield so can see what the info was, though so far, of no use
			bw.write(chr + "\t" + pos + "\t" + ID + "\t" + ref + "\t" + alt + "\t" + qual + "\t" + filt + "\t" + gtfield + "\n"); 
			
			keep++;
			if (alts.length > 1) multi++;
		}
		br.close(); bw.close();
		System.err.println("Scanned " + n + " lines; kept " + keep + "; multi " + multi);
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
	

	void applyToGenome(String genomeIn, String genomeOut, String gtfIn, String gtfOut, 
				Vector<String> vcfFiles, String bedOutRef, String bedOutAlt) throws Exception
	{
		boolean writeGenome = true;
		boolean writeGTF = true;
		boolean writeVCF = true;

		File inF = assertExists(genomeIn);
		File inA = assertExists(gtfIn);
		Vector<File> vcfs = new Vector<File>();
		for (String vcf : vcfFiles) 
		{
			vcfs.add(assertExists(vcf));
		}
		
		File outF = null; 
		File outA = null; 
		File outBedRef = null;
		File outBedAlt = null;
		if (writeGenome)
		{
			outF = newFile(genomeOut);
		}
		if (writeGTF)
		{
			outA = newFile(gtfOut);
		}		
		if (writeVCF)
		{
			outBedAlt = newFile(bedOutAlt);
			outBedRef = newFile(bedOutRef);
		}

	
		HashMap<String,TreeMap<Integer,String>> refs = new HashMap<String,TreeMap<Integer,String>>();
		HashMap<String,TreeMap<Integer,String>> alts = new HashMap<String,TreeMap<Integer,String>>();
		
		for (File vcf : vcfs)
		{
			System.err.println("Read " + vcf.getName());
			BufferedReader br = new BufferedReader(new FileReader(vcf));
			while (br.ready())
			{
				String line = br.readLine();
				if (line.startsWith("#")) continue;
				
				String[] f = line.split("\t");
				
				String chr = f[0];
				int pos = Integer.parseInt(f[1]);
				String ref = f[3];
				String alt = f[4];
				
				if (!refs.containsKey(chr)) {refs.put(chr, new TreeMap<Integer,String>());}
				if (!alts.containsKey(chr)) {alts.put(chr, new TreeMap<Integer,String>());}
				
				refs.get(chr).put(pos, ref);
				alts.get(chr).put(pos, alt);
			}
			br.close();
		}
		
		// Make a map of position diffs resulting from the indels
		//
		// The diff at a given pos gives the offset to be used for all subsequent
		// insertions, accounting for the insertions to that point.
		// 
		// Assumes that there is no overlap of indels with snps or other indels. 
		// 
		// We could do this simultaneously with genome update but we also 
		// need this map in order to update the gtf file.
		//
		TreeMap<String,TreeMap<Integer,Integer>> diffs = new TreeMap<String,TreeMap<Integer,Integer>>();
		for (String chr : refs.keySet())
		{
			diffs.put(chr,new TreeMap<Integer,Integer>());
			int tot_diff = 0;
			for (int pos : refs.get(chr).keySet())
			{
				int rlen = refs.get(chr).get(pos).length();
				int alen = alts.get(chr).get(pos).length();
				int diff = alen - rlen;
				tot_diff += diff;
				diffs.get(chr).put(pos, tot_diff);
			}
		}
		BufferedReader br = null;
		BufferedWriter bw = null;
		

		if (writeGenome)
		{
			System.err.println("Creating " + outF.getName());
			bw = new BufferedWriter(new FileWriter(outF));
		}
        br = new BufferedReader(new FileReader(inF));
        Pattern p = Pattern.compile(">(\\S+)\\s*.*");
        StringBuffer chrStr = new StringBuffer();
        String chr2 = "";
        HashMap<String,Integer> chrSize = new HashMap<String,Integer>();
        while (br.ready())
        {
	        String line = br.readLine();
	        if (line.startsWith(">"))
	        {
		        if (!chr2.equals(""))
		        {
			        chrSize.put(chr2, chrStr.length());
			        if (writeGenome)
			        {
			        	handleChr(chr2,chrStr,bw, refs, alts, diffs);
			        }
		        }
		        chrStr = new StringBuffer();
		        chr2 = "";
		        Matcher m = p.matcher(line);
		        if (!m.matches())
		        {
			        System.err.println("bad chr line " + line);
			        System.exit(0);
		        }
		        chr2 = m.group(1);
				if (!diffs.containsKey(chr2))
				{
					diffs.put(chr2,new TreeMap<Integer,Integer>());
					diffs.get(chr2).put(0,0);
				}
				if (!refs.containsKey(chr2))
				{
					refs.put(chr2,new TreeMap<Integer,String>());
				}

	        }
	        else
	        {
		        chrStr.append(line.trim());
	        }
        }
        if (!chr2.equals(""))
        {
	        chrSize.put(chr2, chrStr.length());
	        if (writeGenome)
	        {
	        	handleChr(chr2,chrStr,bw, refs, alts, diffs);
	        }
        }
        br.close();
        if (writeGenome)  bw.close();

		
		// Fill out the diff for efficiency in doing the gtf update
		System.err.println("complete diffs");
		for (String chr : diffs.keySet())
		{
			System.err.print(chr + "          \n");
			Vector<Integer> posList = new Vector<Integer>();
			for (int pos : diffs.get(chr).keySet())
			{
				posList.add(pos);
			}
			
			int prev_pos = 0;
			int prev_diff = 0;
			for (int pos : posList)
			{
				for (int p2 = prev_pos + 100; p2 < pos; p2 += 100)
				{
					diffs.get(chr).put(p2, prev_diff);
				}
				prev_pos = pos;
				prev_diff = diffs.get(chr).get(pos);
			}
			for (int p2 = prev_pos + 100; p2 < chrSize.get(chr); p2 += 100)
			{
				diffs.get(chr).put(p2, prev_diff);
			}
		}
		
		if (writeGTF)
		{
		    br = new BufferedReader(new FileReader(inA));
		    bw = new BufferedWriter(new FileWriter(outA));
		    System.err.println("Creating " + outA.getName());
		    int ln = 0;
		    while (br.ready())
		    {
			    String line = br.readLine();
			    if (!line.startsWith("chr")) continue; // should be eof
			    String[] f = line.split("\t");
			    String chr = f[0];
			    if (!diffs.containsKey(chr))
			    {
				    	System.err.println("bad chr in GTF:" + line);
				    	continue;
			    }
			    int pos1 = Integer.parseInt(f[3]);
			    int pos2 = Integer.parseInt(f[4]);
			    ln++;
			    System.err.print(ln + ":" + chr + " " + pos1 + "             \r");
			    pos1 = updatePos(chr,pos1,diffs);
			    pos2 = updatePos(chr,pos2,diffs);
			    f[3] = String.valueOf(pos1);
			    f[4] = String.valueOf(pos2);

			    bw.write(Format.strArrayJoin(f, "\t"));
			    bw.newLine();
			    bw.flush();
		    }
			br.close();
			bw.close();		
		}

		if (writeVCF)
		{
		    bw = new BufferedWriter(new FileWriter(outBedAlt));
		    BufferedWriter bw2 = new BufferedWriter(new FileWriter(outBedRef));
		    System.err.println("Creating bed files");
		    for (String chr : refs.keySet())
		    {
		    	int prev_pos = 0;
		    	for (int pos : refs.get(chr).keySet())
		    	{
		    		int diff = (prev_pos > 0 ? diffs.get(chr).get(prev_pos) : 0);
		    		String R = refs.get(chr).get(pos);
		    		String A = alts.get(chr).get(pos);
		    		int pos1 = pos + diff;
		    		int pos2 = pos1 + R.length() - 1;
		    		String note = (A.length()==1 && R.length()==1 ? "SNP" : "INDEL");
		    		bw.write(chr + "\t" + pos1 + "\t" + pos2 + "\t" + note + ":" + R + ":" + A);
		    		bw.newLine();
		    		bw2.write(chr + "\t" + pos + "\t" + (pos+R.length()-1) + "\t" + note + ":" + R + ":" + A);
		    		bw2.newLine();

		    		prev_pos = pos;
		    	}
		    }
		    bw.close();
		    bw2.close();
		}


	}
	
	int updatePos(String chr, int pos, TreeMap<String,TreeMap<Integer,Integer>> diffs)
	{
		for (int p = pos-1; p >= 0; p--)
		{
			if (diffs.get(chr).containsKey(p))
			{
				return pos + diffs.get(chr).get(p);
			}
		}
		return pos;
	}
	void handleChr(String chr, StringBuffer chrStr, BufferedWriter bw, 
			HashMap<String,TreeMap<Integer,String>> refs, HashMap<String,TreeMap<Integer,String>> alts, 
			TreeMap<String,TreeMap<Integer,Integer>> diffs) throws Exception
	{
		System.err.println(chr + ": length " + chrStr.length());
		int diff = 0; int snps=0; int indels=0;
		for (int pos : refs.get(chr).keySet())
		{
			String ref = refs.get(chr).get(pos);
			String alt = alts.get(chr).get(pos);
			int mod_pos = pos + diff;
			String bases = chrStr.substring(mod_pos-1, mod_pos-1 + ref.length() );
			if (ref.length() == 1 && alt.length() == 1) snps++;
			else indels++;
			System.err.print(chr + ": " + snps + " snps " + indels + " indels           \r" );
			if (!ref.equals(bases))
			{
				String bases1 = chrStr.substring(mod_pos-1 - 5, mod_pos-1 + ref.length()  + 5);
				System.err.println(chr + ":mismatch:pos:" + pos + " diff:" + diff + " ref:" + ref + " bases:" + bases + " (" + bases1 + ")");
				System.exit(0);
			}
			else
			{
				chrStr.replace(mod_pos-1, mod_pos-1 + ref.length() , alt);
			}
			
			diff = diffs.get(chr).get(pos);
		}
		
		bw.write(">" + chr);
		bw.newLine();
		for (int i = 0; i < chrStr.length(); i += 70)
		{
			bw.write(chrStr.substring(i, Math.min(i+70,chrStr.length())));
			bw.newLine();
		}
	}	
	
	void makeTCWFiles(String tcwDirS, String exprDirS,String mapFileS,
			String ensPepS, String tcwPepS) throws Exception
	{
		File exprDir = assertExists(exprDirS);
		File tcwDir = assertExists(tcwDirS);
		File ensPep = assertExists(ensPepS);

		File mapFile = newFile(mapFileS);
		File tcwPep = newFile(tcwPepS);
		
		File countDir = new File(tcwDir,"counts");
		if (!countDir.exists()) countDir.mkdir();
		
		BufferedReader br = null;
		BufferedWriter bw = null;
		
		int resNameCol = 2; // transcript name in results.xprs
		int resCountCol = 8; // effective count column of results.xprs
		
		// Get all the transcript common names from the db
		// We are just going to work with these.
		// Build maps from ENS to common and vice versa.
		// Note that a few common will be suffixed to make the maps one to one. 
		// NOTE ALSO THAT THERE ARE SOME GENES DIFFERING ONLY BY CASE e.g. GAPDH, Gapdh. 
		// How stupid is this. 
		// Anyway TCW is case-insensitive, so we have to treat these also as duplicate names.
		//
		TreeMap<String,String> HWtransMap = new TreeMap<String,String>(); // these use original case
		TreeMap<String,String> HWtransMap2 = new TreeMap<String,String>();
		
		// The next tracks the count of how many times the common name has appeared so we can suffix it
		TreeMap<String,Integer> HWtransNames = new TreeMap<String,Integer>(); // this will use lowercase

		TreeMap<String,String> finalName = new TreeMap<String,String>(); // to store the original-case name
		
		
		ResultSet rs = mDB.executeQuery("select transiden,transname from trans");
		while (rs.next())
		{
			String tid = rs.getString(1).replace("ENSMUST","");
			String name = rs.getString(2);

			String suffixedName = name; // original case
			
			// Do the comparison using lower case
			if (!HWtransNames.containsKey(name.toLowerCase()))
			{
				HWtransNames.put(name.toLowerCase(), 0);
			}
			else
			{
				int curSuf = 1 + HWtransNames.get(name.toLowerCase());
				HWtransNames.put(name.toLowerCase(),curSuf);
				suffixedName += "_" + curSuf;
			}
			
			HWtransMap.put(tid, suffixedName);
			HWtransMap2.put(suffixedName, tid);
		}
		
		// Write the peptide fasta
		br = new BufferedReader(new FileReader(ensPep));
		bw = new BufferedWriter(new FileWriter(tcwPep));
		Pattern p3 = Pattern.compile(".*transcript:ENSMUST(\\d+).*");
		TreeSet<String> pep_coding = new TreeSet<String>();
		TreeMap<String,String> pepSeq = new TreeMap<String,String>();
		StringBuffer seqb = new StringBuffer();
		String pname = "";
		while (br.ready())
		{
			String line = br.readLine();
			if (line.startsWith(">"))
			{
				if (!pname.equals(""))
				{
					pepSeq.put(pname, seqb.toString());
					seqb= new StringBuffer();
					pname.equals("");
				}
				if (line.contains("transcript_biotype:protein_coding")) // only these match to the protein_coding in GTF
				{
					Matcher m = p3.matcher(line);
					if (m.matches())
					{
						String tid = m.group(1);
						if (HWtransMap.containsKey(tid))
						{
							pname = HWtransMap.get(tid);
							pep_coding.add(tid);
						}

					}
				}
			}
			else
			{
				if (!pname.equals(""))
				{
					seqb.append(line.trim());
				}
			}
		}
		if (!pname.equals(""))
		{
			pepSeq.put(pname, seqb.toString());
			seqb= new StringBuffer();
			pname.equals("");
		}

		br.close();
		// write the sequences for those found in pep.fa
		int good = 0;
		for (String name : pepSeq.keySet())
		{
			String seq = pepSeq.get(name);
			String tid = HWtransMap2.get(name);
			String nameEd = name.replaceAll("-", "_");
			bw.write(">" + nameEd + " ENSMUST" + tid);
			bw.newLine();
			good++;
			for (int i = 0; i < seq.length(); i += 60)
			{
				bw.write(seq.substring(i, Math.min(seq.length(),i+60)));
				bw.newLine();
			}
		}
		// write dummy sequences for those that were in HW but not found in pep.fa
		// Well, I thought there were some of these, but it doesn't find any now
		int XXX = 0;
		for (String tid : HWtransMap.keySet())
		{
			if (!pep_coding.contains(tid))
			{
				String name = HWtransMap.get(tid);
				String nameEd = name.replaceAll("-", "_");
				bw.write(">" + nameEd + " ENSMUST" + tid);
				bw.newLine();
				bw.write("XXX");
				bw.newLine();
				XXX++;
			}
		}
		System.err.println("Wrote " + good + " pep seqs from pep.fa, and " + XXX + " dummies that were not found");
		bw.close();
		// Now go through each sample output and write it to a two-column file
		
		for (File f : countDir.listFiles())
		{
			if (f.getName().endsWith(".count"))
			{
				f.delete();
			}
		}
		
		int fnum = 0;
		for (File dir : exprDir.listFiles())
		{
			if (dir.isDirectory())
			{
				File rf = new File(dir,"results.xprs");
				if (!rf.isFile())
				{
					continue;
				}
				fnum++;
				System.err.print("Reading " + fnum + " " + dir.getName() + "              \r");
				br = new BufferedReader(new FileReader(rf));
				
				File outF = new File(countDir,dir.getName() + ".count");
				bw = new BufferedWriter(new FileWriter(outF));
				TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
				
				if (br.ready()) br.readLine(); // skip header
				while (br.ready())
				{
					String line = br.readLine();
					String[] f = line.split("\\s+");
					if (f.length < 15)
					{
						if (f.length > 1)
						{
							// else it is prob just eof
							System.err.println("Bad line in " + rf.getAbsolutePath());
							System.err.print(line);
						}
						continue;
					}
					String tid = f[resNameCol-1];
					tid = tid.replaceAll("^[ABC]", "");
					int count = Math.round(Float.parseFloat(f[resCountCol-1]));
					if (HWtransMap.containsKey(tid))
					{	
						String name = HWtransMap.get(tid);
						if (!counts.containsKey(name)) 
						{
							counts.put(name, count);
						}
						else
						{
							counts.put(name,counts.get(name) + count);
						}
					}
				}
				// Seqs without counts - It's not finding any of these either, as it should not
				for (String name : HWtransMap2.keySet())
				{
					if (!counts.containsKey(name))
					{
						System.err.println("missing count for " + name + ":" + HWtransMap2.get(name));
						counts.put(name, 0);
					}
				}
				br.close();
				for (String name : counts.keySet())
				{
					String nameEd = name.replaceAll("-", "_");
					bw.write(nameEd + "\t" + counts.get(name));
					bw.newLine();
				}
				bw.close();
			}
		}
	} 
	void maskGenome(String genomeIn, String genomeOut,Vector<String> vcfFiles) throws Exception
	{
	
		File inF = assertExists(genomeIn);
		Vector<File> vcfs = new Vector<File>();
		for (String vcf : vcfFiles) 
		{
			vcfs.add(assertExists(vcf));
		}
		

		File outF = newFile(genomeOut);

	
		HashMap<String,TreeMap<Integer,String>> refs = new HashMap<String,TreeMap<Integer,String>>();
		
		for (File vcf : vcfs)
		{
			System.err.println("Read " + vcf.getName());
			BufferedReader br = new BufferedReader(new FileReader(vcf));
			while (br.ready())
			{
				String line = br.readLine();
				if (line.startsWith("#")) continue;
				
				String[] f = line.split("\t");
				
				String chr = f[0];
				int pos = Integer.parseInt(f[1]);
				String ref = f[3];
				String alt = f[4];
				
				if (ref.length() > 1 || alt.length() > 1) continue; // snps only
				
				if (!refs.containsKey(chr)) {refs.put(chr, new TreeMap<Integer,String>());}
				
				refs.get(chr).put(pos, ref);
			}
			br.close();
		}
		
		BufferedReader br = null;
		BufferedWriter bw = null;
		

		System.err.println("Creating " + outF.getName());
		bw = new BufferedWriter(new FileWriter(outF));
		
	    br = new BufferedReader(new FileReader(inF));
	    Pattern p = Pattern.compile(">(\\S+)\\s*.*");
	    StringBuffer chrStr = new StringBuffer();
	    String chr2 = "";
	    HashMap<String,Integer> chrSize = new HashMap<String,Integer>();
	    while (br.ready())
	    {
	        String line = br.readLine();
	        if (line.startsWith(">"))
	        {
		        if (!chr2.equals(""))
		        {
			        chrSize.put(chr2, chrStr.length());
			        handleChr2(chr2,chrStr,bw, refs);
		        }
		        chrStr = new StringBuffer();
		        chr2 = "";
		        Matcher m = p.matcher(line);
		        if (!m.matches())
		        {
			        System.err.println("bad chr line " + line);
			        System.exit(0);
		        }
		        chr2 = m.group(1);
				if (!refs.containsKey(chr2))
				{
					refs.put(chr2,new TreeMap<Integer,String>());
				}
	
	        }
	        else
	        {
		        chrStr.append(line.trim());
	        }
	    }
	    if (!chr2.equals(""))
	    {
	        chrSize.put(chr2, chrStr.length());
	        handleChr2(chr2,chrStr,bw, refs);
	    }
	    br.close();
	    bw.close();
	
	}

	void handleChr2(String chr, StringBuffer chrStr, BufferedWriter bw, 
			HashMap<String,TreeMap<Integer,String>> refs) throws Exception
	{
		System.err.println(chr + ": length " + chrStr.length());
		int snps=0; 
		for (int pos : refs.get(chr).keySet())
		{
			String ref = refs.get(chr).get(pos);
			String bases = chrStr.substring(pos-1, pos );
			snps++;

			System.err.print(chr + ": " + snps + " snps          \r" );
			if (!ref.equals(bases))
			{
				String bases1 = chrStr.substring(pos-1 - 5, pos + 5);
				System.err.println(chr + ":mismatch:pos:" + pos + "  ref:" + ref + " bases:" + bases + " (" + bases1 + ")");
				System.exit(0);
			}
			else
			{
				chrStr.replace(pos-1, pos , "N");
			}
			
		}
		
		bw.write(">" + chr);
		bw.newLine();
		for (int i = 0; i < chrStr.length(); i += 70)
		{
			bw.write(chrStr.substring(i, Math.min(i+70,chrStr.length())));
			bw.newLine();
		}
	}	
	/*
	// this one has logic for finding the protein coding transcripts and a bunch of checks 
	void makeTCWFiles_old(String ensmFileS, String tcwDirS, String exprDirS,String mapFileS,
			String ensPepS, String tcwPepS) throws Exception
	{
		File ensmFile = assertExists(ensmFileS);
		File exprDir = assertExists(exprDirS);
		File tcwDir = assertExists(tcwDirS);
		File ensPep = assertExists(ensPepS);

		File mapFile = newFile(mapFileS);
		File tcwPep = newFile(tcwPepS);
		
		TreeMap<String,String> names = new TreeMap<String,String>();
		TreeMap<String,String> names2 = new TreeMap<String,String>(); // to get other ordering...
		TreeSet<String> coding = new TreeSet<String>();
		Pattern p1 = Pattern.compile(".*transcript_id\\s*\"ENSMUST(\\d+)\\s*\".*");
		Pattern p2 = Pattern.compile(".*transcript_name\\s*\"([^\"]+)\\s*\".*");
		int resNameCol = 2; // transcript name in results.xprs
		int resCountCol = 8; // effective count column of results.xprs
		
		// Get all the transcript common names from the db
		// We are just going to work with these 
		TreeMap<String,String> HWtransMap = new TreeMap<String,String>();
		TreeSet<String> HWtransNames = new TreeSet<String>();
		ResultSet rs = mDB.executeQuery("select transiden,transname from trans");
		while (rs.next())
		{
			String tid = rs.getString(1).replace("ENSMUST","");
			String name = rs.getString(2);
			HWtransMap.put(tid, name);
			HWtransNames.add(name);
		}
		
		// First build a map from transcript ids to common names,
		// from the ensembl GTF file.
		// We just read this from the DB...but now we can double check. 
		// Note that some common names go to more than one ENS name. 
		// For these we will eventually add suffixes.
		BufferedReader br = new BufferedReader(new FileReader(ensmFile));
		while (br.ready())
		{
			String line = br.readLine();

			Matcher m = p1.matcher(line);
			if (m.matches())
			{
				String tid = m.group(1);
				m = p2.matcher(line);
				
				if (m.matches())
				{
					String name = m.group(1);
					String[] f = line.split("\\s+");
					String type = f[1];
					if (type.equals("protein_coding"))
					{
						coding.add(name);
					}
					else
					{
						continue;
					}
					if (names.containsKey(tid))
					{
						// Checking whether one ENS name goes to two common; doesn't happen
						if (!name.equals(names.get(tid)))
						{
							System.err.println(tid + " two names:" + name + "," + names.get(tid));
						}
					}
					names.put(tid, name);
					// It turns out that some common names go to more than one ens name, where one
					// of them is protein_coding and the other is something else. We'll print them
					// out but basically if we see it as protein_coding once, that's its category
					if (names2.containsKey(name))
					{
						if (!tid.equals(names2.get(name)))
						{
							System.err.println(name + " two tids:" + tid + "," + names2.get(name));
						}
					}
					names2.put(name,tid);

				}
			}
		}
		br.close(); // ensembl gtf
		
		if (names.size() != names2.size())
		{
			System.err.println(names.size() + " trans ids; " + names2.size() + " common names!");
		}
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(mapFile));
		
		for (String name : names2.keySet())
		{
			String tid = names2.get(name);
			String c = (coding.contains(name) ? "coding" : "");
			bw.write(tid + "\t" + name + "\t" + c);
			bw.newLine();
		}
		bw.close();
		System.err.println("Wrote " + names2.keySet().size() + " entries to " 
				+ mapFile.getAbsolutePath());
		
		// Write the peptide fasta
		br = new BufferedReader(new FileReader(ensPep));
		bw = new BufferedWriter(new FileWriter(tcwPep));
		Pattern p3 = Pattern.compile(".*transcript:ENSMUST(\\d+).*");
		TreeSet<String> pep_coding = new TreeSet<String>();
		TreeMap<String,String> pepSeq = new TreeMap<String,String>();
		StringBuffer seqb = new StringBuffer();
		String pname = "";
		while (br.ready())
		{
			String line = br.readLine();
			if (line.startsWith(">"))
			{
				if (!pname.equals(""))
				{
					pepSeq.put(pname, seqb.toString());
					seqb= new StringBuffer();
					pname.equals("");
				}
				if (line.contains("transcript_biotype:protein_coding")) // only these match to the protein_coding in GTF
				{
					Matcher m = p3.matcher(line);
					if (m.matches())
					{
						String tid = m.group(1);
						pep_coding.add(tid);
						if (names.containsKey(tid))
						{
							pname = names.get(tid);
						}
						else
						{
							// There are some protein_coding seqs in the pep.fa that are not in the GTF...why??
							// Anyway we can't use them as they were not aligned in eXpress
							//System.err.println(tid + " in fa but not in gtf!!");
						}
					}
				}
			}
			else
			{
				if (!pname.equals(""))
				{
					seqb.append(line.trim());
				}
			}
		}
		if (!pname.equals(""))
		{
			pepSeq.put(pname, seqb.toString());
			seqb= new StringBuffer();
			pname.equals("");
		}
		for (String name : coding)
		{
			if (!pepSeq.containsKey(name))
			{
				System.err.println(name + " coding in gtf but not fa");
			}
		}
		br.close();
		for (String name : pepSeq.keySet())
		{
			String seq = pepSeq.get(name);
			bw.write(">" + name);
			bw.newLine();
			for (int i = 0; i < seq.length(); i += 60)
			{
				bw.write(seq.substring(i, Math.min(seq.length(),i+60)));
				bw.newLine();
			}
		}
		// Now go through each sample output and write it to a two-column file
		
		for (File f : tcwDir.listFiles())
		{
			if (f.getName().endsWith(".count"))
			{
				f.delete();
			}
		}
		
		int fnum = 0;
		for (File dir : exprDir.listFiles())
		{
			if (dir.isDirectory())
			{
				File rf = new File(dir,"results.xprs");
				if (!rf.isFile())
				{
					continue;
				}
				fnum++;
				System.err.print("Reading " + fnum + " " + dir.getName() + "              \r");
				br = new BufferedReader(new FileReader(rf));
				
				File outF = newFile(tcwDirS + "/" + dir.getName() + ".count");
				bw = new BufferedWriter(new FileWriter(outF));
				TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
				
				if (br.ready()) br.readLine(); // skip header
				while (br.ready())
				{
					String line = br.readLine();
					String[] f = line.split("\\s+");
					if (f.length < 15)
					{
						if (f.length > 1)
						{
							// else it is prob just eof
							System.err.println("Bad line in " + rf.getAbsolutePath());
							System.err.print(line);
						}
						continue;
					}
					String tid = f[resNameCol-1];
					tid = tid.replaceAll("^[ABC]", "");
					int count = Math.round(Float.parseFloat(f[resCountCol-1]));
					if (!names.containsKey(tid))
					{
						System.err.println("Bad transcript " + tid + " in " + rf.getAbsolutePath());
						continue;
					}
					String name = names.get(tid);
					if (!counts.containsKey(name)) 
					{
						counts.put(name, count);
					}
					else
					{
						counts.put(name,counts.get(name) + count);
					}
					
				}
				br.close();
				for (String name : counts.keySet())
				{
					if (coding.contains(name))
					{
						bw.write(name + "\t" + counts.get(name));
						bw.newLine();
					}
				}
				bw.close();
			}
		}
	} */
	
	
		
	
	
	
	
	
	
	
}
