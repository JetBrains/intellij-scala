package org.jetbrains.plugins.scala.lang.exprTree

import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.util.EnumSet.EnumSet

sealed abstract class AccessModifier

object AccessModifier {
  sealed abstract class Unqualified extends AccessModifier
  sealed abstract class PrivateOrProtected extends Unqualified
  case object Public extends Unqualified
  case object Private extends PrivateOrProtected
  case object Protected extends PrivateOrProtected
  sealed abstract class Qualified extends AccessModifier {
    def modifier: PrivateOrProtected
  }
  case class NameQualified(qualifier: String, override val modifier: PrivateOrProtected) extends Qualified
  case class ThisQualified(override val modifier: PrivateOrProtected) extends Qualified

  def fromPsi(psi: ScAccessModifier): AccessModifier = {
    val modifier: PrivateOrProtected =
      if (psi.isPrivate) Private
      else if (psi.isProtected) Protected
      else return Public

    psi.idText match {
      case Some(name) => NameQualified(name, modifier)
      case _ if psi.isThis => ThisQualified(modifier)
      case _ => modifier
    }
  }

  def fromPsi(psi: ScModifierList): AccessModifier =
    psi.accessModifier match {
      case Some(access) => fromPsi(access)
      case _ => Public
    }
}

sealed abstract class SymbolKind
object SymbolKind {
  sealed abstract class Type extends SymbolKind
  case object Class extends Type
  case object ObjectType extends Type
  case object Trait extends Type
  case object Enum extends Type
  sealed abstract class Value extends SymbolKind
  case object Object extends Value
  case object Val extends Value
  case object Var extends Value
}

sealed abstract class Symbol {
  def name: Option[String]
  def kind: SymbolKind
  def accessModifier: AccessModifier
  def modifier: EnumSet[ScalaModifier]
  def companion: Option[Symbol]
}

abstract class TypeSymbol extends Symbol {
  override def kind: SymbolKind.Type
  override def companion: Option[ValueSymbol] = None
}
abstract class ValueSymbol extends Symbol {
  override def kind: SymbolKind.Value
  override def companion: Option[TypeSymbol] = None
  def inferType(context: TreeContext): TypeResult
}

object ValueSymbol {
  sealed abstract class Origin
  object Origin {
    case class Psi(psiElement: ScValueOrVariable)
  }
}


sealed abstract class SymbolInContext[S <: Symbol] {
  def symbol: S
  def context: TreeContext
}