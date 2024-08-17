package unluac.parse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import unluac.Version;
import unluac.assemble.Directive;
import unluac.decompile.CodeExtract;
import unluac.decompile.Op;


abstract public class LFunctionType extends BObjectType<LFunction> {
  
  public static LFunctionType get(Version.FunctionType type) {
    switch(type) {
      case LUA50: return new LFunctionType50();
      case LUA51: return new LFunctionType51();
      case LUA52: return new LFunctionType52();
      case LUA53: return new LFunctionType53();
      case LUA54: return new LFunctionType54();
      default: throw new IllegalStateException();
    }
  }
  
  protected static class LFunctionParseState {
    
    public LString name;
    int lineBegin;
    int lineEnd;
    int lenUpvalues;
    int lenParameter;
    int vararg;
    int maximumStackSize;
    int length;
    int[] code;
    BList<LObject> constants;
    BList<LFunction> functions;
    BList<BInteger> lines;
    BList<LAbsLineInfo> abslineinfo;
    BList<LLocal> locals;
    LUpvalue upvalues[];
  }
  
  @Override
  public LFunction parse(ByteBuffer buffer, BHeader header) {
    if(header.debug) {
      System.out.println("-- beginning to parse function");
    }
    if(header.debug) {
      System.out.println("-- parsing name...start...end...upvalues...params...varargs...stack");
    }
    LFunctionParseState s = new LFunctionParseState();
    parse_main(buffer, header, s);
    int[] lines = new int[s.lines.length.asInt()];
    for(int i = 0; i < lines.length; i++) {
      lines[i] = s.lines.get(i).asInt();
    }
    LAbsLineInfo[] abslineinfo = null;
    if(s.abslineinfo != null) {
      abslineinfo = s.abslineinfo.asArray(new LAbsLineInfo[s.abslineinfo.length.asInt()]);
    }
    LFunction lfunc = new LFunction(header, s.name, s.lineBegin, s.lineEnd, s.code, lines, abslineinfo, s.locals.asArray(new LLocal[Math.max(0, s.locals.length.asInt())]), s.constants.asArray(new LObject[Math.max(0, s.constants.length.asInt())]), s.upvalues, s.functions.asArray(new LFunction[Math.max(0, s.functions.length.asInt())]), s.maximumStackSize, s.lenUpvalues, s.lenParameter, s.vararg);
    for(LFunction child : lfunc.functions) {
      child.parent = lfunc;
    }
    if(s.lines.length.asInt() == 0 && s.locals.length.asInt() == 0) {
      lfunc.stripped = true;
    }
    return lfunc;
  }
  
  abstract public List<Directive> get_directives();
  
  abstract protected void parse_main(ByteBuffer buffer, BHeader header, LFunctionParseState s);
  
  protected void parse_code(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    if(header.debug) {
      System.out.println("-- beginning to parse bytecode list");
    }
    s.length = header.integer.parse(buffer, header).asInt();
    s.code = new int[s.length];
    for(int i = 0; i < s.length; i++) {
      s.code[i] = buffer.getInt();
      if(header.debug) {
        int codepoint = s.code[i];
        CodeExtract ex = header.extractor;
        Op op = header.opmap.get(ex.op.extract(codepoint));
        System.out.println("-- parsed codepoint " + Integer.toHexString(codepoint));
        if(op != null) {
          System.out.println("-- " + op.codePointToString(0, null, codepoint, ex, null, false));
        } else {
          System.out.println("-- " + Op.defaultToString(0, null, codepoint, header.version, ex, false));
        }
      }
    }
  }
  
