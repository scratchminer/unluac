package unluac.decompile;

import unluac.Version;
import unluac.parse.LBoolean;
import unluac.parse.LNil;
import unluac.parse.LNumber;
import unluac.parse.LObject;
import unluac.parse.LString;

public class Constant {

  private static enum Type {
    NIL,
    BOOLEAN,
    NUMBER,
    STRING,
  }
  
  private final Type type;
  
  private final boolean bool;
  private final LNumber number;
  private final String string;
  
  public Constant(int constant) {
    type = Type.NUMBER;
    bool = false;
    number = LNumber.makeInteger(constant);
    string = null;
  }
  
  public Constant(double x) {
    type = Type.NUMBER;
    bool = false;
    number = LNumber.makeDouble(x);
    string = null;
  }
  
  public Constant(LObject constant) {
    if(constant instanceof LNil) {
      type = Type.NIL;
      bool = false;
      number = null;
      string = null;
    } else if(constant instanceof LBoolean) {
      type = Type.BOOLEAN;
      bool = constant == LBoolean.LTRUE;
      number = null;
      string = null;
    } else if(constant instanceof LNumber) {
      type = Type.NUMBER;
      bool = false;
      number = (LNumber) constant;
      string = null;
    } else if(constant instanceof LString) {
      type = Type.STRING;
      bool = false;
      number = null;
      string = ((LString) constant).deref();
    } else {
      throw new IllegalArgumentException("Illegal constant type: " + constant.toString());
    }
  }
  
  public void print(Decompiler d, Output out, boolean braced) {
    switch(type) {
      case NIL:
        out.print("nil");
        break;
      case BOOLEAN:
        out.print(bool ? "true" : "false");
        break;
      case NUMBER:
        out.print(number.toPrintString(0));
        break;
      case STRING:
        int newlines = 0;
        int unprintable = 0;
        boolean rawstring = d.getConfiguration().rawstring;
        for(int i = 0; i < string.length(); i++) {
          char c = string.charAt(i);
          if(c == '\n') {
            newlines++;
          } else if((c <= 31 && c != '\t') || c >= 127) {
            unprintable++;
          }
        }
        boolean longString = (newlines > 1 || (newlines == 1 && string.indexOf('\n') != string.length() - 1)); // heuristic
        longString = longString && unprintable == 0; // can't escape and for robustness, don't want to allow non-ASCII output
        longString = longString && !string.contains("[["); // triggers compatibility error in 5.1 TODO: avoidable?
        if(d.function.header.version.usenestinglongstrings.get()) {
          longString = longString && !string.contains("]]") && !string.endsWith("]"); // no piping TODO: allow proper nesting
        }
        if(longString) {
          int pipe = 0;
          String pipeString = "]]";
          String startPipeString = "]";
          while(string.endsWith(startPipeString) || string.indexOf(pipeString) >= 0) {
            pipe++;
            pipeString = "]";
            int i = pipe;
            while(i-- > 0) pipeString += "=";
            startPipeString = pipeString;
            pipeString += "]";
          }
          if(braced) out.print("(");
          out.print("[");
          while(pipe-- > 0) out.print("=");
          out.print("[");
          int indent = out.getIndentationLevel();
          out.setIndentationLevel(0);
          out.println();
          out.print(string);
          out.print(pipeString);
          if(braced) out.print(")");
          out.setIndentationLevel(indent);
        } else {
          out.print("\"");
          for(int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if(c <= 31 || c >= 127) {
              if(c == 7) {
                out.print("\\a");
              } else if(c == 8) {
                out.print("\\b");
              } else if(c == 12) {
                out.print("\\f");
              } else if(c == 10) {
                out.print("\\n");
              } else if(c == 13) {
                out.print("\\r");
              } else if(c == 9) {
                out.print("\\t");
              } else if(c == 11) {
                out.print("\\v");
              } else if(!rawstring || c <= 127) {
                String dec = Integer.toString(c);
                int len = dec.length();
                out.print("\\");
                while(len++ < 3) {
                  out.print("0");
                }
                out.print(dec);
              } else {
                out.print((byte)c);
              }
            } else if(c == 34) {
              out.print("\\\"");
            } else if(c == 92) {
              out.print("\\\\");
            } else {
              out.print(Character.toString(c));
            }
          }
          out.print("\"");
        }
        break;
      default:
        throw new IllegalStateException();
    }
  }
  
  public boolean isNil() {
    return type == Type.NIL;
  }
  
  public boolean isBoolean() {
    return type == Type.BOOLEAN;
  }
  
  public boolean isNumber() {
    return type == Type.NUMBER;
  }
  
  public boolean isInteger() {
    return number.value() == Math.round(number.value());
  }
  
  public boolean isNegative() {
    // Tricky to catch -0.0 here
    return String.valueOf(number.value()).startsWith("-");
  }
  
  public int asInteger() {
    if(!isInteger()) {
      throw new IllegalStateException();
    }
    return (int) number.value();
  }
  
  public boolean isString() {
    return type == Type.STRING;
  }
  
  public boolean isIdentifierPermissive(Version version) {
    if(!isString() || version.isReserved(string)) {
      return false;
    }
    if(string.length() == 0) {
      return false;
    }
    char start = string.charAt(0);
    if(Character.isDigit(start) && start != ' ' && !Character.isLetter(start)) {
      return false;
    }
    return true;
  }
  
  public boolean isIdentifier(Version version) {
    if(!isIdentifierPermissive(version)) {
      return false;
    }
    char start = string.charAt(0);
    if(start != '_' && !Character.isLetter(start)) {
      return false;
    }
    for(int i = 1; i < string.length(); i++) {
      char next = string.charAt(i);
      if(Character.isLetter(next)) {
        continue;
      }
      if(Character.isDigit(next)) {
        continue;
      }
      if(next == '_') {
        continue;
      }
      return false;
    }
    return true;
  }
  
  public String asName() {
    if(type != Type.STRING) {
      throw new IllegalStateException();
    }
    return string;
  }
  
}
