package org.jetbrains.plugins.scala
package codeInspection.deprecation

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScAnnotationsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult


/**
 * User: Alexander Podkhalyuzin
 * Date: 13.04.2010
 */

class ScalaDeprecationInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    def checkDeprecated(result: ScalaResolveResult, elementToHighlight: PsiElement, name: String) {
      val refElement = result.element
      refElement match {
        case param: ScParameter if result.isNamedParameter &&
          !ScalaNamesUtil.equivalent(param.name, name) && param.deprecatedName.nonEmpty =>
          val description: String = s"Parameter name ${param.deprecatedName.get} is deprecated"
          holder.registerProblem(holder.getManager.createProblemDescriptor(elementToHighlight, description, true,
            ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly))
          return
        case _: PsiNamedElement =>
        case _ => return
      }
      val context = ScalaPsiUtil.nameContext(refElement.asInstanceOf[PsiNamedElement])
      context match {
        case doc: PsiDocCommentOwner =>
          doc match {
            case _: ScPrimaryConstructor =>
            case f: PsiMethod if f.isConstructor =>
            case _ => if (!doc.isDeprecated) return
          }
          if (!doc.isDeprecated && !Option(doc.containingClass).exists(_.isDeprecated)) return
        case _ => return
      }
      val message = for {
        holder <- context.asOptionOf[ScAnnotationsHolder]
        annotation <- holder.annotations("scala.deprecated").headOption
        message <- ScalaPsiUtil.readAttribute(annotation, "value")
      } yield message
      
      val description: String = Seq(Some("Symbol " + name + " is deprecated"),  message).flatten.mkString(". ")
      holder.registerProblem(holder.getManager.createProblemDescriptor(elementToHighlight, description, true,
        ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly))
    }

    new ScalaElementVisitor {
      override def visitFunction(fun: ScFunction) {
        //todo: check super method is deprecated
      }

      override def visitReference(ref: ScReferenceElement) {
        if (!ref.isValid) return
        ref.bind().foreach {
          checkDeprecated(_, ref.nameId, ref.refName)
        }
      }

      override def visitReferenceExpression(ref: ScReferenceExpression) {
        visitReference(ref)
      }

      override def visitTypeProjection(proj: ScTypeProjection) {
        visitReference(proj)
      }
    }
  }

  override def getID: String = {
    "ScalaDeprecation"
  }

  override def isEnabledByDefault: Boolean = {
    true
  }

}