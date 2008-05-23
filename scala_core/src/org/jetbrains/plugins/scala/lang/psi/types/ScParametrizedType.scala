package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

//for packages & objects
case class ScDesignatorType (element : PsiNamedElement) extends ScType {
  override def equiv(t : ScType) = t match {
    case ScDesignatorType(element1) => element eq element1
    case _ => false
  }
}

//for classes
case class ScParameterizedType(owner : ScTypeDefinition, subst : ScSubstitutor) extends ScType {
  override def equiv(t : ScType) : boolean = t match {
    case ScParameterizedType(owner1, subst1) => {
      if (!(owner eq owner1)) false
      else {
        val params = owner.typeParameters
        for (param <- params) {
          if (!subst.subst(param).equiv(subst1.subst(param))) return false
        }
        true
      }
    }
    case _ => false
  }
}