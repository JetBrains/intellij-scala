package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.{PsiNamedElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

//for packages & objects
case class ScDesignatorType(element: PsiNamedElement) extends ScType {
  override def equiv(t: ScType) = t match {
    case ScDesignatorType(element1) => element eq element1
    case _ => false
  }
}

//for classes
case class ScParameterizedType(owner: PsiTypeParameterListOwner, subst: ScSubstitutor) extends ScType {
  override def equiv(t: ScType): Boolean = t match {
    case ScParameterizedType(owner1, subst1) => {
      if (owner != owner1) false
      else {
        owner.getTypeParameters.equalsWith(owner1.getTypeParameters) {
          (tp1, tp2) => subst.subst(tp1).equiv(subst1.subst(tp2))
        }
      }
    }
    case _ => false
  }
}