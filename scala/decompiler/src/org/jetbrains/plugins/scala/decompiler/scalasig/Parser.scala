package org.jetbrains.plugins.scala.decompiler.scalasig

import java.io.IOException
import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat

import org.jetbrains.plugins.scala.decompiler.scalasig.TagGroups._

import scala.annotation.switch
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.internal.pickling.PickleFormat._

/**
  * Nikolay.Tropin
  * 18-Jul-17
  */

//Some parts of scala.reflect.internal.pickling.UnPickler used
object Parser {

  def parseScalaSig(bytes: Array[Byte], fileName: String): ScalaSig = {
    try {
      new Builder(bytes).readAll()
    } catch {
      case ex: IOException =>
        throw ex
      case ex: Throwable =>
        val message = s"Error parsing scala signature of $fileName"
        System.err.println(message)
        ex.printStackTrace()
        throw ex
    }
  }

  private class Builder(bytes: Array[Byte]) extends ScalaSigReader(bytes) {
    val index: Array[Int] = createIndex()

    private val entries = new Array[Entry](index.length)

    implicit val scalaSig: ScalaSig = new ScalaSig(entries)

    def readAll(): ScalaSig = {
      var i = 0
      while (i < index.length) {
        entries(i) = readEntry(i)
        i += 1
      }
      scalaSig.finished()
      scalaSig
    }

    def readEntry(i: Int): Entry = {
      readIndex = index(i)
      val tag = readByte()

      (tag: @switch) match {
        case TERMname          => readName()
        case TYPEname          => readName()
        case NONEsym           => NoSymbol
        case TYPEsym           => readSymbol(tag)
        case ALIASsym          => readSymbol(tag)
        case CLASSsym          => readSymbol(tag)
        case MODULEsym         => readSymbol(tag)
        case VALsym            => readSymbol(tag)
        case EXTref            => readExtSymbol(tag)
        case EXTMODCLASSref    => readExtSymbol(tag)
        case NOtpe             => NoType
        case NOPREFIXtpe       => NoPrefixType
        case THIStpe           => readType(tag)
        case SINGLEtpe         => readType(tag)
        case CONSTANTtpe       => readType(tag)
        case TYPEREFtpe        => readType(tag)
        case TYPEBOUNDStpe     => readType(tag)
        case REFINEDtpe        => readType(tag)
        case CLASSINFOtpe      => readType(tag)
        case METHODtpe         => readType(tag)
        case POLYtpe           => readType(tag)
        case IMPLICITMETHODtpe => readType(tag)
        case LITERALunit       => readLiteral(tag)
        case LITERALboolean    => readLiteral(tag)
        case LITERALbyte       => readLiteral(tag)
        case LITERALshort      => readLiteral(tag)
        case LITERALchar       => readLiteral(tag)
        case LITERALint        => readLiteral(tag)
        case LITERALlong       => readLiteral(tag)
        case LITERALfloat      => readLiteral(tag)
        case LITERALdouble     => readLiteral(tag)
        case LITERALstring     => readLiteral(tag)
        case LITERALnull       => readLiteral(tag)
        case LITERALclass      => readLiteral(tag)
        case LITERALenum       => readLiteral(tag)
        case LITERALsymbol     => readLiteral(tag)
        case SYMANNOT          => readSymbolAnnotation()
        case CHILDREN          => Children
        case ANNOTATEDtpe      => readType(tag)
        case ANNOTINFO         => AnnotInfo
        case ANNOTARGARRAY     => readAnnotArgArray()
        case SUPERtpe          => readType(tag)
        case DEBRUIJNINDEXtpe  => readType(tag)
        case EXISTENTIALtpe    => readType(tag)
        case TREE              => Tree
        case MODIFIERS         => readModifiers()
        case SUPERtpe2         => readType(tag)
      }
    }

    def tagAt(i: Int): Byte = bytes(index(i))

    def tryReadRef[T <: Entry : ClassTag](tagCondition: Int => Boolean,
                                          constructor: Int => Ref[T],
                                          entryEnd: Int): Option[Ref[T]] = {
      if (readIndex >= entryEnd) return None

      val savedIdx = readIndex
      val ref = readNat()
      if (tagCondition(tagAt(ref))) Some(constructor(ref))
      else {
        readIndex = savedIdx
        None
      }
    }

    def readNameRef(): Ref[Name]                      = Ref.to[Name](readNat())
    def readSymbolRef(): Ref[Symbol]                  = Ref.to[Symbol](readNat())
    def readTypeRef(): Ref[Type]                      = Ref.to[Type](readNat())
    def readConstantRef(): Ref[Constant]              = Ref.to[Constant](readNat())
    def readConstantAnnotArgRef(): Ref[ConstAnnotArg] = Ref.to[ConstAnnotArg](readNat())

    def readScalaSymbol(): Ref[ScalaSymbol] = readNameRef().map(n => ScalaSymbol(n.value))

    def tryReadTypeRef(end: Int): Option[Ref[Type]]     = tryReadRef(isTypeTag, Ref.to[Type], end)
    def tryReadSymbolRef(end: Int): Option[Ref[Symbol]] = tryReadRef(isSymbolTag, Ref.to[Symbol], end)

    def readSymbolInfo(end: Int): SymbolInfo = {
      val name = readNameRef()
      val owner = readSymbolRef()
      val flags = readNat()
      val privateWithin = tryReadRef(isSymbolTag, Ref.to[Symbol], end)
      val typeInfo = readTypeRef()
      SymbolInfo(name, owner, flags, privateWithin, typeInfo)
    }