  protected void write_code(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.integer.write(out, header, new BInteger(object.code.length));
    for(int i = 0; i < object.code.length; i++) {
      int codepoint = object.code[i];
      if(header.lheader.endianness == LHeader.LEndianness.LITTLE) {
        out.write((byte)(0xFF & (codepoint)));
        out.write((byte)(0xFF & (codepoint >> 8)));
        out.write((byte)(0xFF & (codepoint >> 16)));
        out.write((byte)(0xFF & (codepoint >> 24)));
      } else {
        out.write((byte)(0xFF & (codepoint >> 24)));
        out.write((byte)(0xFF & (codepoint >> 16)));
        out.write((byte)(0xFF & (codepoint >> 8)));
        out.write((byte)(0xFF & (codepoint)));
      }
    }
  }
  
  protected void parse_constants(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    if(header.debug) {
      System.out.println("-- beginning to parse constants list");
    }
    s.constants = header.constant.parseList(buffer, header);
    if(header.debug) {
      System.out.println("-- beginning to parse functions list");
    }
    s.functions = header.function.parseList(buffer, header);
  }
  
  protected void write_constants(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.constant.writeList(out, header, object.constants);
    header.function.writeList(out, header, object.functions);
  }
  
  protected void create_upvalues(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    s.upvalues = new LUpvalue[s.lenUpvalues];
    for(int i = 0; i < s.lenUpvalues; i++) {
      s.upvalues[i] = new LUpvalue();
    }
  }
  
  protected void parse_upvalues(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    BList<LUpvalue> upvalues = header.upvalue.parseList(buffer, header);
    s.lenUpvalues = upvalues.length.asInt();
    s.upvalues = upvalues.asArray(new LUpvalue[s.lenUpvalues]);
  }
  
  protected void write_upvalues(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.upvalue.writeList(out, header, object.upvalues);
  }
  
  protected void parse_debug(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    if(header.debug) {
      System.out.println("-- beginning to parse source lines list");
    }
    s.lines = header.integer.parseList(buffer, header);
    if(header.debug) {
      System.out.println("-- beginning to parse locals list");
    }
    s.locals = header.local.parseList(buffer, header, header.version.locallengthmode.get());
    parse_upvalue_names(buffer, header, s);
  }
  
  protected void parse_upvalue_names(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    if(header.debug) {
      System.out.println("-- beginning to parse upvalue names list");
    }
    BList<LString> upvalueNames = header.string.parseList(buffer, header, header.version.upvaluelengthmode.get(), new BInteger(s.lenUpvalues));
    for(int i = 0; i < Math.min(s.upvalues.length, upvalueNames.length.asInt()); i++) {
      s.upvalues[i].bname = upvalueNames.get(i);
      s.upvalues[i].name = s.upvalues[i].bname.deref();
    }
  }
  
  protected void write_debug(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.integer.write(out, header, new BInteger(object.lines.length));
    for(int i = 0; i < object.lines.length; i++) {
      header.integer.write(out, header, new BInteger(object.lines[i]));
    }
    header.local.writeList(out, header, object.locals);
    int upvalueNameLength = 0;
    for(LUpvalue upvalue : object.upvalues) {
      if(upvalue.bname != null && upvalue.bname != LString.NULL) {
        upvalueNameLength++;
      } else {
        break;
      }
    }
    header.integer.write(out, header, new BInteger(upvalueNameLength));
    for(int i = 0; i < upvalueNameLength; i++) {
      header.string.write(out, header, object.upvalues[i].bname);
    }
  }
  
}

class LFunctionType50 extends LFunctionType {

  @Override
  protected void parse_main(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    s.name = header.string.parse(buffer, header);
    s.lineBegin = header.integer.parse(buffer, header).asInt();
    s.lineEnd = 0;
    s.lenUpvalues = 0xFF & buffer.get();
    create_upvalues(buffer, header, s);
    s.lenParameter = 0xFF & buffer.get();
    s.vararg = 0xFF & buffer.get();
    s.maximumStackSize = 0xFF & buffer.get();
    parse_debug(buffer, header, s);
    parse_constants(buffer, header, s);
    parse_code(buffer, header, s);
  }
  
