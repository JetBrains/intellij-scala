package org.jetbrains.plugins.scala.debugger

/**
 * @author Nikolay.Tropin
 */
private[debugger] object BytecodeUtil {
  import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.Opcodes._

  private val oneByteCodes = Map(
    istore_0 -> iload_0,
    istore_1 -> iload_1,
    istore_2 -> iload_2,
    istore_3 -> iload_3,
    astore_0 -> aload_0,
    astore_1 -> aload_1,
    astore_2 -> aload_2,
    astore_3 -> aload_3,
    dstore_0 -> dload_0,
    dstore_1 -> dload_1,
    dstore_2 -> dload_2,
    dstore_3 -> dload_3,
    fstore_0 -> fload_0,
    fstore_1 -> fload_1,
    fstore_2 -> fload_2,
    fstore_3 -> fload_3,
    lstore_0 -> lload_0,
    lstore_1 -> lload_1,
    lstore_2 -> lload_2,
    lstore_3 -> lload_3
  )

  val oneByteLoadCodes = oneByteCodes.values.toSet
  val oneByteStoreCodes = oneByteCodes.keySet

  private val twoBytesCodes = Map(
    istore -> iload,
    astore -> aload,
    dstore -> dload,
    fstore -> fload,
    lstore -> lload
  )

  val twoBytesLoadCodes = twoBytesCodes.values.toSet
  val twoBytesStoreCodes = twoBytesCodes.keySet

  val returnCodes = Set(areturn, dreturn, freturn, ireturn, lreturn, voidReturn)

  def iloadCode(istoreCode: Seq[Byte]): Seq[Byte] = {
    istoreCode match {
      case Seq(`istore_0`) =>  Seq(iload_0)
      case Seq(`istore_1`) =>  Seq(iload_1)
      case Seq(`istore_2`) =>  Seq(iload_2)
      case Seq(`istore_3`) =>  Seq(iload_3)
      case Seq(`istore`, b) => Seq(iload, b)
      case _ => Nil
    }
  }

  def readIstore(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil
    bytecodes(codeIndex) match {
      case c @ (`istore_0` | `istore_1` | `istore_2` | `istore_3`) => Seq(c)
      case `istore` => Seq(istore, bytecodes(codeIndex + 1))
      case _ => Nil
    }
  }

  def readIload(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil
    bytecodes(codeIndex) match {
      case c @ (`iload_0` | `iload_1` | `iload_2` | `iload_3`) => Seq(c)
      case `iload` => Seq(iload, bytecodes(codeIndex + 1))
      case _ => Nil
    }
  }

  def readStoreCode(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil

    val bytecode = bytecodes(codeIndex)
    if (oneByteStoreCodes contains bytecode) Seq(bytecode)
    else if (twoBytesStoreCodes contains bytecode) Seq(bytecode, bytecodes(codeIndex + 1))
    else Nil
  }

  def readLoadCode(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil

    val bytecode = bytecodes(codeIndex)
    if (oneByteLoadCodes contains bytecode) Seq(bytecode)
    else if (twoBytesLoadCodes contains bytecode) Seq(bytecode, bytecodes(codeIndex + 1))
    else Nil
  }

  def loadCode(storeCode: Seq[Byte]): Seq[Byte] = {
    storeCode match {
      case Seq(b) => oneByteCodes.get(b).map(b => Seq(b)).getOrElse(Nil)
      case Seq(code, addr) => twoBytesCodes.get(code).map(b => Seq(b, addr)).getOrElse(Nil)
      case _ => Nil
    }
  }

  def isIconst_0(codeIndex: Int, bytecodes: Array[Byte]): Boolean = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) false
    else bytecodes(codeIndex) == iconst_0
  }

  def isGoto(codeIndex: Int, bytecodes: Array[Byte]): Boolean = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) false
    else bytecodes(codeIndex) == goto
  }
}
