package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
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

  def visitParameterizedType(`type`: ParameterizedType) {}

  def visitProjectionType(p: ScProjectionType) {}

  def visitThisType(t: ScThisType) {}

  def visitDesignatorType(d: ScDesignatorType) {}

  def visitLiteralType(l: ScLiteralType) {}
}
