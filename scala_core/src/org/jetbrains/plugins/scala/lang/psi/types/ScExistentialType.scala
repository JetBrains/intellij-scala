package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.{ScTypeAlias, ScValue}

/** 
* @author ilyas
*/

case class ScExistentialType(val quantified : ScType, types : Seq[ScTypeAlias], vals : Seq[ScValue]) extends ScType {
  
}

case class ScWildcardType(val lowerBound : ScType, val upperBound : ScType) extends ScType