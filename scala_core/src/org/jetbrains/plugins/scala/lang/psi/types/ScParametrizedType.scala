package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

case class ScDesignatorType (element : PsiNamedElement) extends ScType

case class ScParameterizedType(typeDef : ScTypeParametersOwner, subst : ScSubstitutor) extends ScType {

}