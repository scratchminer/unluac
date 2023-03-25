package unluac.decompile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import unluac.parse.LFunction;
import unluac.parse.LUpvalue;

public class VariableFinder {

  static class RegisterState {
    
    public RegisterState() {
      last_written = 1;
      last_read = -1;
      last_displayOverride = -1;
      read_count = 0;
      temporary = false;
      local = false;
      read = false;
      written = false;
      displayOverride = false;
      isFunc = false;
    }
    
    int last_written;
    int last_read;
    int last_displayOverride;
    int read_count;
    boolean temporary;
    boolean local;
    boolean read;
    boolean written;
    boolean displayOverride;
    boolean isFunc;
  }
  
  static class RegisterStates {
    
    @SuppressWarnings("unchecked")
    RegisterStates(int registers, int lines) {
      this.registers = registers;
      this.lines = lines;
      states = (ArrayList<RegisterState>[])(new ArrayList[lines]);
      for(int line = 0; line < lines; line++) {
        states[line] = new ArrayList<RegisterState>();
        for(int register = 0; register < registers; register++) {
          states[line].add(new RegisterState());
        }
      }
    }
    
    public RegisterState get(int register, int line) {
      return states[line - 1].get(register);
    }
    
    public void makeSureDisplays(int register, int line) {
      get(register, line).displayOverride = true;
      get(register, line).last_displayOverride = line;
    }
    
    public void setWritten(int register, int line) {
      get(register, line).isFunc = false;
      get(register, line).written = true;
      get(register, line + 1).last_written = line;
      
      get(register, line).displayOverride = false;
      get(register, line).last_displayOverride = -1;
    }
    
    public void setRead(int register, int line) {
      get(register, line).read = true;
      get(register, get(register, line).last_written).read_count++;
      get(register, get(register, line).last_written).last_read = line;
    }
    
    public void setLocalRead(int register, int line) {
      for(int r = 0; r <= register; r++) {
        get(r, get(r, line).last_written).local = true;
      }
    }
    
    public void setLocalWrite(int register_min, int register_max, int line) {
      for(int r = 0; r < register_min; r++) {
        get(r, get(r, line).last_written).local = true;
      }
      for(int r = register_min; r <= register_max; r++) {
        get(r, line).local = true;
      }
    }
    
    public void setTemporaryRead(int register, int line) {
      for(int r = register; r < registers; r++) {
        get(r, get(r, line).last_written).temporary = true;
      }
    }
    
    public void setTemporaryWrite(int register_min, int register_max, int line) {
      for(int r = register_max + 1; r < registers; r++) {
        get(r, get(r, line).last_written).temporary = true;
      }
      for(int r = register_min; r <= register_max; r++) {
        get(r, line).temporary = true;
      }
    }
    
    public void nextLine(int line) {
      if(line + 1 < lines) {
        for(int r = 0; r < registers; r++) {
          if(get(r, line).last_written > get(r, line + 1).last_written) {
            get(r, line + 1).last_written = get(r, line).last_written;
          }
          
          if(get(r, line).last_displayOverride > get(r, line + 1).last_displayOverride) {
            get(r, line + 1).last_displayOverride = get(r, line).last_displayOverride;
          }
          
          if(get(r, line).displayOverride) {
            get(r, line + 1).displayOverride = true;
          }
          
          if(get(r, line).isFunc) {
            get(r, line + 1).isFunc = true;
          }
        }
      }
    }
    
    private int registers;
    private int lines;
    private List<RegisterState>[] states;
  }
  
  private static boolean isConstantReference(Decompiler d, int value) {
    return d.function.header.extractor.is_k(value);
  }
  
  public static Declaration[] process(Decompiler d, int args, int registers, Declaration[] inputArray) {
    //System.out.println("processing locals");
    
    List<Declaration> declList = new ArrayList<Declaration>(Arrays.asList(process(d, args, registers)));
    
    for(int register = 0; register < inputArray.length; register++) {
      int target = inputArray[register].register;
      boolean match = false;
      for(int register2 = 0; register2 < declList.size(); register2++) {
        if(declList.get(register).register == target) {
          declList.get(register).name = inputArray[register].name;
          match = true;
        }
      }
      if(!match) {
        declList.add(inputArray[register]);
      }
    }
    
    return declList.toArray(new Declaration[declList.size()]);
  }
  
