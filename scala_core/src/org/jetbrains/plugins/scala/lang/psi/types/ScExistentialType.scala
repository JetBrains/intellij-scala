package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

case class ScExistentialType(tas: List[ScTypeAlias], vals : List[Tuple2[String, ScType]]) extends ScType