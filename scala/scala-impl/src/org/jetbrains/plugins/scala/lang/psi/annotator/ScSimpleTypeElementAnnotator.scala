package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ResolvesTo, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner


trait ScSimpleTypeElementAnnotator extends Annotatable { self: ScSimpleTypeElement =>

  // TODO Shouldn't the ScExpressionAnnotator be enough?
  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    //todo: check bounds conformance for parameterized type
    checkAbsentTypeArgs(holder)
  }

  private def checkAbsentTypeArgs(holder: AnnotationHolder): Unit = {
    // Dirty hack(see SCL-12582): we shouldn't complain about missing type args since they will be added by a macro after expansion
    def isFreestyleAnnotated(ah: ScAnnotationsHolder): Boolean = {
      (ah.findAnnotationNoAliases("freestyle.free") != null) ||
        ah.findAnnotationNoAliases("freestyle.module") != null
    }
    def needTypeArgs: Boolean = {
      def noHigherKinds(owner: ScTypeParametersOwner) = !owner.typeParameters.exists(_.typeParameters.nonEmpty)

      val canHaveTypeArgs = reference.map(_.resolve()).exists {
        case ah: ScAnnotationsHolder if isFreestyleAnnotated(ah) => false
        case c: PsiClass => c.hasTypeParameters
        case owner: ScTypeParametersOwner => owner.typeParameters.nonEmpty
        case _ => false
      }

      if (!canHaveTypeArgs) return false

      getParent match {
        case ScParameterizedTypeElement(`self`, _) => false
        case tp: ScTypeParam if tp.contextBoundTypeElement.contains(this) => false
        case (_: ScTypeArgs) childOf (gc: ScGenericCall) =>
          gc.referencedExpr match {
            case ResolvesTo(f: ScFunction) => noHigherKinds(f)
            case _ => false
          }
        case (_: ScTypeArgs) childOf (parameterized: ScParameterizedTypeElement) =>
          parameterized.typeElement match {
            case ScSimpleTypeElement(Some(ResolvesTo(owner: ScTypeParametersOwner))) => noHigherKinds(owner)
            case ScSimpleTypeElement(Some(ResolvesTo(ScPrimaryConstructor.ofClass(c)))) => noHigherKinds(c)
            case _ => false
          }
        case infix: ScInfixTypeElement if infix.left == this || infix.rightOption.contains(this) =>
          infix.operation.resolve() match {
            case owner: ScTypeParametersOwner => noHigherKinds(owner)
            case _ => false
          }
        case _ => true
      }
    }

    if (needTypeArgs) {
      val annotation = holder.createErrorAnnotation(getTextRange,
        ScalaBundle.message("type.takes.type.parameters", getText))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }
}
