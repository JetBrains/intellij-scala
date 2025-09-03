package org.jetbrains.plugins.scala.lang.ir.typeTree

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement

sealed abstract class TypeTree {
  def isSingleton: Boolean = ???
  def toScalaCode: String = ???
}

object TypeTree {
  case class SimpleType(qual: Option[SimpleType], name: String) extends TypeTree
  case class MatchType() extends TypeTree
  case class ParameterizedType(qualifier: TypeTree, arguments: Seq[TypeTree]) extends TypeTree
  case class InfixType() extends TypeTree
  case class CompoundType(components: Seq[TypeTree], refinement: Option[ScRefinement]) extends TypeTree
  case class ParenthesizedType(inner: TypeTree) extends TypeTree
}