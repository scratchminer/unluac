package unluac.decompile;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Output {

  private OutputProvider out;
  private int indentationLevel = 0;
  private int position = 0;
  private boolean start = true;
  private boolean paragraph = false;
  
  public Output() {
    this(new OutputProvider() {

      @Override
      public void print(String s) {
        System.out.print(s);
      }

      @Override
      public void print(byte b) {
        System.out.write(b);
      }
      
      @Override
      public void println() {
        System.out.println();
      }
      
      @Override
      public void finish() {
        System.out.flush();
      }
      
    });
  }
  
  public Output(OutputProvider out) {
    this.out = out;
  }
  
  public void indent() {
    start = true;
    indentationLevel += 1;
  }
  
  public void dedent() {
    paragraph = false;
    indentationLevel -= 1;
  }
  
  public void paragraph() {
    paragraph = true;
  }
  
  public int getIndentationLevel() {
    return indentationLevel;
  }
  
  public int getPosition() {
    return position;
  }
  
  public void setIndentationLevel(int indentationLevel) {
    this.indentationLevel = indentationLevel;
  }
  
  private void start() {
    if(position == 0) {
      for(int i = indentationLevel; i != 0; i--) {
        out.print("\t");
        position++;
      }
      if(paragraph && !start) {
        paragraph = false;
        out.println();
        position = 0;
        start();
      }
    }
    start = false;
  }
  
  public void print(String s) {
    start();
    ByteBuffer bb = Charset.forName("UTF-8").encode(s);
    for(int i = 0; i < bb.limit(); i++) {
      out.print(bb.get(i));
    }
    position += s.length();
  }
  
  public void print(byte b) {
    start();
    out.print(b);
    position += 1;
  }
  
  public void println() {
    start();
    out.println();
    position = 0;
  }
  
  public void println(String s) {
    print(s);
    println();
  }
  
  public void finish() {
    out.finish();
  }
  
}
