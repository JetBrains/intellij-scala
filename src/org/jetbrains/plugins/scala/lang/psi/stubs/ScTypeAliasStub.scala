package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

trait ScTypeAliasStub extends NamedStub[ScTypeAlias] with ScMemberOrLocal {
  def isDeclaration: Boolean

  def typeElementText: Option[String]

  def typeElement: Option[ScTypeElement]

  def lowerBoundElementText: Option[String]

  def lowerBoundTypeElement: Option[ScTypeElement]

  def upperBoundElementText: Option[String]

  def upperBoundTypeElement: Option[ScTypeElement]

  def isStableQualifier: Boolean
}