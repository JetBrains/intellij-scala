package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScUndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType

/**
  * @author adkozlov
  */
trait TypeVisitor {
  def visitStdType(`type`: StdType) {}

  def visitJavaArrayType(`type`: JavaArrayType) {}

  def visitMethodType(`type`: ScMethodType) {}

  def visitUndefinedType(`type`: ScUndefinedType) {}
}
