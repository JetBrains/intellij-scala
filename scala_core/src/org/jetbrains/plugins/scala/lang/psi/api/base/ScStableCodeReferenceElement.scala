package org.jetbrains.plugins.scala.lang.psi.api.base

trait ScStableCodeReferenceElement extends ScReferenceElement {
  def qualifier = findChild(classOf[ScStableCodeReferenceElement])
  def refName : String
}