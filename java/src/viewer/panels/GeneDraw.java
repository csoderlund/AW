package viewer.panels;
/*********************************************
 * Draws the gene with its transcripts and variants
 * 
 * TODO: 
 * add indel length
 * have button to turn introns variants on/off
 * show coverage of the selected libraries from trans/gene table
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.MouseInputListener;

import database.DBConn;
import util.ErrorReport;
import util.Globals;
import viewer.ViewerFrame;
import viewer.controls.CreateJ;
import viewer.controls.ViewTextPane;

public class GeneDraw extends JPanel {
	private static final long serialVersionUID = -8093046634969009273L;
	private static final String htmlFile = "/html/GeneDraw.html";
	private float zoom = 1.0f;
	private int initW = 600;
	private int yTop = 40;
	private int xLeft = 20;
	private int exonH = 6;
	
	private Color genomeC = Color.black;
	private Color exonC = Color.gray;
	private Color snpC = Color.black;
	private Color indel = Color.RED;
	
	private String [] colStr = {"blue", "cyan", "gray", "green", "magenta"};
	private Color[] colors = {Color.blue, Color.CYAN, Color.GRAY, Color.GREEN, Color.MAGENTA};
	private int colorIdx=0;
	
	DecimalFormat nF = new DecimalFormat("###,###,###,###" );
	
	public GeneDraw(ViewerFrame pF, int gIdx) 
	{
		geneIdx = gIdx;
		theViewerFrame = pF;
		readDB();
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		createTopPanel();
		createDrawPanel();
	
		add(Box.createVerticalStrut(10));
		add(ctrlPanel);
		add(Box.createVerticalStrut(10));
		add(drawScroll);
	}
	
	/********************************************
	 * top panel
	 */
	private void createTopPanel() {
		try {
			ctrlPanel = CreateJ.panelLine();	
			ctrlPanel.add(Box.createHorizontalStrut(5));
			int len = (geneEnd-geneStart)+1;
			ctrlPanel.add(new JLabel(" Gene: " + geneName + "  Chr" + chr +  " Strand" + geneStrand 
				+ " Len: " + nF.format(len)));
			ctrlPanel.add(Box.createHorizontalStrut(20));
			
			btnExons = new JButton("Exons ");
			btnExons.setBackground(Globals.COLOR_MENU);
			btnExons.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					ViewTextPane.displayInfo(geneName + " Exons", msgExon, false);
					if (Globals.toSTD) System.out.println(msgExon);
				}
			});
			ctrlPanel.add(btnExons);
			ctrlPanel.add(Box.createHorizontalStrut(5));
			
			btnSNPs = new JButton("Vars");
			btnSNPs.setBackground(Globals.COLOR_MENU);
			btnSNPs.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					ViewTextPane.displayInfo(geneName + " Variants", msgVariants, false);
					if (Globals.toSTD) System.out.println(msgVariants);
				}
			});
			ctrlPanel.add(btnSNPs);
			ctrlPanel.add(Box.createHorizontalStrut(5));
			
			btnPval = new JButton("Pvals");
			btnPval.setBackground(Globals.COLOR_MENU);
			btnPval.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					ViewTextPane.displayInfo(geneName + " Variant Pvalues", msgPval, false);
					if (Globals.toSTD) System.out.println(msgVariants);
				}
			});
			ctrlPanel.add(btnPval);
			ctrlPanel.add(Box.createHorizontalStrut(10));
			
			btnIn  = new JButton(createImageIcon("/images/plus.gif"));	
			btnIn.setBackground(Globals.COLOR_PROMPT); 
			btnIn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					zoom *= 1.5;
					refreshPanel();
				}
			});
			ctrlPanel.add(btnIn);
			ctrlPanel.add(Box.createHorizontalStrut(5));
			
			btnOut = new JButton(createImageIcon("/images/minus.gif"));
			btnOut.setBackground(Globals.COLOR_PROMPT);
			btnOut.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					zoom *= .75;
					refreshPanel();
				}
			});
			ctrlPanel.add(btnOut);
			ctrlPanel.add(Box.createHorizontalStrut(5));
			
			btnHome = new JButton("Reset");
			btnHome.setBackground(Globals.COLOR_PROMPT);
			btnHome.setBackground(Globals.COLOR_PROMPT); 
			btnHome.setBackground(Globals.COLOR_PROMPT);
			btnHome.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					zoom = 1.0f;
					refreshPanel();
				}
			});
			ctrlPanel.add(btnHome);
			ctrlPanel.add(Box.createHorizontalStrut(5));
			
			JButton btnHelp = new JButton("Help");
	        btnHelp.setBackground(Globals.COLOR_HELP);
	        btnHelp.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					ViewTextPane.displayHTML(theViewerFrame, "Gene Query Help", htmlFile);
				}
			});  	
			ctrlPanel.add(btnHelp);
			
			ctrlPanel.add(Box.createHorizontalGlue());
			ctrlPanel.setMaximumSize(ctrlPanel.getPreferredSize());
		}
		catch (Exception e) {ErrorReport.prtError(e, "Gene Draw - draw ctrl panel"); }
	}
	/*********************************************
	 * draw panel
	 * TRIED: everything. It scrolls with a regular panel, but there is something
	 * about the image dimensions that its not recognizing. 
	 * I put the image into a Page Layout, it showed nothing
	 * I played with setting dimensions on the scroll and image, with no success
	 */
	private void createDrawPanel() {
		imagePanel = new imageClassPanel(this);	
		drawScroll = new JScrollPane(imagePanel);
		drawScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		drawScroll.setPreferredSize(new Dimension(getWidth(), getHeight()));
		
		refreshPanel();
	}
	private void refreshPanel() {
		int w = (int) (initW*zoom) + 200; // works for width, 200 for gene 'end' text
		int h = trList.size()*40; // kludgy but it works pretty good 
		imagePanel.setPreferredSize(new Dimension(w, h));
		//imagePanel.setPreferredSize(new Dimension(5000, 5000));
		imagePanel.removeAll();
		imagePanel.revalidate();
		imagePanel.repaint();
	}
	
	/*********************************************
	 * draw image
	 * todo - if variants are too close, can't see them
	 * todo - scale indel according to its len 
	 */
	public void drawImage(Graphics g)
	{
		Graphics2D g2 = (Graphics2D)g;
		super.paintComponent( g );
		setBackground(Color.WHITE);
		FontMetrics fm = g2.getFontMetrics();
		int start;
		int end;
		int w;
		
		float geneLen = (geneEnd - geneStart + 1);
		float scale = (zoom*((float)initW))/geneLen;
		
		int xL = xLeft;	
		int y = yTop;
		int x1, x2;
		
		String s=String.valueOf(geneStart);
		for (Trans tr : trList) 
			if (tr.name.length()>s.length()) s = tr.name;
		int sLen = fm.stringWidth(String.valueOf(s));
		xL += sLen + 5;
		
		// draw gene coords and line
		g2.drawString(nF.format(geneStart), xLeft, y-3);
		g2.setColor(genomeC);
		x1 = xLeft + sLen+5;
		x2 = x1 + (int)((geneEnd - geneStart + 1)*scale);
		g2.drawLine(x1, y, x2, y);
		g2.drawString(nF.format(geneEnd), x2+5, y-3);
		
		// draw ticks
		for (int i = geneStart; i <= geneEnd; i++)
		{
			if (i % 1000 == 0)
			{
				int x = xL + (int)((i - geneStart)*scale);
				g2.drawLine(x, y-5, x, y+5);
			}
		}
		g2.setColor(exonC);

		// draw transcripts
		for (Trans tr : trList)
		{
			y += 30;
			g2.setColor(Color.BLACK);
			g2.drawString(tr.name, xLeft, y-3);
			start = tr.start;
			end = tr.end;
			w = (int)((end-start)*scale);
			x1 = xL + (int)((start-geneStart)*scale);			
			g2.fillRect(x1, y-1, w, 2);
			
			for (int e : tr.exons)
			{
				Exon ex = exonList.get(e);
				g2.setColor(ex.c);
				int len = ex.y - ex.x;
				
				x1 = xL + (int)((ex.x-geneStart)*scale);
				w = (int)(len*scale);
				if (w==0) w=1;
				g2.fillRect(x1, y-exonH, w, 2*exonH); // x, y, width, height
			}
		}
		int yTransBottom = y;
		
		// draw SNPs
		int inc=10;
		int nVar=1;
		int level=0;
		int last=0;
		for (Variants v : varList)
		{
			if (!v.isSNP) g2.setColor(indel);
			else g2.setColor(snpC);

			int x = (int)((v.pos-geneStart)*scale);
			x1 = xL + x;
			g2.drawLine(x1, yTop-inc, x1, yTransBottom +inc); // x1, y1, x2, y2
			
			if (last!=0 && x-last<30) level+=inc;
			else {level=0; last = x;}
			
			g2.drawString(nVar+v.ase, x1-1, yTransBottom+20+level);
			nVar++;
		}
	}	
	public String getName() {return "Draw " + geneName;}
	public String getSummary(){return "Draw " + geneName;}
	
	private static ImageIcon createImageIcon(String path) {
	    java.net.URL imgURL = GeneDraw.class.getResource(path);
	    if (imgURL != null)
	    	return new ImageIcon(imgURL);
	    else {
	    		System.err.println("Couldn't find icon: "+path);
	    		return null;
	    }
	}
	/********************************************
	 * get info from database and make lists for display
	 */
	private void readDB() {
		try {
			DBConn mDB = ViewerFrame.getDBConnection();
			
		// gene info
			ResultSet rs = mDB.executeQuery("select geneName, chr, strand, start, end from gene " +
					" where geneid=" + geneIdx);
			rs.first();
			geneName = rs.getString(1);
			chr = rs.getString(2);
			geneStrand = rs.getString(3);
			geneStart = rs.getInt(4);
			geneEnd = rs.getInt(5);
			if (geneStart>geneEnd) {
				geneEnd = rs.getInt(4);
				geneStart = rs.getInt(5);
			}
		// trans info
			
			rs = mDB.executeQuery("select  transid, transName,start, end from trans " +
					" where geneid=" + geneIdx + " order by transName asc");
			int nTrans=1;
			while (rs.next()) {
				int tid = rs.getInt(1);
				String tname = rs.getString(2);
				int start = rs.getInt(3);
				int end = rs.getInt(4);
				if (start>end) {
					end = rs.getInt(3);
					start = rs.getInt(4);
				}
				trList.add( new Trans(tid, (nTrans + "." + tname), start, end));
				nTrans++;
			}	
		// exon info 
			int eIdx=0;
			nTrans=1;
			for (Trans tr : trList) {
				rs = mDB.executeQuery("select cStart, cEnd, frame, cntSNP, cntIndel " +
						" from transExon " +
						" where cStart>0 && cEnd> 0 && transid=" + tr.transid);
				while (rs.next())
				{
					int s=rs.getInt(1), e=rs.getInt(2), f=rs.getInt(3);
					int idx=-1;
					for (int i=0; i<exonList.size(); i++) {
						Exon ex = exonList.get(i);
						if (f==-1 && ex.frame!=-1) continue;
						if (f!=-1 && ex.frame==-1) continue;
						if (ex.x==s && ex.y==e) {
							idx = i; break;
						}
					}
					if (idx == -1) {
						Exon ex = new Exon(s, e, f, nTrans, rs.getInt(4), rs.getInt(5));
						exonList.add(ex);
						tr.add(eIdx);
						eIdx++;
					}
					else {
						tr.add(idx);
						exonList.get(idx).add(nTrans);
					}
				}	
				nTrans++;
			}
			// Create Exon popup content
			// we can't know the order of all transcript exons until we have them all
			// and we need to assign color and create msg based on asc pos
			int [] st = new int [exonList.size()];
			for (int i=0; i<exonList.size(); i++) st[i]=i;
			for (int i=0; i<exonList.size()-1; i++) {
				for (int j=i+1; j<exonList.size(); j++) {
					Exon p1= exonList.get(st[i]);
					Exon p2= exonList.get(st[j]);
					if (p1.x > p2.x) {
						int t = st[i];
						st[i] = st[j];
						st[j] = t;
					}
				}
			}
	
			msgExon= String.format(
					"%2s %3s %3s %7s %7s %5s %8s %4s %s\n", 
					"", "SNP", "IDL",  "Rstart", "Rend", "Len", "Color", "!CDS", "Trans");
			int cnt=1;
			for (int i=0; i<exonList.size(); i++) {
				Exon p1= exonList.get(st[i]);
				String sCol="";
				if (p1.frame==-1) {
					p1.add(Color.BLACK,i);
					sCol = "black";
				}
				else {
					p1.add(colors[colorIdx], i);
					sCol = colStr[colorIdx];
					colorIdx++;
					if (colorIdx>=colors.length) colorIdx=0;
				}
				String cds = (p1.frame==-1) ? "X " : "";
				msgExon += String.format("%2d %3d %3d %7s %7s %5s %8s %4s %s\n", 
						cnt, p1.cntSNP, p1.cntIndel, 
						 nF.format(p1.x-geneStart), nF.format(p1.y-geneStart), nF.format(p1.y-p1.x+1),
						sCol, cds, p1.transList);
				cnt++;
			}
			
		// SNPs in this range -- need to add indel length
			DecimalFormat df1 = new DecimalFormat("#0.0##");
			DecimalFormat df2 = new DecimalFormat("0E0;0E0");
			msgVariants= String.format("%2s %-11s %-3s %7s %-10s %s\n", 
					"", "Name", "IDL", "Rpos", "Trans", "EffectList");
		
			msgPval = "#Var ";
			libList = theViewerFrame.getMetaData().getHybLibs();
			String libSQL="";
			for (String lib : libList) {
				libSQL += "," + lib;
				if (lib.length()>6) lib.substring(0,6);
				msgPval+= lib + " ";
			}
			msgPval += "\n";
			
			rs = mDB.executeQuery("select " +
					" rsid, pos, isSNP, isdamaging, effectList, dist, ref, alt " + libSQL +
					" from SNP " +
					" where pos >=" + geneStart + " and pos <=" + geneEnd + 
					" and chr='" + chr + "'" +
					" order by pos asc");
			Vector <Integer> transList = new Vector <Integer> ();
			cnt=1;
			int libStart=9, libEnd=9+libList.size();
			while(rs.next())
			{
				String id = rs.getString(1);
				int pos = rs.getInt(2);
				boolean isSNP = rs.getBoolean(3);
				int damage = rs.getInt(4);
				String effect = rs.getString(5);
				String ref = rs.getString(7), alt=rs.getString(8);
				int max = Math.abs(ref.length()-alt.length()); 
				
				transList.clear();
				boolean isASE=false;
				String trans = "";
				for (int i=0; i<exonList.size(); i++) {
					Exon ex= exonList.get(st[i]); // use sort list index
					if (pos >= ex.x && pos <= ex.y)  {
						for (int t : ex.transVec) {			
							if (!transList.contains(t)) {
								transList.add(t);
								trans += (trans.equals("")) ? t : "," + t;
							}
						}
					}
				}
				
				msgVariants += String.format("%2d %-11s %3d %7s %-10s %s\n", 
						cnt, id, max, nF.format(pos-geneStart), trans, effect);
				
				msgPval += String.format("%3s ", cnt);
				for (int i=libStart; i< libEnd; i++) {
					double pval = rs.getDouble(i);
					String p="";
					if (pval==Globals.NO_PVALUE) p = "-";
					else if (pval >= 0.001 && pval < 1) p = df1.format(pval);
				    	else p = df2.format(pval);
					msgPval += String.format("%6s ", p);
					if (pval<Globals.AI_PVALUE) isASE=true;
				}
				msgPval += "\n";
				varList.add(new Variants(id, pos, isSNP, damage, isASE));
				cnt++;
			}
		}
		catch (Exception e) {ErrorReport.prtError(e, "Gene Draw - read db"); }
	}
	/****************************************************
	 * image class
	 */
	private class imageClassPanel extends JPanel implements MouseInputListener
	{
		private static final long serialVersionUID = -5567198149411527973L;
		GeneDraw gd;
		public imageClassPanel(GeneDraw gd) 
		{
			super();
			setBackground(Color.white);
			this.gd = gd; 
			addMouseListener(this);
			addMouseMotionListener(this);
			//setBorder(null);
		}
	    public void paintComponent(Graphics g) 
	    {
	        super.paintComponent(g); 
	        gd.drawImage(g);
	    }
	    public void mouseClicked(MouseEvent m){}
	    public void mousePressed(MouseEvent m){}	    
	    public void mouseEntered(MouseEvent m){}	    
	    public void mouseReleased(MouseEvent m){}	    
	    public void mouseExited(MouseEvent m){}	
	    public void mouseDragged(MouseEvent m){}	  
	    public void mouseMoved(MouseEvent m){}	  	    
	}
	/*****************************************
	 * classes
	 */
	private class Trans {
		public Trans (int t, String n, int s, int e) {
			transid = t;
			name = n;
			start = s;
			end = e;
		}
		public void add(int i) {
			exons.add(i);
		}
		String name;
		int start, end, transid;
		Vector <Integer> exons = new Vector <Integer> ();
	}
	private class Variants {
		public Variants(String n, int s, boolean i, int d, boolean isASE) {
			name = n;
			pos = s;
			isSNP = i;
			damage = d;
			if (isASE) ase="*";
		}
		String name, ase="";
		int pos,  damage;
		boolean isSNP, inExon;
	}
	private class Exon {
		public Exon(int s, int e, int f, int nt, int nS, int nI) {
			x=s; y=e; frame=f;
			transList = "" + nt;
			transVec.add(nt);
			cntSNP=nS; cntIndel=nI;
		}
		public void add (Color cc, int i) {
			c = cc;
			idx = i;
		}
		public void add(int nt) {
			transList += "," + nt;
			transVec.add(nt);
		}
		int x, y, frame, cntSNP, cntIndel;
		Color c;
		int idx; // sorted index
		String transList="";
		Vector <Integer> transVec = new Vector <Integer> ();
	}
	/*****************************************/
	Vector <Trans> trList = new Vector <Trans> ();
	Vector <Variants> varList = new Vector <Variants> ();
	Vector <Exon> exonList = new Vector <Exon> ();
	Vector <String> libList = new Vector <String> ();
	String msgExon, msgVariants, msgPval;
	
	private ViewerFrame theViewerFrame;
	private String geneName, geneStrand, chr;
	private int geneIdx, geneStart, geneEnd;
	
	private JButton btnIn, btnOut, btnHome, btnExons, btnSNPs, btnPval;  
	private JPanel ctrlPanel = null;
	private JScrollPane drawScroll = null;
	private imageClassPanel imagePanel = null;
	private int imageHeight=0;
}
