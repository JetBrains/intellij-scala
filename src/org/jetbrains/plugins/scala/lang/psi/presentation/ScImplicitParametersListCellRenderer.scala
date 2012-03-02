package org.jetbrains.plugins.scala.lang.psi.presentation

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiNamedElement
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 25.10.11
 */

class ScImplicitParametersListCellRenderer extends PsiElementListCellRenderer[PsiNamedElement] {
  def getElementText(element: PsiNamedElement): String = {
    if (element.name == "NotFoundParameter") return "Parameter not found"
    element match {
      case method: ScFunction => {
        method.name + PresentationUtil.presentationString(method.paramClauses) + ": " +
          PresentationUtil.presentationString(method.returnType.
            getOrAny)
      }
      case b: ScTypedDefinition => b.name + ": " +
        PresentationUtil.presentationString(b.getType(TypingContext.empty).getOrAny)
      case _ => element.name
    }
  }

  def getIconFlags: Int = {
    0
  }

  def getContainerText(element: PsiNamedElement, name: String) = null //todo: add package name
}