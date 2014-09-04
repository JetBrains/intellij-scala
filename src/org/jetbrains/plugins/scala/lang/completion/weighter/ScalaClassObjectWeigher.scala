package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.psi.util.proximity.ProximityWeigher
import com.intellij.psi.util.{ProximityLocation, PsiTreeUtil}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.01.12
 */

class ScalaClassObjectWeigher extends ProximityWeigher {
  def weigh(element: PsiElement, location: ProximityLocation): Comparable[_] = {
    val elem = location.getPosition
    val ref = PsiTreeUtil.getParentOfType(elem, classOf[ScReferenceElement])
    if (ref == null) return null
    element match {
      case o: ScObject => 
        if (ref.isInstanceOf[ScReferenceExpression]) return 1
      case p: PsiClass =>
        if (!ref.isInstanceOf[ScReferenceExpression]) return 1
      case _ =>
    }
    0
  }
}