  @Override
  public List<Directive> get_directives() {
    return Arrays.asList(new Directive[] {
      Directive.SOURCE,
      Directive.LINEDEFINED,
      Directive.NUMPARAMS,
      Directive.IS_VARARG,
      Directive.MAXSTACKSIZE,
    });
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.string.write(out, header, object.name);
    header.integer.write(out, header, new BInteger(object.linedefined));
    out.write(object.numUpvalues);
    out.write(object.numParams);
    out.write(object.vararg);
    out.write(object.maximumStackSize);
    write_debug(out, header, object);
    write_constants(out, header, object);
    write_code(out, header, object);
  }
  
}

class LFunctionType51 extends LFunctionType {
  
  protected void parse_main(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    s.name = header.string.parse(buffer, header);
    s.lineBegin = header.integer.parse(buffer, header).asInt();
    s.lineEnd = header.integer.parse(buffer, header).asInt();
    s.lenUpvalues = 0xFF & buffer.get();
    create_upvalues(buffer, header, s);
    s.lenParameter = 0xFF & buffer.get();
    s.vararg = 0xFF & buffer.get();
    s.maximumStackSize = 0xFF & buffer.get();
    parse_code(buffer, header, s);
    parse_constants(buffer, header, s);
    parse_debug(buffer, header, s);
  }
  
  @Override
  public List<Directive> get_directives() {
    return Arrays.asList(new Directive[] {
      Directive.SOURCE,
      Directive.LINEDEFINED,
      Directive.LASTLINEDEFINED,
      Directive.NUMPARAMS,
      Directive.IS_VARARG,
      Directive.MAXSTACKSIZE,
    });
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.string.write(out, header, object.name);
    header.integer.write(out, header, new BInteger(object.linedefined));
    header.integer.write(out, header, new BInteger(object.lastlinedefined));
    out.write(object.numUpvalues);
    out.write(object.numParams);
    out.write(object.vararg);
    out.write(object.maximumStackSize);
    write_code(out, header, object);
    write_constants(out, header, object);
    write_debug(out, header, object);
  }
  
}

class LFunctionType52 extends LFunctionType {
  
  protected void parse_main(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    s.lineBegin = header.integer.parse(buffer, header).asInt();
    s.lineEnd = header.integer.parse(buffer, header).asInt();
    s.lenParameter = 0xFF & buffer.get();
    s.vararg = 0xFF & buffer.get();
    s.maximumStackSize = 0xFF & buffer.get();
    parse_code(buffer, header, s);
    parse_constants(buffer, header, s);
    parse_upvalues(buffer, header, s);
    s.name = header.string.parse(buffer, header);
    parse_debug(buffer, header, s);
  }
  
  @Override
  public List<Directive> get_directives() {
    return Arrays.asList(new Directive[] {
      Directive.LINEDEFINED,
      Directive.LASTLINEDEFINED,
      Directive.NUMPARAMS,
      Directive.IS_VARARG,
      Directive.MAXSTACKSIZE,
      Directive.SOURCE,
    });
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.integer.write(out, header, new BInteger(object.linedefined));
    header.integer.write(out, header, new BInteger(object.lastlinedefined));
    out.write(object.numParams);
    out.write(object.vararg);
    out.write(object.maximumStackSize);
    write_code(out, header, object);
    write_constants(out, header, object);
    write_upvalues(out, header, object);
    header.string.write(out, header, object.name);
    write_debug(out, header, object);
  }
  
}

class LFunctionType53 extends LFunctionType {
  
  protected void parse_main(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    s.name = header.string.parse(buffer, header); //TODO: psource
    s.lineBegin = header.integer.parse(buffer, header).asInt();
    s.lineEnd = header.integer.parse(buffer, header).asInt();
    s.lenParameter = 0xFF & buffer.get();
    s.vararg = 0xFF & buffer.get();
    s.maximumStackSize = 0xFF & buffer.get();
    parse_code(buffer, header, s);
    s.constants = header.constant.parseList(buffer, header);
    parse_upvalues(buffer, header, s);
    s.functions = header.function.parseList(buffer, header);
    parse_debug(buffer, header, s);
  }
  
