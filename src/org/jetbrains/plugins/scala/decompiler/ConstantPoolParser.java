package org.jetbrains.plugins.scala.decompiler;

/**
 * Nikolay.Tropin
 * 2014-12-03
 */
public class ConstantPoolParser {
  /**
   * The type of CONSTANT_Class constant pool items.
   */
  static final int CLASS = 7;

  /**
   * The type of CONSTANT_Fieldref constant pool items.
   */
  static final int FIELD = 9;

  /**
   * The type of CONSTANT_Methodref constant pool items.
   */
  static final int METH = 10;

  /**
   * The type of CONSTANT_InterfaceMethodref constant pool items.
   */
  static final int IMETH = 11;

  /**
   * The type of CONSTANT_String constant pool items.
   */
  static final int STR = 8;

  /**
   * The type of CONSTANT_Integer constant pool items.
   */
  static final int INT = 3;

  /**
   * The type of CONSTANT_Float constant pool items.
   */
  static final int FLOAT = 4;

  /**
   * The type of CONSTANT_Long constant pool items.
   */
  static final int LONG = 5;

  /**
   * The type of CONSTANT_Double constant pool items.
   */
  static final int DOUBLE = 6;

  /**
   * The type of CONSTANT_NameAndType constant pool items.
   */
  static final int NAME_TYPE = 12;

  /**
   * The type of CONSTANT_Utf8 constant pool items.
   */
  static final int UTF8 = 1;

  /**
   * The type of CONSTANT_MethodType constant pool items.
   */
  static final int MTYPE = 16;

  /**
   * The type of CONSTANT_MethodHandle constant pool items.
   */
  static final int HANDLE = 15;

  /**
   * The type of CONSTANT_InvokeDynamic constant pool items.
   */
  static final int INDY = 18;

  /**
   * The class to be parsed. <i>The content of this array must not be
   * modified. This field is intended for {@link org.jetbrains.org.objectweb.asm.Attribute} sub classes, and
   * is normally not needed by class generators or adapters.</i>
   */
  public final byte[] b;

  /**
   * The start index of each constant pool item in {@link #b b}, plus one. The
   * one byte offset skips the constant pool item tag that indicates its type.
   */
  private final int[] items;

  /**
   * The String objects corresponding to the CONSTANT_Utf8 items. This cache
   * avoids multiple parsing of a given CONSTANT_Utf8 constant pool item,
   * which GREATLY improves performances (by a factor 2 to 3). This caching
   * strategy could be extended to all constant pool items, but its benefit
   * would not be so great for these items (because they are much less
   * expensive to parse than CONSTANT_Utf8 items).
   */
  private final String[] strings;

  /**
   * Maximum length of the strings contained in the constant pool of the
   * class.
   */
  private final int maxStringLength;

  public ConstantPoolParser(int length, byte[] bytes) {
    b = bytes;
    items = new int[length];
    strings = new String[length];

    int max = 0;
    int index = 0;
    for (int i = 1; i < length; ++i) {
      items[i] = index + 1;
      int size;
      switch (b[index]) {
        case FIELD:
        case METH:
        case IMETH:
        case INT:
        case FLOAT:
        case NAME_TYPE:
        case INDY:
          size = 5;
          break;
        case LONG:
        case DOUBLE:
          size = 9;
          ++i;
          break;
        case UTF8:
          size = 3 + readUnsignedShort(index + 1);
          if (size > max) {
            max = size;
          }
          break;
        case HANDLE:
          size = 4;
          break;
        // case ClassWriter.CLASS:
        // case ClassWriter.STR:
        // case ClassWriter.MTYPE
        default:
          size = 3;
          break;
      }
      index += size;
    }
    maxStringLength = max;
  }

  int readUnsignedShort(final int index) {
    byte[] b = this.b;
    return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
  }

  String readUTF8Constant(int index) {
    String s = this.strings[index];
    if(s != null) {
      return s;
    } else {
      int start = this.items[index];
      return this.strings[index] = this.readUTF(start + 2, this.readUnsignedShort(start), new char[maxStringLength]);
    }
  }

  /**
   * Reads UTF8 string in {@link #b b}.
   *
   * @param index
   *            start offset of the UTF8 string to be read.
   * @param utfLen
   *            length of the UTF8 string to be read.
   * @param buf
   *            buffer to be used to read the string. This buffer must be
   *            sufficiently large. It is not automatically resized.
   * @return the String corresponding to the specified UTF8 string.
   */
  private String readUTF(int index, final int utfLen, final char[] buf) {
    int endIndex = index + utfLen;
    byte[] b = this.b;
    int strLen = 0;
    int c;
    int st = 0;
    char cc = 0;
    while (index < endIndex) {
      c = b[index++];
      switch (st) {
        case 0:
          c = c & 0xFF;
          if (c < 0x80) { // 0xxxxxxx
            buf[strLen++] = (char) c;
          } else if (c < 0xE0 && c > 0xBF) { // 110x xxxx 10xx xxxx
            cc = (char) (c & 0x1F);
            st = 1;
          } else { // 1110 xxxx 10xx xxxx 10xx xxxx
            cc = (char) (c & 0x0F);
            st = 2;
          }
          break;

        case 1: // byte 2 of 2-byte char or byte 3 of 3-byte char
          buf[strLen++] = (char) ((cc << 6) | (c & 0x3F));
          st = 0;
          break;

        case 2: // byte 2 of 3-byte char
          cc = (char) ((cc << 6) | (c & 0x3F));
          st = 1;
          break;
      }
    }
    return new String(buf, 0, strLen);
  }

  public MethodInfo readMethodInfo(int index) throws IllegalStateException {
    int methodInfoIndex = items[index];
    if (b[methodInfoIndex - 1] != METH) throw new IllegalStateException("Not a method ref info");
    int classInfoIndex = items[readUnsignedShort(methodInfoIndex)];

    if (b[classInfoIndex - 1] != CLASS) throw new IllegalStateException("Not a class info");
    int classNameIndex = readUnsignedShort(classInfoIndex);

    int methodNameAndTypeInfoIndex = items[readUnsignedShort(methodInfoIndex + 2)];
    if (b[methodNameAndTypeInfoIndex - 1] != NAME_TYPE)  throw new IllegalStateException("Not a name and type info");
    int methodNameIndex = readUnsignedShort(methodNameAndTypeInfoIndex);

    return new MethodInfo(readUTF8Constant(classNameIndex), readUTF8Constant(methodNameIndex));
  }

  public static class MethodInfo {
    public final String className;
    public final String methodName;

    public MethodInfo(String className, String methodName) {
      this.className = className;
      this.methodName = methodName;
    }
  }
}
