package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import a.f.sc

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAccessModifier extends ScalaPsiElement {
  def scope() : PsiNamedElement /* either ScTypeDefinition or PsiPackage */

  type AccessType = Access.Value

  def access() : AccessType

  def idText: Option[String]

  def isPrivate: Boolean
  def isProtected: Boolean
  def isThis: Boolean
  def isUnqualifiedPrivateOrThis: Boolean = isPrivate && (getReference == null || isThis)

  object Access extends Enumeration {
    val PRIVATE, PROTECTED, THIS_PRIVATE, THIS_PROTECTED = Value
  }
}