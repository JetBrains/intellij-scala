package org.jetbrains.plugins.scala
package codeInspection.deprecation

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi._
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

/**
 * User: Alexander Podkhalyuzin
 * Date: 13.04.2010
 */
class ScalaDeprecationInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    def registerDeprecationProblem(description: String, elementToHighlight: PsiElement): Unit = {
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
            val description = s"Parameter name: $deprecatedName is deprecated."
            registerDeprecationProblem(description, elementToHighlight)
          }
        case named: PsiNamedElement =>
          val context = ScalaPsiUtil.nameContext(named)

          val isDeprecated = context.asOptionOf[PsiDocCommentOwner].exists {
            case Constructor(constr) => constr.isDeprecated || constr.containingClass.toOption.exists(_.isDeprecated)
            case other               => other.isDeprecated
          }

          if (isDeprecated) {
            val message = for {
              holder     <- context.asOptionOf[ScAnnotationsHolder]
              annotation <- holder.annotations("scala.deprecated").headOption
              message    <- ScalaPsiUtil.readAttribute(annotation, "value")
            } yield message

            val description = s"Symbol $name is deprecated. ${message.getOrElse("")}"
            registerDeprecationProblem(description, elementToHighlight)
          }
        case _ => ()
      }

    new ScalaElementVisitor {
      override def visitFunction(fun: ScFunction): Unit = {
        //todo: check super method is deprecated
      }

      override def visitReference(ref: ScReference): Unit =
        if (ref.isValid) ref.bind().foreach { checkDeprecated(_, ref.nameId, ref.refName) }

      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = visitReference(ref)
      override def visitTypeProjection(proj:     ScTypeProjection): Unit      = visitReference(proj)
    }
  }

  override def getID: String               = "ScalaDeprecation"
  override def isEnabledByDefault: Boolean = true
}
