package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScDesignated
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

case class ScDesignatorType (_def : ScDesignated) extends ScType

case class ScParameterizedType(typeDef : ScTypeDefinition, subst : ScSubstitutor) extends ScType {

}