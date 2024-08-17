package unluac.parse;

public class LNil extends LObject{

  public static final LNil NIL = new LNil();
  
  private LNil() {
    
  }
 
  @Override
  public String toPrintString(int flags) {
    return "nil";
  }
  
  @Override
  public boolean equals(Object o) {
    return this == o;
  }
  
}
