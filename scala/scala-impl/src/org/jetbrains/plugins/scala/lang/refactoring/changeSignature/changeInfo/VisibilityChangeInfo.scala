package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature.changeInfo

import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer

private[changeInfo] trait VisibilityChangeInfo {
  this: ScalaChangeInfo =>

  override def getNewVisibility: String = {
    if (newVisibility != null) scalaToJavaVisibility(newVisibility)
    else oldVisibility
  }

  override def isVisibilityChanged: Boolean = oldVisibility != newVisibility

  def oldVisibility: String = {
    function.getModifierList.accessModifier.fold("")(AccessModifierRenderer.simpleTextHtmlEscaped)
  }

  private def scalaToJavaVisibility(scalaModifier: String): String = { //todo more correct transformation
    if (scalaModifier == "") PsiModifier.PUBLIC
    else if (scalaModifier.startsWith("protected")) PsiModifier.PROTECTED
    else PsiModifier.PRIVATE
  }
}
