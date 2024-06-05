package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.TypeIsNotStable

object ScSimpleTypeElementAnnotator extends ElementAnnotator[ScSimpleTypeElement] {

  // TODO Shouldn't the ScExpressionAnnotator be enough?
  override def annotate(element: ScSimpleTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    //todo: check bounds conformance for parameterized type
    if (typeAware) {
      checkAbsentTypeArgs(element)
    }
  }

  private def checkAbsentTypeArgs(typeElement: ScSimpleTypeElement)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    val typeElementResolveResult = typeElement.reference.flatMap(_.bind()) match {
      case Some(rr) => rr
      case _ =>
        return
    }

    //this branch is tested via
    //org.jetbrains.plugins.scala.annotator.element.ReferenceToStableAndNonStableTypeTest_Scala3
    if (typeElement.isSingleton) {
      val showStableIdentifierRequiredError = typeElementResolveResult.problems.contains(TypeIsNotStable)
      if (showStableIdentifierRequiredError) {
        holder.createErrorAnnotation(
          typeElement,
          ScalaBundle.message("stable.identifier.required", typeElement.getText),
          ProblemHighlightType.GENERIC_ERROR
        )
        return
      }
    }
  }
}
