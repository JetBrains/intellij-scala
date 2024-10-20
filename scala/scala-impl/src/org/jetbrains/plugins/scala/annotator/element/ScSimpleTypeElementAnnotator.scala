package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScInfixTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeArgs}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
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
    val typeElementResolved = typeElementResolveResult.element

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

    // Dirty hack(see SCL-12582): we shouldn't complain about missing type args since they will be added by a macro after expansion
    def isFreestyleAnnotated(ah: ScAnnotationsHolder): Boolean = {
      (ah.findAnnotationNoAliases("freestyle.free") != null) ||
        ah.findAnnotationNoAliases("freestyle.module") != null
    }

    def needTypeArgs: Boolean = {
      def noHigherKinds(owner: ScTypeParametersOwner) = !owner.typeParameters.exists(_.typeParameters.nonEmpty)

      val canHaveTypeArgs = typeElementResolved match {
        case ah: ScAnnotationsHolder if isFreestyleAnnotated(ah) => false
        case c: PsiClass                                         => c.hasTypeParameters
        case owner: ScTypeParametersOwner                        => owner.typeParameters.nonEmpty
        case _                                                   => false
      }

      if (!canHaveTypeArgs)
        return false

      typeElement.parents.find(!_.is[ScParenthesisedTypeElement]).orNull match {
        case ScParameterizedTypeElement(_, _)  => false
        case _: ScContextBound                 => false
        case (_: ScTypeArgs) childOf (gc: ScGenericCall) =>
          gc.referencedExpr match {
            case ResolvesTo(f: ScFunction) => noHigherKinds(f)
            case _                         => false
          }
        case (_: ScTypeArgs) childOf (parameterized: ScParameterizedTypeElement) =>
          parameterized.typeElement match {
            case ScSimpleTypeElement(ResolvesTo(target)) =>
              target match {
                case cons: ScPrimaryConstructor      => noHigherKinds(cons.containingClass)
                case owner: ScTypeParametersOwner    => noHigherKinds(owner)
                case _                               => false
              }
            case _ => false
          }
        case infix: ScInfixTypeElement if infix.left == typeElement || infix.rightOption.contains(typeElement) =>
          infix.operation.resolve() match {
            case owner: ScTypeParametersOwner => noHigherKinds(owner)
            case _                            => false
          }
        case _ =>
          //SCL-19477, this code is OK, no need in type argument
          //def f[T]: "42" = ???
          //val refOk: f.type = ???
          typeElementResolved match {
            case f: ScFunction => !f.isStable
            case _             => true
          }
      }
    }

    val needTypeArgsRes = needTypeArgs
    if (needTypeArgsRes) {
      holder.createErrorAnnotation(
        typeElement,
        ScalaBundle.message("type.takes.type.parameters", typeElement.getText),
        ProblemHighlightType.GENERIC_ERROR
      )
    }
  }
}
