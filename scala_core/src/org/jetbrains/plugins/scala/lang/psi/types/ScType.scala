package org.jetbrains.plugins.scala.lang.psi.types

trait ScType {

  def equiv(t: ScType): Boolean = false

  def conforms(t: ScType): Boolean = false 

}