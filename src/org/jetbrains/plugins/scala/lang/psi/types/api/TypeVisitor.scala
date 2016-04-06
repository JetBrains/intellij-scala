package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeVariable, ScUndefinedType, StdType}

/**
  * @author adkozlov
  */
trait TypeVisitor {
  def visitStdType(`type`: StdType) {}

  def visitJavaArrayType(`type`: JavaArrayType) {}

  def visitMethodType(`type`: ScMethodType) {}

  def visitUndefinedType(`type`: ScUndefinedType) {}

  def visitTypeVariable(`type`: ScTypeVariable) {}
}
