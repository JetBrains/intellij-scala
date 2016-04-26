package org.jetbrains.plugins.scala.lang.psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue._

/**
 * User: Alefas
 * Date: 28.09.11
 */

trait ScalaTypeVisitor extends api.TypeVisitor {
  def visitCompoundType(c: ScCompoundType) {}
  def visitExistentialType(e: ScExistentialType) {}
  def visitExistentialArgument(s: ScExistentialArgument) {}
  def visitAbstractType(a: ScAbstractType) {}
  def visitTypePolymorphicType(t: ScTypePolymorphicType) {}
}