package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.{ElementDescriptionLocation, ElementDescriptionProvider, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper


/**
 * User: Alefas
 * Date: 18.02.12
 */

class ScalaClassElementDescriptionProvider extends ElementDescriptionProvider {
  def getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String = {
    element match {
      case o: ScObject => o.name
      case PsiClassWrapper(definition) => definition.name
      case _ => null
    }
  }
}
