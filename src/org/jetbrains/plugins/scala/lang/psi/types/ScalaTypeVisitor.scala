package org.jetbrains.plugins.scala.lang.psi
package types

import types._
import nonvalue._

/**
 * User: Alefas
 * Date: 28.09.11
 */

trait ScalaTypeVisitor {
  def visitStdType(x: StdType) {}
  def visitFunctionType(f: ScFunctionType) {}
  def visitTupleType(t: ScTupleType) {}
  def visitCompoundType(c: ScCompoundType) {}
  def visitProjectionType(p: ScProjectionType) {}
  def visitJavaArrayType(j: JavaArrayType) {}
  def visitParameterizedType(p: ScParameterizedType) {}
  def visitExistentialType(e: ScExistentialType) {}
  def visitThisType(t: ScThisType) {}
  def visitDesignatorType(d: ScDesignatorType) {}
  def visitTypeParameterType(t: ScTypeParameterType) {}
  def visitExistentialArgument(e: ScExistentialArgument) {}
  def visitSkolemizedType(s: ScSkolemizedType) {}
  def visitTypeVariable(t: ScTypeVariable) {}
  def visitUndefinedType(u: ScUndefinedType) {}
  def visitMethodType(m: ScMethodType) {}
  def visitAbstractType(a: ScAbstractType) {}
  def visitTypePolymorphicType(t: ScTypePolymorphicType) {}
}