  public static Declaration[] process(Decompiler d, int args, int registers) {
    Code code = d.code;
    RegisterStates states = new RegisterStates(registers, code.length());
    boolean[] skip = new boolean[code.length()];
    
    for(int line = 1; line <= code.length(); line++) {
      states.nextLine(line);
      if(skip[line - 1]) continue;
      int A = code.A(line);
      int B = code.B(line);
      int C = code.C(line);
      switch(code.op(line)) {
        case MOVE:
          states.setWritten(A, line);
          states.setRead(B, line);/*
          if(A < B) {
            states.setLocalWrite(A, A, line);
          } else if(B < A) {
            states.setLocalRead(B, line);
          }*/
          break;
        case NEWTABLE54: {
          states.setLocalWrite(A, A, line);
          break;
        }
        case LOADNIL52: {
          states.setTemporaryWrite(A, A + B, line);
          break;
        }
        case GETI:
          states.setWritten(A, line);
          states.setRead(B, line);
          break;
        case GETUPVAL:
        case GETTABUP54:
          states.setWritten(A, line);
          states.setTemporaryWrite(A, A, line);
          break;
        case GETTABLE54:
          states.setWritten(A, line);
          states.setRead(B, line);
          states.setRead(C, line);
          break;
        case ADDI:
        case ADDK:
        case SUBK:
        case MULK:
        case DIVK:
        case MODK:
        case POWK:
        case IDIVK:
        case BANDK:
        case BORK:
        case BXORK:
        case SHRI:
        case SHLI:
          states.setWritten(A, line);
          states.setRead(B, line);
          break;
        case SETUPVAL:
        case RETURN1:
          states.setRead(A, line);
          break;
        case SETI:
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case GETFIELD:
          states.setWritten(A, line);
          states.setRead(B, line);
          states.setTemporaryRead(B, line);
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case SETTABUP54:
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case SETFIELD:
          states.setWritten(A, line);
          //states.setTemporaryRead(A, line);
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case SETTABLE54:
          states.setRead(B, line);
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case ADD54:
        case SUB54:
        case MUL54:
        case DIV54:
        case IDIV54:
        case MOD54:
        case POW54:
        case BAND54:
        case BOR54:
        case BXOR54:
        case SHR54:
        case SHL54:
          states.setWritten(A, line);
          states.setRead(B, line);
          states.setRead(C, line);
          break;
        case SELF54:
          states.setWritten(A, line);
          states.setWritten(A + 1, line);
          states.setRead(B, line);
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case UNM:
        case BNOT:
        case NOT:
        case LEN:
          states.setWritten(A, line);
          states.get(code.B(line), line).read = true;
          break;
        case CONCAT54:
          for(int register = A; register < A + B; register++) {
            states.setRead(register, line);
            states.setTemporaryRead(register, line);
          }
          break;
        case SETLIST54: {
          for(int register = A + 1; register <= B; register++) {
            states.setRead(register, line);
          }
          states.setWritten(A, line);
          break;
        }
        case EQ54:
        case LT54:
        case LE54:
          states.setRead(A, line);
          states.setRead(B, line);
          break;
        case EQK:
        case EQI:
        case LTI:
        case LEI:
        case GTI:
        case GEI:
          states.setRead(A, line);
          break;
        case TESTSET54:
          states.setWritten(A, line);
          states.setLocalWrite(A, A, line);
          states.setRead(B, line);
          break;
        case CLOSURE: {
          LFunction f = d.function.functions[code.Bx(line)];
          for(LUpvalue upvalue : f.upvalues) {
            if(upvalue.instack) {
              states.setLocalRead(upvalue.idx, line);
            }
          }
          states.setWritten(A, line);
          break;
        }
        case CALL:
        case TAILCALL54: {
          for(int i = states.get(A, line).last_written; i <= line; i++)
            states.get(A, i).isFunc = true;
          
          if(code.op(line) != Op.TAILCALL54) {
            if(C >= 2) {
              for(int register = A; register <= A + C - 2; register++) {
                states.setWritten(register, line);
              }
            }
          }
          for(int register = A; register <= A + B - 1; register++) {
            states.setRead(register, line);
            states.setTemporaryRead(register, line);
          }
          if(C >= 2) {
            int nline = line + 1;
            int register = A + C - 2;
            while(register >= A && nline <= code.length()) {
              if(code.op(nline) == Op.MOVE && code.B(nline) == register) {
                states.setWritten(code.A(nline), nline);
                states.setRead(code.B(nline), nline);
                states.setLocalWrite(code.A(nline), code.A(nline), nline);
                skip[nline - 1] = true;
              }
              register--;
              nline++;
            }
          }
          
          break;
        }
        case RETURN54: {
          if(B == 0) B = registers - code.A(line) + 1;
          for(int register = A; register <= A + B - 2; register++) {
            states.get(register, line).read = true;
          }
          break;
        }
        default:
          break;
      }
    }
    for(int line = 1; line <= code.length(); line++) {
      for(int register = 0; register < registers; register++) {
        RegisterState s = states.get(register, line);
        if(s.written && s.temporary) {
          List<Integer> ancestors = new ArrayList<Integer>();
          for(int read = 0; read < registers; read++) {
            RegisterState r = states.get(read, line);
            if(r.read && !r.local) {
              ancestors.add(read);
            }
          }
          int pline;
          for(pline = line - 1; pline >= 1; pline--) {
            boolean any_written = false;
            for(int pregister = 0; pregister < registers; pregister++) {
              if(states.get(pregister, pline).written && ancestors.contains(pregister)) {
                any_written = true;
                ancestors.remove((Object)pregister);
              }
            }
            if(!any_written) {
              break;
            }
            for(int pregister = 0; pregister < registers; pregister++) {
              RegisterState a = states.get(pregister, pline); 
              if(a.read && !a.local) {
                ancestors.add(pregister);
              }
            }
          }
          for(int ancestor : ancestors) {
            if(pline >= 1) {
              states.setRead(ancestor, pline);
            }
          }
        }
      }
    }/*
    for(int register = 0; register < registers; register++) {
      for(int line = 1; line <= code.length(); line++) {
        RegisterState s = states.get(register, line);
        if(s.written || line == 1) {
          System.out.println("WRITE r:" + register + " l:" + line + " .. " + s.last_read);
          if(s.local) System.out.println("  LOCAL");
          if(s.temporary) System.out.println("  TEMPORARY");
          System.out.println("  READ_COUNT " + s.read_count);
        }
      }
    }
    //*/
    List<Declaration> declList = new ArrayList<Declaration>(registers); 
    for(int register = 0; register < registers; register++) {
      String id = "L";
      boolean local = false;
      boolean temporary = false;
      int masterOverride = -1;
      
      int read = 0;
      int written = 0;
      
      List<Integer> starts = new ArrayList<Integer>();
      List<Integer> ends = new ArrayList<Integer>();
      List<Boolean> locals = new ArrayList<Boolean>();
      List<Boolean> temps = new ArrayList<Boolean>();
      
      if(register < args) {
        local = true;
        id = "A";
      }
      boolean is_arg = false;
      if(register == args) {
        switch(d.getVersion().varargtype.get()) {
        case ARG:
        case HYBRID:
          if((d.function.vararg & 1) != 0) {
            local = true;
            is_arg = true;
          }
          break;
        case ELLIPSIS:
          break;
        }
      }
      
      if(!local && !temporary) {
        boolean prevOverride = false;
        
        for(int line = 1; line <= code.length(); line++) {
          RegisterState state = states.get(register, line);
          
          if(state.displayOverride && !prevOverride) {
            temporary = false;
            local = true;
            locals.add(local);
            temps.add(temporary);
            starts.add(written == 0 ? 1 : line);
            ends.add(state.last_read);
            
            prevOverride = true;
          }
          if(state.displayOverride) {
            continue;
          }
          
          prevOverride = false;
          
          if(state.temporary || state.isFunc) {
            temporary = true;
          }
          
          if(state.read) {
            written = 0; read++;
          }
          
          if(state.written) {
            if(written == 0) {
              if(read != 0 && state.read_count >= 2 && d.function.parent != null && !state.isFunc) {
                temporary = false;
                local = true;
              }
              else if(read != 0 && state.read_count >= 2 && d.function.parent == null && !state.isFunc) {
                temporary = false;
                local = true;
              }
              
              locals.add(local);
              temps.add(temporary);
              
              if(state.isFunc) starts.add(line);
              else starts.add(1);
              ends.add(state.last_read);
            }
            
            read = 0; written++;
          }
        }
      }
      
      if(!local) {
        for(int i = 0; i < d.function.functions.length; i++) {
          LFunction f = d.function.functions[i];
          for(LUpvalue upvalue : f.upvalues) {
            if(upvalue.idx == register) {
              local = true;
              temporary = false;
              locals.add(local);
              temps.add(temporary);
              starts.add(1);
              ends.add(code.length());
            }
          }
        }
      }
      
      if(!local && !temporary) {
        if(d.function.parent != null && read >= 2 || read == 0 && written > 0) {
          local = true;
          
          locals.add(local);
          temps.add(false);
          starts.add(1);
          ends.add(code.length());
        }
      }
      
      String name;
      int delimCounter = 0;
      
      for(int i = 0; i < locals.size(); i++) {
        if(locals.get(i) && !temps.get(i)) {
          if(is_arg) {
            name = "arg";
          } else {
            name = id + register + "_" + lc++;
          }
          
          Declaration decl2 = new Declaration(name, starts.get(i), code.length() + d.getVersion().outerblockscopeadjustment.get());
          decl2.register = register;
          declList.add(decl2);
        }
      }
      
      if(locals.size() == 0) {
        if(register < args) {
          name = id + register + "_" + lc++;
          
          Declaration decl = new Declaration(name, 0, code.length() + d.getVersion().outerblockscopeadjustment.get());
          decl.register = register;
          declList.add(decl);
        }
        else if(masterOverride >= 0) {
          name = id + register + "_" + lc++;
          
          Declaration decl = new Declaration(name, masterOverride, code.length() + d.getVersion().outerblockscopeadjustment.get());
          decl.register = register;
          declList.add(decl);
        }
      }
    }
    
    return declList.toArray(new Declaration[declList.size()]);
  }
  
  static int lc = 0;
  
  private VariableFinder() {}
  
}
