package org.jetbrains.plugins.scala.lang.psi

import api.base.ScStableCodeReferenceElement
import api.statements.{ScValue, ScTypeAlias, ScVariable}
import com.intellij.psi.{PsiElement, PsiNamedElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

object ScalaPsiUtil {
  def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def isAppropriatePsiElement(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias => true
        case _ => false
      }
    }
    if (isAppropriatePsiElement(x)) return x
    while (parent != null && !isAppropriatePsiElement(parent)) parent = parent.getParent
    return parent
  }

  def adjustTypes(element: PsiElement): Unit = {
    for (child <- element.getChildren) {
      child match {
        case x: ScStableCodeReferenceElement => if (x.resolve != null) x.bindToElement(x.resolve)
        case _ => adjustTypes(child)
      }
    }
  }
}