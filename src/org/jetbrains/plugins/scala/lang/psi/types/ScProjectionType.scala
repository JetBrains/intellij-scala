package org.jetbrains.plugins.scala
package lang
package psi
package types


import resolve._
import impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.psi.{PsiClass, PsiNamedElement}
import api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScClass}
import api.base.{ScPathElement, ScStableCodeReferenceElement, ScReferenceElement}
import processor.ResolveProcessor

/**
* @author ilyas
*/

case class ScProjectionType(projected: ScType, ref: ScReferenceElement) extends ValueType {
  def resolveResult = ref.bind

  lazy val element: Option[PsiNamedElement] = resolveResult.map(_.element)

  override def removeAbstracts = ScProjectionType(projected.removeAbstracts, ref)
}