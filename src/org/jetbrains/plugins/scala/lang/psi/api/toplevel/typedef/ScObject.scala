package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.{PsiElement, PsiMethod}
import lexer.ScalaTokenTypes
import statements.ScDeclaredElementsHolder

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScObject extends ScTypeDefinition with ScTypedDefinition with ScMember with ScDeclaredElementsHolder {

  override def getContainingClass: ScTemplateDefinition = null

  // TODO jzaugg I added this method rather than redefining getContainingClass to be conservative.
  //             Would anything break if getContainingClass didn't return null?
  def containingClass: Option[ScTemplateDefinition] = Option(super[ScMember].getContainingClass)

  //Is this object generated as case class companion module
  private var isSyntheticCaseClassCompanion: Boolean = false
  def isSyntheticObject: Boolean = isSyntheticCaseClassCompanion
  def setSyntheticObject: Unit = isSyntheticCaseClassCompanion = true
  def objectSyntheticMembers: Seq[PsiMethod]

  def getObjectToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kOBJECT)

  def declaredElements = Seq(this)
}