package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType

/**
  * @author adkozlov
  */
trait TypeVisitor {
  def visitStdType(`type`: StdType) {}

  def visitJavaArrayType(`type`: JavaArrayType) {}

  def visitMethodType(`type`: ScMethodType) {}

  def visitUndefinedType(`type`: UndefinedType) {}

  def visitTypeParameterType(`type`: TypeParameterType) {}

  def visitTypeVariable(`type`: TypeVariable) {}

  def visitParameterizedType(`type`: ParameterizedType) {}
}
