package org.jetbrains.plugins.scala.lang.psi.api.base

trait ScStableCodeReferenceElement extends ScReferenceElement {
  def qualifier = findChild(classOf[ScStableCodeReferenceElement])
  def refName: String

  def qualName: String = (qualifier match {
    case Some(x) => x.qualName + "."
    case _ => ""
  }) + refName 


}