package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: List[Tuple2[ScType, Boolean]]) extends ScType