package org.scalatest.finders;

class NameTransformer {

  private static final int nops = 128;
  private static final int ncodes = 26 * 26;
  
  private static final String[] op2code = new String[nops];
  private static final OpCodes[] code2op = new OpCodes[ncodes];
  
  static {
    /* Note: decoding assumes opcodes are only ever lowercase. */
    enterOp('~', "$tilde");
    enterOp('=', "$eq");
    enterOp('<', "$less");
    enterOp('>', "$greater");
    enterOp('!', "$bang");
    enterOp('#', "$hash");
    enterOp('%', "$percent");
    enterOp('^', "$up");
    enterOp('&', "$amp");
    enterOp('|', "$bar");
    enterOp('*', "$times");
    enterOp('/', "$div");
    enterOp('+', "$plus");
    enterOp('-', "$minus");
    enterOp(':', "$colon");
    enterOp('\\', "$bslash");
    enterOp('?', "$qmark");
    enterOp('@', "$at");
  }

  private record OpCodes(char op, String code, NameTransformer.OpCodes next) {
  }
  
  
  private static void enterOp(char op, String code) {
    op2code[op] = code;
    int c = (code.charAt(1) - 'a') * 26 + code.charAt(2) - 'a';
    code2op[c] = new OpCodes(op, code, code2op[c]);
  }
  
  /** Replace operator symbols by corresponding `\$opname`.
  *
  *  @param name the string to encode
  *  @return     the string with all recognized opchars replaced with their encoding
  */
  public static String encode(String name) {
    StringBuilder buf = null;
    int len = name.length();
    int i = 0;
    while (i < len) {
      char c = name.charAt(i);
      if (c < nops && (op2code[c] != null)) {
        if (buf == null) {
          buf = new StringBuilder();
          buf.append(name, 0, i);
        }
        buf.append(op2code[c]);
      /* Handle glyphs that are not valid Java/JVM identifiers */
      }
      else if (!Character.isJavaIdentifierPart(c)) {
        if (buf == null) {
          buf = new StringBuilder();
          buf.append(name, 0, i);
        }
        buf.append(String.format("$u%04X", c));
      }
      else if (buf != null) {
        buf.append(c);
      }
      i += 1;
    }
    if (buf == null) 
      return name; 
    else 
      return buf.toString();
  }
 
  /** Replace `\$opname` by corresponding operator symbol.
  *
  *  @param name0 the string to decode
  *  @return      the string with all recognized operator symbol encodings replaced with their name
  */
 public static String decode(String name0) {
   //System.out.println("decode: " + name);//DEBUG
   String name = name0.endsWith("<init>") ? name0.substring(0, name0.length() - ("<init>").length()) + "this" : name0;
   StringBuilder buf = null;
   int len = name.length();
   int i = 0;
   while (i < len) {
     OpCodes ops = null;
     boolean unicode = false;
     char c = name.charAt(i);
     if (c == '$' && (i + 2) < len) {
       char ch1 = name.charAt(i+1);
       if ('a' <= ch1 && ch1 <= 'z') {
         char ch2 = name.charAt(i+2);
         if ('a' <= ch2 && ch2 <= 'z') {
           ops = code2op[(ch1 - 'a') * 26 + ch2 - 'a'];
           while ((ops != null) && !name.startsWith(ops.code, i)) ops = ops.next;
           if (ops != null) {
             if (buf == null) {
               buf = new StringBuilder();
               buf.append(name, 0, i);
             }
             buf.append(ops.op);
             i += ops.code.length();
           }
           /* Handle the decoding of Unicode glyphs that are
            * not valid Java/JVM identifiers */
         } else if ((len - i) >= 6 && // Check that there are enough characters left
                    ch1 == 'u' &&
                    ((Character.isDigit(ch2)) ||
                    ('A' <= ch2 && ch2 <= 'F'))) {
           /* Skip past "$u", next four should be hexadecimal */
           String hex = name.substring(i+2, i+6);
           try {
             char str = (char) Integer.parseInt(hex, 16);
             if (buf == null) {
               buf = new StringBuilder();
               buf.append(name, 0, i);
             }
             buf.append(str);
             /* 2 for "$u", 4 for hexadecimal number */
             i += 6;
             unicode = true;
           } catch(NumberFormatException e) {
               /* <code>hex</code> did not decode to a hexadecimal number, so
                * do nothing. */
           }
         }
       }
     }
     /* If we didn't see an opcode or encoded Unicode glyph, and the
       buffer is non-empty, write the current character and advance
        one */
     if ((ops == null) && !unicode) {
       if (buf != null)
         buf.append(c);
       i += 1;
     }
   }
   //System.out.println("= " + (if (buf == null) name else buf.toString()));//DEBUG
   if (buf == null) 
     return name;
   else 
     return buf.toString();
 }
}
