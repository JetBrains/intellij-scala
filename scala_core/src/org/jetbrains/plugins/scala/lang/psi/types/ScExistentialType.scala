package org.jetbrains.plugins.scala.lang.psi.types

/** 
* @author ilyas
*/

case class ScExistentialType(vals : Seq[Tuple2[String, ScType]]) extends ScType

object ScExistentialType {
  val unbounded = new ScExistentialType(Seq.empty)
}