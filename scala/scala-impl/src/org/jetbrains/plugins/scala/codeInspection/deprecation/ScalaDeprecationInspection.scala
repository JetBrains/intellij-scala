package org.jetbrains.plugins.scala
package codeInspection
package deprecation

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.deprecation.ScalaDeprecationInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScAnnotationsHolder, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScalaDeprecationInspection extends LocalInspectionTool {

  override def getID: String = "ScalaDeprecation"

  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    def registerDeprecationProblem(@Nls description: String, elementToHighlight: PsiElement): Unit = {
      val descriptor = holder.getManager.createProblemDescriptor(
        elementToHighlight,
        description,
        true,
        ProblemHighlightType.LIKE_DEPRECATED,
        isOnTheFly
      )
      holder.registerProblem(descriptor)
    }

    def checkDeprecated(result: ScalaResolveResult, elementToHighlight: PsiElement, name: String): Unit =
      result.element match {
        case param: ScParameter if result.isNamedParameter && !ScalaNamesUtil.equivalent(param.name, name) =>
          param.deprecatedName.foreach { deprecatedName =>
            registerDeprecationProblem(ScalaInspectionBundle.message("parameter.name.is.deprecated", deprecatedName), elementToHighlight)
          }
        case named: PsiNamedElement =>
          val context = ScalaPsiUtil.nameContext(named)

          val deprecatedElement = context.asOptionOf[PsiDocCommentOwner].flatMap {
            case Constructor(constr) if constr.isDeprecated => Some(constr)
            case Constructor.ofClass(clazz) if clazz.isDeprecated => Some(clazz)
            case func@ScFunction.inSynthetic(clazz) if func.isApplyMethod && clazz.isDeprecated => Some(clazz)
            case other if other.isDeprecated => Some(other)
            case _: ScFunction =>
              result.getActualElement.asOptionOfUnsafe[PsiDocCommentOwner].filter(_.isDeprecated)
            case _ =>
              None
          }

          deprecatedElement.foreach { deprecatedElement =>
            val message = deprecationMessage(deprecatedElement).getOrElse("")
            registerDeprecationProblem(ScalaInspectionBundle.message("symbol.name.is.deprecated.with.message", name, message), elementToHighlight)
          }
        case _ => ()
      }

    def checkOverridingDeprecated(superMethod: PsiMethod, method: ScFunction): Unit = {
      superMethod match {
        case owner if owner.isDeprecated =>
          val message = deprecationMessage(owner).getOrElse("")
          registerDeprecationProblem(ScalaInspectionBundle.message("super.method.name.is.deprecated.with.message", method.name, message), method.nameId)
        case owner  if owner.hasAnnotation("scala.deprecatedOverriding") =>
          val message = deprecationMessage(owner).getOrElse("")
          registerDeprecationProblem(ScalaInspectionBundle.message("overriding.is.deprecated", method.name, message), method.nameId)
        case _ =>
      }
    }

    new ScalaElementVisitor {

      override def visitFunction(fun: ScFunction): Unit =
        if (!fun.isLocal) {
          fun.superMethods.foreach(checkOverridingDeprecated(_, fun))
        }

      override def visitReference(ref: ScReference): Unit =
        if (ref.isValid) {
          val resolveResult = ref.bind()
          resolveResult.foreach(checkDeprecated(_, ref.nameId, ref.refName))
        }

      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = visitReference(ref)
      override def visitTypeProjection(proj: ScTypeProjection): Unit = visitReference(proj)
    }
  }
}

object ScalaDeprecationInspection {

  private val ScalaDeprecatedAnnotation = "scala.deprecated"
  private val ScalaDeprecatedAnnotationMessageField = "value"

  private def deprecationMessage(commentOwner: PsiDocCommentOwner): Option[String] =
    for {
      holder     <- commentOwner.asOptionOfUnsafe[ScAnnotationsHolder]
      annotation <- holder.annotations(ScalaDeprecatedAnnotation).headOption
      message    <- ScalaPsiUtil.readAttribute(annotation, ScalaDeprecatedAnnotationMessageField)
    } yield message
}
