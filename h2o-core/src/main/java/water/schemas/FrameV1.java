package water.schemas;

import water.*;
import water.api.Handler;
import water.api.RequestServer;
import water.fvec.*;
import water.util.DocGen.HTML;

// Private Schema class for the Inspect handler.  Not a registered Schema.
public class FrameV1 extends Schema {

  // Input fields
  @API(help="Key to inspect",validation="/*this input is required*/")
  Key key;

  @API(help="Row offset to display")
  long off;

  @API(help="Number of rows to display")
  int len;

  // Output fields
  @API(help="Columns")
  Col[] columns;

  // Output fields one-per-column
  private static class Col extends Iced {
    @API(help="label")
    final String label;

    @API(help="missing")
    final long missing;

    @API(help="min")
    final double min;

    @API(help="max")
    final double max;

    @API(help="mean")
    final double mean;

    @API(help="sigma")
    final double sigma;

    @API(help="datatype: {time, enum, int, real}")
    final String type;

    @API(help="domain; not-null for enum columns only")
    final String[] domain;

    @API(help="data")
    final double[] data;

    transient Vec _vec;

    Col( String name, Vec vec, long off, int len ) {
      label=name;
      missing = vec.naCnt();
      min = vec.min();
      max = vec.max();
      mean = vec.mean();
      sigma = vec.sigma();
      type = vec.isEnum() ? "enum" : (vec.isTime() ? "time" : (vec.isInt() ? "int" : "real"));
      domain = vec.domain();
      len = (int)Math.min(len,vec.length()-off);
      data = MemoryManager.malloc8d(len);
      for( int i=0; i<len; i++ )
        data[i] = vec.at(off+i);
      _vec = vec;               // Better HTML display, not in the JSON
    }
  }

  // Constructor for when called from the Inspect handler instead of RequestServer
  transient Frame _fr;         // Avoid an racey update to Key; cached loaded value
  public FrameV1( Frame fr ) { key = fr._key;  _fr = fr; }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public FrameV1 fillInto( Handler h ) { throw H2O.fail(); }

  // Version&Schema-specific filling from the handler
  @Override public FrameV1 fillFrom( Handler h ) {
    off = 0;
    len = 100;                  // comes from Frame??? TODO
    columns = new Col[_fr.numCols()];
    Vec[] vecs = _fr.vecs();
    for( int i=0; i<columns.length; i++ )
      columns[i] = new Col(_fr._names[i],vecs[i],off,len);
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    String[] urls = RequestServer.frameChoices(getVersion(),_fr);
    for( String url : urls )
      ab.href("hex",url,url);

    // Main data display
    // Column names
    String titles[] = new String[_fr._names.length+1];
    titles[0]="";
    System.arraycopy(_fr._names,0,titles,1,_fr._names.length);
    ab.arrayHead(titles);

    // Optional missing-element line
    boolean has_miss=false;
    for( Col c : columns ) if( c.missing > 0 ) { has_miss=true; break; }
    if( has_miss )
      formatRow(ab,"class='warning'","missing",new ColOp() { String op(Col c) { return c.missing==0?"":Long.toString(c.missing); } } );

    // Rollup data
    formatRow(ab,"","type" ,new ColOp() { String op(Col c) { return c.type; } } );
    formatRow(ab,"","min"  ,new ColOp() { String op(Col c) { return formatCell(c.domain==null ? c.min  :Double.NaN,c); } } );
    formatRow(ab,"","max"  ,new ColOp() { String op(Col c) { return formatCell(c.domain==null ? c.max  :Double.NaN,c); } } );
    formatRow(ab,"","mean" ,new ColOp() { String op(Col c) { return formatCell(c.domain==null ? c.mean :Double.NaN,c); } } );
    formatRow(ab,"","sigma",new ColOp() { String op(Col c) { return formatCell(c.domain==null ? c.sigma:Double.NaN,c); } } );

    // enums
    ab.p("<tr>").cell("levels");
    for( Col c : columns )
      ab.cell(c.domain==null?"":Integer.toString(c.domain.length));
    ab.p("</tr>");

    // Frame data
    int len = columns.length > 0 ? columns[0].data.length : 0;
    for( int i=0; i<len; i++ ) {
      final int row = i;
      formatRow(ab,"",Integer.toString(row+1),new ColOp() { String op(Col c) { return formatCell(c.data[row],c); } } );
    }

    ab.arrayTail();

    return ab.bodyTail();
  }

  private abstract static class ColOp { abstract String op(Col v); }

  private void formatRow( HTML ab, String color, String msg, ColOp vop ) {
    ab.p("<tr").p(color).p(">");
    ab.cell(msg);
    for( Col c : columns )  ab.cell(vop.op(c));
    ab.p("</tr>");
  }

  private String formatCell( double d, Col c ) {
    if( Double.isNaN(d) ) return "-";
    if( c.domain!=null ) return c.domain[(int)d];

    Chunk chk = c._vec.chunkForRow(off);
    Class Cc = chk._vec.chunkForRow(off).getClass();
    if( Cc == C1SChunk.class ) return x2(d,((C1SChunk)chk)._scale);
    if( Cc == C2SChunk.class ) return x2(d,((C2SChunk)chk)._scale);
    if( Cc == C4SChunk.class ) return x2(d,((C4SChunk)chk)._scale);
    long l = (long)d;
    return (double)l == d ? Long.toString(l) : Double.toString(d);
  }

  private static String x2( double d, double scale ) {
    String s = Double.toString(d);
    // Double math roundoff error means sometimes we get very long trailing
    // strings of junk 0's with 1 digit at the end... when we *know* the data
    // has only "scale" digits.  Chop back to actual digits
    int ex = (int)Math.log10(scale);
    int x = s.indexOf('.');
    int y = x+1+(-ex);
    if( x != -1 && y < s.length() ) s = s.substring(0,x+1+(-ex));
    while( s.charAt(s.length()-1)=='0' )
      s = s.substring(0,s.length()-1);
    return s;
  }

}
