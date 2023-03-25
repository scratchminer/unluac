package unluac.decompile.expression;

import java.util.ArrayList;
import java.util.Collections;

import unluac.decompile.Decompiler;
import unluac.decompile.Output;
import unluac.decompile.Walker;

public class TableLiteral extends Expression {

  public static class Entry implements Comparable<Entry> {
    
    public final Expression key;
    public final Expression value;
    public final boolean isList;
    public final int timestamp;
    private boolean hash;
    
    public Entry(Expression key, Expression value, boolean isList, int timestamp) {
      this.key = key;
      this.value = value;
      this.isList = isList;
      this.timestamp = timestamp;
    }
    
    @Override
    public int compareTo(Entry e) {
      return ((Integer) timestamp).compareTo(e.timestamp);
    }
  }
  
  private ArrayList<Entry> entries;
  
  private boolean isObject = true;
  private boolean isList = true;
  
  private boolean gettingIndex = false;
  private boolean walking = false;
  private boolean referenceChecking = false;
  
  private int listLength = 1;
  
  private final int hashSize;
  private int hashCount;
  
  private String name;
  
  public TableLiteral(int arraySize, int hashSize) {
    super(PRECEDENCE_ATOMIC);
    entries = new ArrayList<Entry>(arraySize + hashSize);
    this.hashSize = hashSize;
    hashCount = 0;
  }
  
  public TableLiteral(int arraySize, int hashSize, String name) {
    super(PRECEDENCE_ATOMIC);
    entries = new ArrayList<Entry>(arraySize + hashSize);
    this.hashSize = hashSize;
    this.name = name;
    hashCount = 0;
  }

  @Override
  public void walk(Walker w) {
    if(walking) return;
    walking = true;
    
    Collections.sort(entries);
    w.visitExpression(this);
    boolean lastEntry = false;
    for(Entry entry : entries) {
      entry.key.walk(w);
      if(!lastEntry) {
        entry.value.walk(w);
        if(entry.value.isMultiple()) {
          lastEntry = true;
        }
      }
    }
    
    walking = false;
  }
  
  @Override
  public int getConstantIndex() {
    if(gettingIndex) return -1;
    gettingIndex = true;
    
    int index = -1;
    for(Entry entry : entries) {
      index = Math.max(entry.key.getConstantIndex(), index);
      index = Math.max(entry.value.getConstantIndex(), index);
    }
    
    gettingIndex = false;
    return index;
  }
  
  @Override
  public void print(Decompiler d, Output out) {
    listLength = 1;
    if(entries.isEmpty()) {
      out.print("{}");
    } else {
      boolean lineBreak = isList && entries.size() > 5 || isObject && entries.size() > 2 || !isObject;
      //System.out.println(" -- " + (isList && entries.size() > 5));
      //System.out.println(" -- " + (isObject && entries.size() > 2));
      //System.out.println(" -- " + (!isObject));
      if(!lineBreak) {
        for(Entry entry : entries) {
          Expression value = entry.value;
          if(!(value.isBrief())) {
            lineBreak = true;
            break;
          }
        }
      }
      out.print("{");
      if(lineBreak) {
        out.println();
        out.indent();
      }
      printEntry(d, 0, out, name);
      if(!entries.get(0).value.isMultiple()) {
        for(int index = 1; index < entries.size(); index++) {
          out.print(",");
          if(lineBreak) {
            out.println();
          } else {
            out.print(" ");
          }
          printEntry(d, index, out, name);
          if(entries.get(index).value.isMultiple()) {
            break;
          }
        }
      }
      if(lineBreak) {
        out.println();
        out.dedent();
      }
      out.print("}");     
    }    
  }
  
  public boolean referencesTable() {
    if(referenceChecking) return true;
    referenceChecking = true;
    
    boolean referencesSelf = false;
    for(Entry entry : entries) {
      if(entry.key.referencesTable() || entry.value.referencesTable()) {
        referencesSelf = true;
        break;
      }
    }
    
    referenceChecking = false;
    return referencesSelf;
  }
  
  @Override
  public boolean referencesTableNonRecursive() {
    if(referenceChecking) return true;
    referenceChecking = true;
    
    boolean referencesSelf = false;
    for(Entry entry : entries) {
      if(entry.key.referencesTableNonRecursive() || entry.value.referencesTableNonRecursive()) {
        referencesSelf = true;
        break;
      }
    }
    
    referenceChecking = false;
    return referencesSelf;
  }
  
  private void printEntry(Decompiler d, int index, Output out, String tableName) {
    Entry entry = entries.get(index);
    Expression key = entry.key;
    Expression value = entry.value;
    boolean isList = entry.isList;
    boolean multiple = index + 1 >= entries.size() || value.isMultiple();
    
    if(isList && key.isInteger() && listLength == key.asInteger()) {
      if(multiple) {
        value.printMultiple(d, out);
      } else {
        value.print(d, out);
      }
      listLength++;
    } else if(entry.hash) {
      out.print(key.asName());
      out.print(" = ");
      
      referenceChecking = true;
      if(value.referencesTableNonRecursive()) out.print(name);
      else value.print(d, out);
      referenceChecking = false;
      
    } else {
      out.print("[");
      
      referenceChecking = true;
      if(key.referencesTableNonRecursive()) out.print(name);
      else key.printBraced(d, out);
      referenceChecking = false;
      
      out.print("] = ");
      
      referenceChecking = true;
      if(value.referencesTableNonRecursive()) out.print(name);
      else value.print(d, out);
      referenceChecking = false;
    }
  }
  
  @Override
  public boolean isTableLiteral() {
    return true;
  }
  
  @Override
  public boolean isUngrouped() {
    return true;
  }
  
  @Override
  public boolean isNewEntryAllowed() {
    return true;
  }
  
  @Override
  public void addEntry(Entry entry) {
    if(hashCount < hashSize && entry.key.isIdentifier()) {
      entry.hash = true;
      hashCount++;
    }
    entries.add(entry);
    isObject = isObject && (entry.isList || entry.key.isIdentifier());
    isList = isList && entry.isList;
  }
  
  @Override
  public boolean isBrief() {
    return false;
  }
    
}
