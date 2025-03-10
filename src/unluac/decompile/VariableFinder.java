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
      read_count = 0;
      func_ref = -1;
      for_value = -1;
      func = false;
      temporary = false;
      local = false;
      read = false;
      written = false;
    }
    
    int last_written;
    int last_read;
    int read_count;
    int func_ref;
    int for_value;
    
    boolean func;
    boolean temporary;
    boolean local;
    boolean read;
    boolean written;
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
    
    public void setWritten(int register, int line) {
      get(register, line).written = true;
      get(register, line).last_written = line;
      get(register, line).read_count = 0;
      get(register, line).func_ref = -1;
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
      for(int r = register_min; r <= register_max; r++) {
        get(r, line).temporary = true;
      }
    }
    
    public void nextLine(int line) {
      if(line > 1) {
        for(int r = 0; r < registers; r++) {
          get(r, line).last_written = get(r, line - 1).last_written;
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
          states.setRead(B, line);
          states.setWritten(A, line);
          break;
        case NEWTABLE54:
        case LOADI:
        case LOADF:
        case LOADK:
        case LOADKX:
        case LOADBOOL:
        case LOADFALSE:
        case LFALSESKIP:
        case LOADTRUE:
          states.setWritten(A, line);
          break;
        case LOADNIL52: {
          for (int register = A; register <= A + B; register++) {
            states.setWritten(register, line);
          }
          break;
        }
        case GETI:
          states.setRead(B, line);
          states.setWritten(A, line);
          break;
        case GETUPVAL:
        case GETTABUP54:
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          states.setWritten(A, line);
          break;
        case GETTABLE54:
          states.setRead(B, line);
          states.setRead(C, line);
          states.setWritten(A, line);
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
          states.setRead(B, line);
          states.setWritten(A, line);
          break;
        case SETUPVAL:
        case RETURN1:
          states.setRead(A, line);
          break;
        case GETFIELD:
          states.setRead(B, line);
          states.setWritten(A, line);
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case SETI:
        case SETTABUP54:
        case SETFIELD:
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          break;
        case SETTABLE54:
          states.setRead(A, line);
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
          states.setRead(B, line);
          states.setRead(C, line);
          states.setWritten(A, line);
          break;
        case SELF54:
          if(!isConstantReference(d, code.C(line))) states.setRead(C, line);
          states.setRead(B, line);
          states.setWritten(A, line);
          states.setWritten(A + 1, line);
          break;
        case UNM:
        case BNOT:
        case NOT:
        case LEN:
          states.setRead(B, line);
          states.setWritten(A, line);
          break;
        case CONCAT54: {
          for(int register = A; register < A + B; register++) {
            states.setRead(register, line);
          }
          states.setWritten(A, line);
          break;
        }
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
          states.setRead(B, line);
          states.setWritten(A, line);
          break;
        case CLOSURE: {
          LFunction f = d.function.functions[code.Bx(line)];
          for(LUpvalue upvalue : f.upvalues) {
            if(upvalue.instack) {
              states.get(upvalue.idx, states.get(upvalue.idx, line).last_written).local = true;
            }
          }
          states.setWritten(A, line);
          states.get(A, line).func = true;
          break;
        }
        case CALL:
        case TAILCALL54: {
          for(int register = A + 1; register <= A + B - 1; register++) {
            states.setRead(register, line);
          }
          if(code.op(line) != Op.TAILCALL54) {
            if(C >= 2) {
              for(int register = A; register <= A + C - 2; register++) {
                states.setWritten(register, line);
              }
            }
          }
          if(C >= 2) {
            int nline = line + 1;
            int register = A + C - 2;
            while(register >= A && nline <= code.length()) {
              if(code.op(nline) == Op.MOVE && code.B(nline) == register) {
                states.setWritten(code.A(nline), nline);
                states.setRead(code.B(nline), nline);
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
            states.setRead(register, line);
          }
          break;
        }
        case FORLOOP54: {
          // set the read count to at least 2
          states.setRead(A + 3, line);
          states.setRead(A + 3, line);
          break;
        }
        case FORPREP54:
          states.setWritten(A, line);
          states.setWritten(A + 1, line);
          states.setWritten(A + 2, line);
          states.setWritten(A + 3, line);
          break;
        case TFORCALL54: {
          states.setRead(A + 1, line);
          for (int register = A + 4; register <= A + 3 + C; register++) {
            states.setWritten(register, line);
          }
          break;
        }
        default:
          break;
      }
    }/*
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
          }
          for(int ancestor : ancestors) {
            if(pline >= 1) {
              states.setRead(ancestor, pline);
            }
          }
        }
      }
    }*/
    /*for(int register = 0; register < registers; register++) {
      for(int line = 1; line <= code.length(); line++) {
        RegisterState s = states.get(register, line);
        if(s.written) {
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
      
      List<Integer> starts = new ArrayList<Integer>();
      List<Integer> ends = new ArrayList<Integer>();
      List<Boolean> locals = new ArrayList<Boolean>();
      
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
      
      for(int line = 1; line <= code.length(); line++) {
        RegisterState state = states.get(register, line);
        
        if(states.get(register, state.last_written).temporary) continue;
        
        if(state.written) {
          if(state.local || state.read_count > 1) {
            locals.add(true);
            starts.add(line);
            ends.add(code.length());
          }
        }
      }
      
      String name;
      int delimCounter = 0;
      
      for(int i = 0; i < locals.size(); i++) {
        if(is_arg) {
          name = "arg";
        } else {
          name = id + register + "_" + lc++;
        }
        
        Declaration decl2 = new Declaration(name, starts.get(i), ends.get(i) + d.getVersion().outerblockscopeadjustment.get());
        decl2.register = register;
        declList.add(decl2);
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