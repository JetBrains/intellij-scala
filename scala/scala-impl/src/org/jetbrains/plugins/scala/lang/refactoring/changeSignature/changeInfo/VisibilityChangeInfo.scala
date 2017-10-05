package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature.changeInfo

import com.intellij.psi.PsiModifier

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
private[changeInfo] trait VisibilityChangeInfo {
  this: ScalaChangeInfo =>

  def getNewVisibility: String = {
    if (newVisibility != null) scalaToJavaVisibility(newVisibility)
    else oldVisibility
  }

  def isVisibilityChanged: Boolean = oldVisibility != newVisibility

  def oldVisibility: String = {
    function.getModifierList.accessModifier.fold("")(_.getText)
  }

  private def scalaToJavaVisibility(scalaModifier: String): String = { //todo more correct transformation
    if (scalaModifier == "") PsiModifier.PUBLIC
    else if (scalaModifier.startsWith("protected")) PsiModifier.PROTECTED
    else PsiModifier.PRIVATE
  }
}
