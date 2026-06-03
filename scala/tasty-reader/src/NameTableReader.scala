package org.jetbrains.plugins.scala.tasty.reader

import TermName.*

import dotty.tools.tasty.TastyFormat.NameTags.*
import dotty.tools.tasty.TastyReader

import scala.io.Codec

// See dotty.tools.dotc.core.tasty.TastyUnpickler

private class NameTableReader(in: TastyReader) {
  import in.*

  val nameAtRef: NameTable = new NameTable()

  def read(): Unit = {
    until(readEnd()) { nameAtRef.add(readNameContents()) }
  }

  private def readName(): TermName = nameAtRef(readNameRef())

  def termName(bs: Array[Byte], offset: Int, len: Int): SimpleName = {
    val chars = Codec.fromUTF8(bs, offset, len)
    new TermName(new String(chars))
  }

  private def readNameContents(): TermName = {
    val tag = readByte()
    val length = readNat()
    val start = currentAddr
    val end = start + length
    def readSignedRest(original: TermName, target: TermName): TermName = {
//      val result = readName().toTypeName
      // DOTTY: we shouldn't have to give an explicit type to paramsSig,
      // see https://github.com/lampepfl/dotty/issues/4867
//      val paramsSig: List[Signature.ParamSig] = until(end)(readParamSig())
//      val sig = Signature(paramsSig, result)
      goto(end)
      SignedName(original, "[...]", target)
    }

    val result = tag match {
      case UTF8 =>
        goto(end)
        termName(bytes, start.index, length)
      case QUALIFIED | EXPANDED | EXPANDPREFIX =>
        qualifiedNameKindOfTag(tag)(readName(), readName().asSimpleName)
      case UNIQUE =>
        val separator = readName().toString
        val num = readNat()
        val originals = until(end)(readName())
        val original = if (originals.isEmpty) EmptyTermName else originals.head
        uniqueNameKindOfSeparator(separator)(original, num)
      case DEFAULTGETTER =>
        numberedNameKindOfTag(tag)(readName(), readNat())
      case SIGNED =>
        val original = readName()
        readSignedRest(original, original)
      case TARGETSIGNED =>
        val original = readName()
        val target = readName()
        readSignedRest(original, target)
      case _ =>
        simpleNameKindOfTag(tag)(readName())
    }
    assert(currentAddr == end, s"bad name $result $start $currentAddr $end")
    result
  }
}
