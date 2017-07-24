package scala.tools.scalap
package scalax
package rules
package scalasig

import scala.tools.scalap.scalax.rules.scalasig.ScalaSigEntryParsers._

trait Symbol extends Flags {
  def name : String
  def parent : Option[Symbol]
  def children : Seq[Symbol]

  def isType: Boolean = this match {
    case _: ClassSymbol if !isModule=> true
    case _: TypeSymbol => true
    case _ if isTrait => true
    case _ => false
  }

  def path: String = parent.filterNot(_ == NoSymbol).map(_.path + ".").getOrElse("") + name
}

case object NoSymbol extends Symbol {
  def name = "<no symbol>"
  def parent = None
  def hasFlag(flag : Long) = false
  def children = Nil
}

abstract class ScalaSigSymbol extends Symbol {
  def applyRule[A](rule : EntryParser[A]) : A = expect(rule)(entry)
  def applyScalaSigRule[A](rule : ScalaSigParsers.Parser[A]): A = ScalaSigParsers.expect(rule)(entry.scalaSig)

  def entry : ScalaSig#Entry
  def index: Int = entry.index

  lazy val children : Seq[Symbol] = applyScalaSigRule(ScalaSigParsers.symbols) filter (_.parent.contains(this))
  lazy val attributes : Seq[AttributeInfo] = {
    val found = applyScalaSigRule(ScalaSigParsers.attributes) filter {attr =>
      (attr.symbol, this) match {
        case (s, t) if s == t => true
        case (m1: MethodSymbol, m2: MethodSymbol) => equiv(m1, m2)
        case _ => false
      }
    }
    val distinct = found.map(a => a.typeRef -> a).toMap.values
    distinct.toVector
  }

  private def equiv(m1: MethodSymbol, m2: MethodSymbol) = {
    def unwrapType(t: Type) = t match {
      case NullaryMethodType(tp) => tp
      case PolyType(tp, Seq()) => tp
      case _ => t
    }

    m1.name.trim == m2.name.trim && m1.parent == m2.parent &&
      unwrapType(m1.infoType) == unwrapType(m2.infoType)
  }
}

case class ExternalSymbol(name: String, parent: Option[Symbol], entry: ScalaSig#Entry) extends ScalaSigSymbol {
  override def toString: String = path
  def hasFlag(flag: Long) = false
}

case class SymbolInfo(name : String, owner : Symbol, flags : Int, privateWithin : Option[AnyRef], info : Int, entry : ScalaSig#Entry) {
  def symbolString(any : AnyRef): String = any match {
    case sym : SymbolInfoSymbol => sym.index.toString
    case other => other.toString
  }

  override def toString: String = name + ", owner=" + symbolString(owner) + ", flags=" + flags.toHexString + ", info=" + info + (privateWithin match {
    case Some(any) => ", privateWithin=" + symbolString(any)
    case None => " "
  })
}

abstract class SymbolInfoSymbol extends ScalaSigSymbol {
  def symbolInfo : SymbolInfo

  def entry: ScalaSig#Entry = symbolInfo.entry
  def name: String = symbolInfo.name
  def parent = Some(symbolInfo.owner)
  def hasFlag(flag : Long): Boolean = (symbolInfo.flags & flag) != 0L

  lazy val infoType = applyRule(parseEntry(typeEntry)(symbolInfo.info))
}

case class TypeSymbol(symbolInfo : SymbolInfo) extends SymbolInfoSymbol{
  override def path: String = name
}

case class AliasSymbol(symbolInfo : SymbolInfo) extends SymbolInfoSymbol{
  override def path: String = name
}
case class ClassSymbol(symbolInfo : SymbolInfo, thisTypeRef : Option[Int]) extends SymbolInfoSymbol {
  lazy val selfType = thisTypeRef.map{(x: Int) => applyRule(parseEntry(typeEntry)(x))}
}
case class ObjectSymbol(symbolInfo : SymbolInfo) extends SymbolInfoSymbol
case class MethodSymbol(symbolInfo : SymbolInfo, aliasRef : Option[Int]) extends SymbolInfoSymbol
