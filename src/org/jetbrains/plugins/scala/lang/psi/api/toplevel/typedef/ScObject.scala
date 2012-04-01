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

  /** Is this object accessible from a stable path from the root package? */
  def isStatic: Boolean = getContainingClass match {
    case obj: ScObject => obj.isStatic
    case null => true
    case _ => false
  }
}