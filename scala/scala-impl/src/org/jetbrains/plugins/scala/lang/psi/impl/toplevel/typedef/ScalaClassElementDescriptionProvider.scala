package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.{ElementDescriptionLocation, ElementDescriptionProvider, PsiElement}
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

//ATTENTION!!!
//It's not clear what was the original primary purpose of this class
//This provider doesn't use "location" and returns name in too many possible locations
//Ideally it should be specific on which locations it should handle
class ScalaClassElementDescriptionProvider extends ElementDescriptionProvider {
  override def getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String = {
    if (location == UsageViewTypeLocation.INSTANCE)
      return null //Handled by com.intellij.usageView.UsageViewTypeLocation.DEFAULT_PROVIDER

    element match {
      case o: ScObject => o.name
      case PsiClassWrapper(definition) => definition.name
      case _ => null
    }
  }
}
