package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import lexer.ScalaTokenTypes
import statements.ScDeclaredElementsHolder
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}

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
  def setSyntheticObject() {
    isSyntheticCaseClassCompanion = true
  }
  def objectSyntheticMembers: Seq[PsiMethod]

  def getObjectToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kOBJECT)

  def getObjectClassOrTraitToken = getObjectToken

  def declaredElements = Seq(this)

  def hasPackageKeyword: Boolean

  def fakeCompanionClass: Option[PsiClass]

  def fakeCompanionClassOrCompanionClass: PsiClass
}