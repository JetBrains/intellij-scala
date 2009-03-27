package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAccessModifier extends ScalaPsiElement {
  def scope() : PsiNamedElement /* either ScTypeDefinition or PsiPackage */

  type AccessType = Access.Value

  def access() : AccessType

  def id(): Option[PsiElement] = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => None
    case x => Some(x.getPsi)
  }

  def isPrivate = getNode.findChildByType(ScalaTokenTypes.kPRIVATE) != null
  def isProtected = getNode.findChildByType(ScalaTokenTypes.kPROTECTED) != null

  object Access extends Enumeration {
    val PRIVATE, PROTECTED, THIS_PRIVATE, THIS_PROTECTED = Value
  }
}