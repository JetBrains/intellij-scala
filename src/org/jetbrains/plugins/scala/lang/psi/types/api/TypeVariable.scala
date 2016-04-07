package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScType, ScUndefinedSubstitutor, api}

case class TypeVariable(name: String) extends ValueType with NamedType {
  override def visitType(visitor: TypeVisitor) = visitor.visitTypeVariable(this)

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem) =
    (`type` match {
      case TypeVariable(`name`) => true
      case _ => false
    }, substitutor)
}
