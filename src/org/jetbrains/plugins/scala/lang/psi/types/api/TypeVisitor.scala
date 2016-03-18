package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScUndefinedType, StdType}

/**
  * @author adkozlov
  */
trait TypeVisitor {
  def visitStdType(`type`: StdType) {}

  def visitJavaArrayType(`type`: JavaArrayType) {}

  def visitMethodType(`type`: ScMethodType) {}

  def visitUndefinedType(`type`: ScUndefinedType) {}
}
