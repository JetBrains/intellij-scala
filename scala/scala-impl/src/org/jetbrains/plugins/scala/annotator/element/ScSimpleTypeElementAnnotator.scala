package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{ResolvesTo, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

object ScSimpleTypeElementAnnotator extends ElementAnnotator[ScSimpleTypeElement] {

  // TODO Shouldn't the ScExpressionAnnotator be enough?
  override def annotate(element: ScSimpleTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    //todo: check bounds conformance for parameterized type
    checkAbsentTypeArgs(element)
  }

  private def checkAbsentTypeArgs(element: ScSimpleTypeElement)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    // Dirty hack(see SCL-12582): we shouldn't complain about missing type args since they will be added by a macro after expansion
    def isFreestyleAnnotated(ah: ScAnnotationsHolder): Boolean = {
      (ah.findAnnotationNoAliases("freestyle.free") != null) ||
        ah.findAnnotationNoAliases("freestyle.module") != null
    }

    def needTypeArgs: Boolean = {
      def noHigherKinds(owner: ScTypeParametersOwner) = !owner.typeParameters.exists(_.typeParameters.nonEmpty)

      val canHaveTypeArgs = element.reference.map(_.resolve()).exists {
        case ah: ScAnnotationsHolder if isFreestyleAnnotated(ah) => false
        case c: PsiClass                                         => c.hasTypeParameters
        case owner: ScTypeParametersOwner                        => owner.typeParameters.nonEmpty
        case _                                                   => false
      }

      if (!canHaveTypeArgs) return false

      element.getParent match {
        case ScParameterizedTypeElement(`element`, _)                        => false
        case tp: ScTypeParam if tp.contextBoundTypeElement.contains(element) => false
        case (_: ScTypeArgs) childOf (gc: ScGenericCall) =>
          gc.referencedExpr match {
            case ResolvesTo(f: ScFunction) => noHigherKinds(f)
            case _                         => false
          }
        case (_: ScTypeArgs) childOf (parameterized: ScParameterizedTypeElement) =>
          parameterized.typeElement match {
            case ScSimpleTypeElement(ResolvesTo(target)) =>
              target match {
                case ScPrimaryConstructor.ofClass(c) => noHigherKinds(c)
                case owner: ScTypeParametersOwner    => noHigherKinds(owner)
                case _                               => false
              }
            case _ => false
          }
        case infix: ScInfixTypeElement if infix.left == element || infix.rightOption.contains(element) =>
          infix.operation.resolve() match {
            case owner: ScTypeParametersOwner => noHigherKinds(owner)
            case _                            => false
          }
        case _ => true
      }
    }

    if (needTypeArgs) {
      val annotation = holder.createErrorAnnotation(element.getTextRange,
        ScalaBundle.message("type.takes.type.parameters", element.getText))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }
}