    def readSymbol(tag: Int): Symbol = {
      val end = readEnd()

      val symbol = tag match {
        case TYPEsym => TypeSymbol(readSymbolInfo(end))
        case ALIASsym => AliasSymbol(readSymbolInfo(end))
        case CLASSsym =>
          val clazz = ClassSymbol(readSymbolInfo(end), tryReadTypeRef(end))
          scalaSig.addClass(clazz)
          clazz
        case MODULEsym =>
          val obj = ObjectSymbol(readSymbolInfo(end))
          scalaSig.addObject(obj)
          obj
        case VALsym =>
          MethodSymbol(readSymbolInfo(end), tryReadSymbolRef(end))
        case _ => errorBadSignature("bad symbol tag: " + tag)
      }
      scalaSig.addChild(symbol.parentRef, symbol)
      symbol
    }

    def readExtSymbol(tag: Int): ExternalSymbol = {
      val end = readEnd()
      val name = readNameRef()
      val owner = tryReadSymbolRef(end)
      val isObject = tag == EXTMODCLASSref
      ExternalSymbol(name, owner, isObject)
    }

    def readTypes(end: Int): List[Ref[Type]] = until(end, readTypeRef _)
    def readSymbols(end: Int): List[Ref[Symbol]] = until(end, readSymbolRef _)

    def readName(): Name = Name(readUtf8(readNat()))

    def readType(tag: Int): Type = {
      val end = readEnd()

      def polyOrNullaryType(restpe: Ref[Type], tparams: List[Ref[Symbol]]): Type = tparams match {
        case Nil => NullaryMethodType(restpe)
        case _   => PolyType(restpe, tparams)
      }

      (tag: @switch) match {
        case NOtpe            => NoType
        case NOPREFIXtpe      => NoPrefixType
        case THIStpe          => ThisType(readSymbolRef())
        case SINGLEtpe        => SingleType(readTypeRef(), readSymbolRef()) // SI-7596 account for overloading
        case SUPERtpe         => SuperType(readTypeRef(), readTypeRef())
        case CONSTANTtpe      => ConstantType(readConstantRef())
        case TYPEREFtpe       => TypeRefType(readTypeRef(), readSymbolRef(), readTypes(end))
        case TYPEBOUNDStpe    => TypeBoundsType(readTypeRef(), readTypeRef())
        case REFINEDtpe       => RefinedType(readSymbolRef(), readTypes(end))
        case CLASSINFOtpe     => ClassInfoType(readSymbolRef(), readTypes(end))
        case METHODtpe        => MethodType(readTypeRef(), readSymbols(end))
        case POLYtpe          => polyOrNullaryType(readTypeRef(), readSymbols(end))
        case DEBRUIJNINDEXtpe => DeBruijnIndexType(readNat(), readNat())
        case EXISTENTIALtpe   => ExistentialType(readTypeRef(), readSymbols(end))
        case ANNOTATEDtpe     => AnnotatedType(readTypeRef())
        case _                => errorBadSignature("bad type tag: " + tag)
      }
    }

    def readLiteral(tag: Int): Constant = {
      val len = readNat()
      (tag: @switch) match {
        case LITERALunit       => Constant(())
        case LITERALboolean    => Constant(readLong(len) != 0L)
        case LITERALbyte       => Constant(readLong(len).toByte)
        case LITERALshort      => Constant(readLong(len).toShort)
        case LITERALchar       => Constant(readLong(len).toChar)
        case LITERALint        => Constant(readLong(len).toInt)
        case LITERALlong       => Constant(readLong(len))
        case LITERALfloat      => Constant(intBitsToFloat(readLong(len).toInt))
        case LITERALdouble     => Constant(longBitsToDouble(readLong(len)))
        case LITERALstring     => Constant(readNameRef())
        case LITERALnull       => Constant(null)
        case LITERALclass      => Constant(readTypeRef())
        case LITERALenum       => Constant(readSymbolRef())
        case LITERALsymbol     => Constant(readScalaSymbol())
        case _                 => errorBadSignature("bad constant tag: " + tag)
      }
    }

    protected def readSymbolAnnotation(): SymAnnot = {
      val end       = readEnd()
      val sym       = readSymbolRef()
      val info      = readTypeRef()
      val args      = until(end, () => tryReadRef(isConstAnnotArgTag, Ref.to[ConstAnnotArg], end)).flatten
      val namedArgs = until(end, () => (readNameRef(), readConstantAnnotArgRef()))
      val annot = SymAnnot(sym, info, args, namedArgs)
      scalaSig.addAttribute(annot)
      annot
    }

    def readAnnotArgArray(): AnnotArgArray = {
      val end = readEnd()
      val args = until(end, readConstantAnnotArgRef _)
      AnnotArgArray(args)
    }


    //implementation from scala.reflect.internal.pickling.UnPickler.Scan.readModifiers
    def readModifiers(): Modifiers = {
      readEnd()
      val pflagsHi = readNat()
      val pflagsLo = readNat()
      val pflags = (pflagsHi.toLong << 32) + pflagsLo
      val flags = scala.reflect.internal.Flags.pickledToRawFlags(pflags)
      val privateWithin = readNameRef()
      Modifiers(flags, privateWithin)
    }

    private def readEnd() = readNat() + readIndex

    protected def errorBadSignature(msg: String) =
      throw new RuntimeException(s"malformed Scala signature " + " at " + readIndex + "; " + msg)

  }
}
