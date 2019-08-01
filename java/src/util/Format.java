package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class Format {
	 
	    // default double
		public static String saveDouble(double d) {
			return roundBoth(d, 3, 2);
		}
		
	    // This routine first applies the decimal rounding to numDec places.
	    // If this returns zero, then it goes to significant digits with numSig places.
	    // E.g., for numSig=numDec=2 the results are
	    // 45.678 --> 45.68, .00045678 --> .00046
	    // This was the original default behavior of TCW (see function decimalString), except it truncated
	    static public String roundBoth(double doub, int numSig, int numDec)
	    {
		    	BigDecimal d = new BigDecimal(doub);
		    	int origSig = d.precision(); 
		    	numSig = Math.min(origSig, numSig); // can't ask for more sig figs than we started with
		    	
		    	// First try the decimal 
		    	BigDecimal d2 = new BigDecimal(doub).setScale(numDec, RoundingMode.HALF_UP);
		    	if (d2.precision() >= numSig) 
		    	{
		    		// we didn't lose sig figs with the decimal rounding, hence good to go
		    		return d2.toString();
		    	}
		    	return roundToSigFigs(doub, numSig);   	
	    }
	    static public String roundToSigFigs (double d, int numSigFigs )
	    {
		    	BigDecimal bd1 = new BigDecimal(d, new MathContext(numSigFigs));
		    	return bd1.toString();
	    }
	    static public String roundDec(double doub, int numDec)
	    {
	       	BigDecimal d = new BigDecimal(doub);    	
	       	d = d.setScale(numDec, RoundingMode.HALF_UP);   
	       	return d.toString();
	    }
	    // mdb added 4/4/08 - maybe could be simplified
	    static public String decimalString (double d, int numSigFigs )
	    {
	    		if (d == 0) return "0.0";
	    	
			String strVal = String.valueOf(d);
			
			int decIndex = strVal.indexOf('.');
			if (decIndex >= 0) 
			{ 
				// has a decimal point
				int expIndex = strVal.indexOf('E');
				
				if (expIndex > 0) 
				{
					// has exponent
					;
				}
				else if (d < 1 && d > -1) 
				{ 
					// count leading zeros (if < 1)
					do { decIndex++; } 
					while (decIndex < strVal.length() && strVal.charAt(decIndex) == '0');
				}
				
				// copy exponent
				String expStr = "";
				if (expIndex > 0) 
				{
					expStr = strVal.substring(expIndex);
					strVal = strVal.substring(0, expIndex);
				}
				
				// truncate value string 
				strVal = strVal.substring(0, Math.min(decIndex+numSigFigs, strVal.length()));
				strVal += expStr;
			}
			
			return strVal;
	    }
	    
		static public String formatDecimal ( double d )
		{
			if ( numFormat == null )
			{
				numFormat = new DecimalFormat ();
				numFormat.setMinimumIntegerDigits(1);
				numFormat.setMaximumFractionDigits(1);
			}
				
			if ( d == 0 )
				return "0";

			if ( d < 0.1 && d > -0.1 )
				numFormat.applyPattern( "#.#E0" );
			else
				numFormat.applyPattern( "###.#" );
			return numFormat.format(d);
		}
		static public String percent(int n1, int n2) {
			double x = ((float) n1 / (float) n2) * 100.0;
			return String.format("%3.1f%s", x, "%");
		}
		static public String percent(long n1, long n2) {
			double x = ((float) n1 / (float) n2) * 100.0;
			return String.format("%3.1f%s", x, "%");
		}
		static public String percent(double n1, double n2) {
			double x = ( n1 /  n2) * 100.0;
			return String.format("%3.1f%s", x, "%");
		}
		static private DecimalFormat numFormat = null;
		
		/************************************************
		 * Random routines for formating
		 */
		static public String getChr(String input) {
			String chr = input.substring(0,3);
			if (!chr.equals("chr")) {
				LogTime.PrtWarn("chr value: " + input);
				return "chr0";
			}
			String type = input.substring(3);
			if (type.equals("X") || type.equals("Y") || 
					type.equals("XY") || type.equals("MT")) return input;

			try  {  
				Integer.parseInt( type );  
				return input;  
			}  
			catch( Exception e)  {  
				return "chr0";  
			} 
		}
		
		static public int getChrN(String input)
		{
			String chr = input.substring(3);
			if (isInteger(chr)) return Integer.parseInt(chr);
			if (chr.equals("X")) return 23;	
			if (chr.equals("Y")) return 24;
			if (chr.equals("XY")) return 25; // none in bgi,  pseudo-autosomal region of X
			if (chr.equals("MT")) return 26; // Mitochondrial*/
			return 0;
		}
		
		// parseInt does not recognize a '+' before number
		static public int getInt(String input) {
			String sign="+";
			int num=0;
			Pattern u1 = Pattern.compile("(\\D)(\\d+)"); // non-digits digits

			Matcher m = u1.matcher(input);
			if (m.find()) {
				sign = m.group(1);
				num = Integer.parseInt(m.group(2));
			}
			if (sign.equals("-")) num = -num;
			return num;
		}
		static public boolean isInteger( String input )  
		{  
			try  {  
				Integer.parseInt( input );  
				return true;  
			}  
			catch( Exception e)  {  
				return false;  
			}  
		}  

		/*******************************************************
		 * not used but could be useful
		 */
		static public BufferedReader openGZIP(String file) {
			try {
				FileInputStream fin = new FileInputStream(file);
				GZIPInputStream gzis = new GZIPInputStream(fin);
				InputStreamReader xover = new InputStreamReader(gzis);
				BufferedReader is = new BufferedReader(xover);
				return is;
			}
			catch (Exception e) {
		    		ErrorReport.prtError(e, "Cannot open gzipped file " + file);
		    }
			return null;
		}
		static public String strArrayJoin(String[] sa, String delim)
		{
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < sa.length; i++)
			{
				out.append(sa[i]);
				if (i < sa.length - 1)
					out.append(delim);
			}
			return out.toString();
		}		
		static public String strCollectionJoin(Collection c, String delim)
		{
			StringBuilder ret = new StringBuilder();
			Iterator i = c.iterator();
			while (i.hasNext())
			{
				ret.append(i.next().toString() + delim);
			}
			return ret.toString();
		}
}
