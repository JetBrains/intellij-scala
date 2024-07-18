package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile, PsiMethod}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.codeInspection.parentheses
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall, ScParenthesisedExpr, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

import scala.annotation.tailrec

object ScUnderscoreSectionAnnotator extends ElementAnnotator[ScUnderscoreSection] {

  override def annotate(element: ScUnderscoreSection, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    checkUnboundUnderscore(element)
    // TODO (otherwise there's no type conformance check)
    // super.visitUnderscoreExpression  }

    if (typeAware) {
      if (element.isInScala3File) {
        checkNonFunctionType(element)
      }
    }
  }

  private def checkUnboundUnderscore(under: ScUnderscoreSection)
                                    (implicit holder: ScalaAnnotationHolder): Unit = {
    if (under.textMatches("_")) {
      under.parentOfType(classOf[ScValueOrVariable], strict = false).foreach {
        case varDef @ ScVariableDefinition.expr(_) if varDef.expr.contains(under) =>
          if (varDef.containingClass == null) {
            val error = ScalaBundle.message("local.variables.must.be.initialized")
            holder.createErrorAnnotation(under, error)
          } else if (varDef.typeElement.isEmpty) {
            val error = ScalaBundle.message("unbound.placeholder.parameter")
            holder.createErrorAnnotation(under, error)
          } else if (varDef.typeElement.exists(_.is[ScLiteralTypeElement])) {
            holder.createErrorAnnotation(varDef.typeElement.get, ScalaBundle.message("default.init.prohibited.literal.types"))
          }
        case valDef @ ScPatternDefinition.expr(_) if valDef.expr.contains(under) =>
          holder.createErrorAnnotation(under, ScalaBundle.message("unbound.placeholder.parameter"))
        case _ =>
        // TODO SCL-2610 properly detect unbound placeholders, e.g. ( { _; (_: Int) } ) and report them.
        //  val error = ScalaBundle.message("unbound.placeholder.parameter")
        //  val annotation: Annotation = holder.createErrorAnnotation(under, error)
      }
    }
  }

  /**
   * Report error if underscore syntax is used with a non-function type, for example: {{{
   *   val f1 = foo1 _
   *   val f2 = foo2 _
   *   def foo1: Int = ???
   *   val foo2: Int = ???
   * }}}
   */
  private def checkNonFunctionType(under: ScUnderscoreSection)
                                  (implicit holder: ScalaAnnotationHolder): Unit =
    under.bindingExpr.foreach { expr =>
      if (!isFunctionWithNonContextClauses(expr)) {
        val typeText = expr.`type`().fold[String](_ => "", _.widen.presentableText(TypePresentationContext(under), Context.Default))
        holder.createErrorAnnotation(
          under,
          ScalaBundle.message("only.function.types.can.be.followed.by.underscore", typeText),
          new RewriteToFunctionValueAction(under, expr)
        )
      }
    }

  // NOTE: in theory, we could reuse something like ParameterlessAccessInspection.HasFunctionType.
  // However, it returns not those types we expect in Scala 3.
  // For example, if we have a function `def foo(): Int` and a function reference `foo` / `foo_`
  // it will detect the type of it as `Int` not as `() => Int`.
  // Maybe in future it will be fixed uniformly and we can reuse existing code.
  @tailrec
  private def isFunctionWithNonContextClauses(expr: ScExpression): Boolean =
    expr match {
      case ref: ScReference =>
        val resolveResult = ref.bind()
        resolveResult match {
          case Some(ScalaResolveResult.withActual(element)) =>
            element match {
              case fun: ScFunction =>
                !fun.paramClauses.clauses.forall(_.isImplicit)
              case _: PsiMethod =>
                true //java method always has parameter clauses
              case _ =>
                false
            }
          case _ =>
            false
        }
      case genericCall: ScGenericCall =>
        isFunctionWithNonContextClauses(genericCall.referencedExpr)
      case ScParenthesisedExpr(inner) =>
        isFunctionWithNonContextClauses(inner)
      case _ =>
        false
    }

  private class RewriteToFunctionValueAction(under: ScUnderscoreSection, expr: ScExpression) extends IntentionAction {
    override def startInWriteAction: Boolean = true

    override def getText: String = ScalaBundle.message("rewrite.to.function.value")

    // Return the same text as in getText.
    // It's not clear why it would be different for the quick fix which is not registered anywhere in settings.
    override def getFamilyName: String = getText

    override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean = under.isValid

    override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
      implicit val p: Project = project
      val newElementText = s"(() => ${expr.getText})"
      val newElement = ScalaPsiElementFactory.createElementFromText[PsiElement](newElementText, psiFile.features)
      val newElementReplaced = under.replace(newElement)
      newElementReplaced match {
        case expr: ScExpression =>
          parentheses.removeUnnecessaryParentheses(psiFile, expr)
        case _ =>
      }
    }
  }
}
