package org.jetbrains.plugins.scala.decompiler.scalasig

/**
  * Nikolay.Tropin
  * 19-Jul-17
  */

object PickleFormat {
  //37 LITERALsymbol len_Nat name_Ref
  final val LITERALsymbol = 37 //is added to PickleFormat in scala 2.13
}

//Literal implementation of scala.reflect.internal.pickling.PickleFormat
sealed trait Entry

trait Symbol extends Flags with Entry {
  def name: String
  def parentRef: Option[Ref[Symbol]]

  def parent: Option[Symbol] = parentRef.map(_.get)

  def children: Seq[Symbol]
  def attributes: Seq[SymAnnot]

  def isType: Boolean = this match {
    case _: ClassSymbol if !isModule=> true
    case _: TypeSymbol => true
    case _ if isTrait => true
    case _ => false
  }

  lazy val path: String = parent match {
    case Some(NoSymbol) | None => name
    case Some(sym) => s"${sym.path}.$name"
  }
}

abstract class ScalaSigSymbol(protected val scalaSig: ScalaSig) extends Symbol {
  def children: Seq[Symbol] = scalaSig.children(this)
  def attributes: Seq[SymAnnot] = scalaSig.attributes(this)
}

abstract class SymbolInfoSymbol(val symbolInfo: SymbolInfo) extends ScalaSigSymbol(symbolInfo.name.scalaSig) {
  lazy val name: String = symbolInfo.name.get.value.trim
  def parentRef: Option[Ref[Symbol]] = Some(symbolInfo.owner)
  def hasFlag(flag: Long): Boolean = (symbolInfo.flags & flag) != 0L
  def infoType: Type = symbolInfo.info.get
}

case class Name(value: String) extends Entry

//represents scala.Symbol
case class ScalaSymbol(value: String) extends Entry

case object NoSymbol extends Symbol {
  def name = "<no symbol>"
  def parentRef: Option[Ref[Symbol]] = None
  def hasFlag(flag: Long) = false
  def children: Seq[Symbol] = Nil
  def attributes: Seq[SymAnnot] = Nil
}

case class TypeSymbol(info: SymbolInfo) extends SymbolInfoSymbol(info)

case class AliasSymbol(info: SymbolInfo) extends SymbolInfoSymbol(info)

case class ClassSymbol(info: SymbolInfo, thisTypeRef: Option[Ref[Type]]) extends SymbolInfoSymbol(info)

case class ObjectSymbol(info: SymbolInfo) extends SymbolInfoSymbol(info) {
  def companionClass: Option[ClassSymbol] = scalaSig.findCompanionClass(this)
}

case class MethodSymbol(info: SymbolInfo, aliasRef: Option[Ref[Symbol]]) extends SymbolInfoSymbol(info)

case class ExternalSymbol(nameRef: Ref[Name], ownerRef: Option[Ref[Symbol]], isObject: Boolean)
  extends ScalaSigSymbol(nameRef.scalaSig) {

  override def toString: String = path

  def hasFlag(flag: Long) = false

  override lazy val name: String = nameRef.value

  override def parentRef: Option[Ref[Symbol]] = ownerRef
}

case class SymbolInfo(name: Ref[Name], owner: Ref[Symbol], flags: Int, privateWithin: Option[Ref[Symbol]], info: Ref[Type]) {
  override def toString: String = s"SymbolInfo(${name.value})"
}

//sealed trait AnnotArg extends Entry //seems inconsistent with PickleFormat

sealed trait ConstAnnotArg extends Entry

case class Constant(value: Any) extends ConstAnnotArg

//separate class is useless
//case class AnnotInfoBody(infoRef: Ref[Type], annotArgs: Seq[Ref[ConstAnnotArg]], namedArgs: Seq[(Ref[Name], Ref[ConstAnnotArg])]) {
//  override def toString: String = "AnnotInfoBody"
//}

case class SymAnnot(symbol: Ref[Symbol], infoRef: Ref[Type], annotArgs: Seq[Ref[ConstAnnotArg]], named: Seq[(Ref[Name], Ref[ConstAnnotArg])]) extends Entry {
  def typeRef: Type = infoRef.get

  def args: Seq[ConstAnnotArg] = annotArgs.collect {
    case ref if ref.get != Tree => ref.get
  }

  def namedArgs: Seq[(String, ConstAnnotArg)] = named.collect {
    case (refName, refArg) if refArg.get != Tree => (refName.get.value, refArg.get)
  }

  def hasArgs: Boolean = args.size + namedArgs.size > 0
}

case object Children extends Entry

//case class AnnotInfo(body: AnnotInfoBody) extends ConstAnnotArg //seems inconsistent with PickleFormat
case object AnnotInfo extends Entry

case class AnnotArgArray(args: Seq[Ref[ConstAnnotArg]]) extends ConstAnnotArg

case object Tree extends ConstAnnotArg

trait Type extends Entry

trait TypeWithParams extends Type {
  def paramRefs: Seq[Ref[Symbol]]
  def paramSymbols: Seq[Symbol] = paramRefs.map(_.get)
}

trait FunctionType extends TypeWithParams {
  def resultType: Ref[Type]
}

case object NoType extends Type

case object NoPrefixType extends Type

case class ThisType(symbol: Ref[Symbol]) extends Type

case class SuperType(typerRef: Ref[Type], superTypeRef: Ref[Type]) extends Type

case class SingleType(typeRef: Ref[Type], symbol: Ref[Symbol]) extends Type

case class ConstantType(constant: Ref[Constant]) extends Type

case class TypeRefType(prefix: Ref[Type], symbol: Ref[Symbol], typeArgs: Seq[Ref[Type]]) extends Type

case class TypeBoundsType(lower: Ref[Type], upper: Ref[Type]) extends Type

case class RefinedType(classSym: Ref[Symbol], typeRefs: Seq[Ref[Type]]) extends Type

case class ClassInfoType(symbol: Ref[Symbol], typeRefs: Seq[Ref[Type]]) extends Type

case class ClassInfoTypeWithCons(symbol: Ref[Symbol], typeRefs: Seq[Ref[Type]], cons: String) extends Type

case class MethodType(resultType: Ref[Type], paramRefs: Seq[Ref[Symbol]]) extends FunctionType

case class NullaryMethodType(resultType: Ref[Type]) extends Type

case class PolyType(typeRef: Ref[Type], paramRefs: Seq[Ref[Symbol]]) extends TypeWithParams

case class PolyTypeWithCons(typeRef: Ref[Type], paramRefs: Seq[Ref[Symbol]], cons: String) extends TypeWithParams

case class ImplicitMethodType(resultType: Ref[Type], paramRefs: Seq[Ref[Symbol]]) extends FunctionType

//case class AnnotatedType(typeRef: Ref[Type], attribTreeRefs: Seq[Ref[AnnotInfo]]) extends Type
//we don't use AnnotInfos, and they seem inconsistent
case class AnnotatedType(typeRef: Ref[Type]) extends Type

case class AnnotatedWithSelfType(typeRef: Ref[Type], symbol: Ref[Symbol], attribTreeRefs: Seq[Int]) extends Type

case class DeBruijnIndexType(typeLevel: Int, typeIndex: Int) extends Type

case class ExistentialType(typeRef: Ref[Type], paramRefs: Seq[Ref[Symbol]]) extends TypeWithParams

//todo: should we use it somehow?
case class Modifiers(flags: Long, privateWithin: Ref[Name]) extends Entry