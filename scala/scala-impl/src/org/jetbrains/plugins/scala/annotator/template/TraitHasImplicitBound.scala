package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

object TraitHasImplicitBound extends AnnotatorPart[ScTrait] {
  def annotate(definition: ScTrait, holder: AnnotationHolder, typeAware: Boolean) {
    val contextBoundElements = definition.typeParameters.flatMap(p => p.contextBoundTypeElement)
    for (te <- contextBoundElements) {
      val message = "Traits cannot have type parameters with context bounds"
      holder.createErrorAnnotation(te, message)
    }
    val viewBoundElements = definition.typeParameters.flatMap(p => p.viewTypeElement)
    for (te <- viewBoundElements) {
      val message = "Traits cannot have type parameters with view bounds"
      holder.createErrorAnnotation(te, message)
    }
  }
}