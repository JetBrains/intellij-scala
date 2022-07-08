package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExpressionOwnerStub, ScTypeElementOwnerStub}

trait ScFunctionStub[F <: ScFunction] extends NamedStub[F]
  with ScTopLevelElementStub[F]
  with ScMemberOrLocal
  with ScTypeElementOwnerStub[F]
  with ScExpressionOwnerStub[F]
  with ScImplicitStub
  with ScGivenStub {

  def implicitConversionParameterClass: Option[String]

  def isDeclaration: Boolean

  def annotations: Array[String]

  def hasAssign: Boolean

  def isExtensionMethod: Boolean
}
