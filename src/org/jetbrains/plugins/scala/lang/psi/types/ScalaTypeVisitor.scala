package org.jetbrains.plugins.scala.lang.psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._

/**
 * User: Alefas
 * Date: 28.09.11
 */

trait ScalaTypeVisitor extends api.TypeVisitor {
  def visitCompoundType(c: ScCompoundType) {}
  def visitProjectionType(p: ScProjectionType) {}
  def visitParameterizedType(p: ScParameterizedType) {}
  def visitExistentialType(e: ScExistentialType) {}
  def visitThisType(t: ScThisType) {}
  def visitDesignatorType(d: ScDesignatorType) {}
  def visitTypeParameterType(t: ScTypeParameterType) {}
  def visitExistentialArgument(s: ScExistentialArgument) {}
  def visitAbstractType(a: ScAbstractType) {}
  def visitTypePolymorphicType(t: ScTypePolymorphicType) {}
}