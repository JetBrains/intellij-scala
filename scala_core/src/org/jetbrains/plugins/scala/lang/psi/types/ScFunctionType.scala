package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: List[ScType]) extends ScType