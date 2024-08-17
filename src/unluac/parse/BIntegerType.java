package unluac.parse;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract public class BIntegerType extends BObjectType<BInteger> {
  
  public static BIntegerType create50Type(boolean signed, int intSize, boolean allownegative) {
    return new BIntegerType50(signed, intSize, allownegative);
  }
  
  public static BIntegerType create54() {
    return new BIntegerType54();
  }
  
  public int getSize() {
    throw new IllegalStateException();
  }
  
  public BInteger create(int n) {
    return new BInteger(n);
  }
  
}

class BIntegerType50 extends BIntegerType {

  public final boolean signed;
  public final int intSize;
  public final boolean allownegative;
  
  public BIntegerType50(boolean signed, int intSize, boolean allownegative) {
    this.signed = signed;
    this.intSize = intSize;
    this.allownegative = allownegative;
  }
  
  protected BInteger raw_parse(ByteBuffer buffer, BHeader header) {
    BInteger value;
    if(signed && (intSize == 0 || intSize == 1 || intSize == 2 || intSize == 4)) {
      switch(intSize) {
        case 0:
          value = new BInteger(0);
          break;
        case 1:
          value = new BInteger(buffer.get());
          break;
        case 2:
          value = new BInteger(buffer.getShort());
          break;
        case 4:
          value = new BInteger(buffer.getInt());
          break;
        default:
          throw new IllegalStateException();  
      }
    } else {
      byte[] bytes = new byte[intSize];
      int start = 0;
      int delta = 1;
      if(buffer.order() == ByteOrder.LITTLE_ENDIAN) {
        start = intSize - 1;
        delta = -1;
      }
      for(int i = start; i >= 0 && i < intSize; i += delta) {
        bytes[i] = buffer.get();
      }
      if(signed) {
        value = new BInteger(new BigInteger(bytes));
      } else {
        value = new BInteger(new BigInteger(1, bytes));
      }
    }

    if(!allownegative && value.signum() < 0) {
      throw new IllegalStateException("Illegal number");
    }
    
    return value;
  }
  
  protected void raw_write(OutputStream out, BHeader header, BInteger object) throws IOException {
    byte[] bytes = object.littleEndianBytes(intSize);
    if(header.lheader.endianness == LHeader.LEndianness.LITTLE) {
      for(byte b : bytes) {
        out.write(b);
      }
    } else {
      for(int i = bytes.length - 1; i >= 0; i--) {
        out.write(bytes[i]);
      }
    }
  }
  
  @Override
  public BInteger parse(ByteBuffer buffer, BHeader header) {
    BInteger value = raw_parse(buffer, header);
    if(header.debug){
      System.out.println("-- parsed <integer> " + value.asInt());
    }
    return value;
  }
  
  @Override
  public void write(OutputStream out, BHeader header, BInteger object) throws IOException {
    raw_write(out, header, object);
  }
  
  @Override
  public int getSize() {
    return intSize;
  }

}

class BIntegerType54 extends BIntegerType {

  public BIntegerType54() {
    
  }
  
  @Override
  public BInteger parse(ByteBuffer buffer, BHeader header) {
    long x = 0;
    byte b;
    do {
      b = buffer.get();
      x = (x << 7) | (b & 0x7F);
    } while((b & 0x80) == 0);
    if(Integer.MIN_VALUE <= x && x <= Integer.MAX_VALUE) {
      return new BInteger((int) x);
    } else {
      return new BInteger(BigInteger.valueOf(x));
    }
  }
  
  @Override
  public void write(OutputStream out, BHeader header, BInteger object) throws IOException {
    byte[] bytes = object.compressedBytes();
    for(int i = bytes.length - 1; i >=1; i--) {
      out.write(bytes[i]);
    }
    out.write(bytes[0] | 0x80);
  }
  
}