  @Override
  public List<Directive> get_directives() {
    return Arrays.asList(new Directive[] {
      Directive.SOURCE,
      Directive.LINEDEFINED,
      Directive.LASTLINEDEFINED,
      Directive.NUMPARAMS,
      Directive.IS_VARARG,
      Directive.MAXSTACKSIZE,
    });
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.string.write(out, header, object.name);
    header.integer.write(out, header, new BInteger(object.linedefined));
    header.integer.write(out, header, new BInteger(object.lastlinedefined));
    out.write(object.numParams);
    out.write(object.vararg);
    out.write(object.maximumStackSize);
    write_code(out, header, object);
    header.constant.writeList(out, header, object.constants);
    write_upvalues(out, header, object);
    header.function.writeList(out, header, object.functions);
    write_debug(out, header, object);
  }
  
}

class LFunctionType54 extends LFunctionType {
  
  @Override
  protected void parse_debug(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    // TODO: process line info correctly
    s.lines = (new BIntegerType50(false, 1, false)).parseList(buffer, header);
    s.abslineinfo = header.abslineinfo.parseList(buffer, header);
    s.locals = header.local.parseList(buffer, header);
    parse_upvalue_names(buffer, header, s);
  }
  
  @Override
  protected void write_debug(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.integer.write(out, header, new BInteger(object.lines.length));
    for(int i = 0; i < object.lines.length; i++) {
      out.write(object.lines[i]);
    }
    header.abslineinfo.writeList(out, header, object.abslineinfo);
    header.local.writeList(out, header, object.locals);
    int upvalueNameLength = 0;
    for(LUpvalue upvalue : object.upvalues) {
      if(upvalue.bname != null && upvalue.bname != LString.NULL) {
        upvalueNameLength++;
      } else {
        break;
      }
    }
    header.integer.write(out, header, new BInteger(upvalueNameLength));
    for(int i = 0; i < upvalueNameLength; i++) {
      header.string.write(out, header, object.upvalues[i].bname);
    }
  }
  
  protected void parse_main(ByteBuffer buffer, BHeader header, LFunctionParseState s) {
    s.name = header.string.parse(buffer, header);
    s.lineBegin = header.integer.parse(buffer, header).asInt();
    s.lineEnd = header.integer.parse(buffer, header).asInt();
    s.lenParameter = 0xFF & buffer.get();
    s.vararg = 0xFF & buffer.get();
    s.maximumStackSize = 0xFF & buffer.get();
    parse_code(buffer, header, s);
    s.constants = header.constant.parseList(buffer, header);
    parse_upvalues(buffer, header, s);
    s.functions = header.function.parseList(buffer, header);
    parse_debug(buffer, header, s);
  }
  
  @Override
  public List<Directive> get_directives() {
    return Arrays.asList(new Directive[] {
      Directive.SOURCE,
      Directive.LINEDEFINED,
      Directive.LASTLINEDEFINED,
      Directive.NUMPARAMS,
      Directive.IS_VARARG,
      Directive.MAXSTACKSIZE,
    });
  }
  
  @Override
  public void write(OutputStream out, BHeader header, LFunction object) throws IOException {
    header.string.write(out, header, object.name);
    header.integer.write(out, header, new BInteger(object.linedefined));
    header.integer.write(out, header, new BInteger(object.lastlinedefined));
    out.write(object.numParams);
    out.write(object.vararg);
    out.write(object.maximumStackSize);
    write_code(out, header, object);
    header.constant.writeList(out, header, object.constants);
    write_upvalues(out, header, object);
    header.function.writeList(out, header, object.functions);
    write_debug(out, header, object);
  }
  
}
