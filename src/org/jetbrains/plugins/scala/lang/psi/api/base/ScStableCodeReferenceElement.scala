package org.jetbrains.plugins.scala.lang.psi.api.base

trait ScStableCodeReferenceElement extends ScReferenceElement with ScPathElement {
  def qualifier = findChild(classOf[ScStableCodeReferenceElement])
  def pathQualifier = findChild(classOf[ScPathElement])

  def qualName: String = (qualifier match {
    case Some(x) => x.qualName + "."
    case _ => ""
  }) + refName 
}
