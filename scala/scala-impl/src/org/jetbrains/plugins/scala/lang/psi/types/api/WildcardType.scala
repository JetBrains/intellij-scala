package org.jetbrains.plugins.scala
package lang
package psi
package types
package api

/**
  * Special type used to represent higher-kinded unbounded [[ScAbstractType]].
  * Should only be used as a designator in [[ScParameterizedType]] and never outside of conformance checks.
  * Used to avoid code duplication between [[ScAbstractType]] and [[UndefinedType]] (e.g. partial unification).
  *
  * DO NOT USE THIS, UNLESS YOU KNOW WHAT YOU ARE DOING
  */
final case class WildcardType(tparam: TypeParameter) extends nonvalue.NonValueType with LeafType {
  override def inferValueType: ValueType               = TypeParameterType(tparam)

  override def visitType(visitor: ScalaTypeVisitor): Unit = {}

  override implicit def projectContext: project.ProjectContext = tparam.psiTypeParameter
}
