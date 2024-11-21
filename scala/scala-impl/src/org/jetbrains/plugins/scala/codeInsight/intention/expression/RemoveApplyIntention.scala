package org.jetbrains.plugins.scala.codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScalaConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.annotation.tailrec
import scala.collection.mutable

class RemoveApplyIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.remove.unnecessary.apply")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    Option(methodCallExpr).map(_.getInvokedExpr) match {
      case Some(ref: ScReferenceExpression) =>
        val range: TextRange = ref.nameId.getTextRange
        val offset = editor.getCaretModel.getOffset

        (range.getStartOffset <= offset && offset <= range.getEndOffset) &&
          ref.isQualified &&
          ref.nameId.textMatches("apply") &&
          ref.qualifier.filterByType[ScMethodCall].forall(!_.args.isColonArgs) &&
          buildReplacement(methodCallExpr).isDefined
      case _ =>
        false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val expr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (expr != null && expr.isValid) {
      buildReplacement(expr).foreach { case (replacementText, start) =>
        IntentionPreviewUtils.write { () =>
          expr.replace(createExpressionFromText(replacementText, element)(element.getManager))
          editor.getCaretModel.moveToOffset(start)
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }
  }

  private def buildReplacement(expr: ScMethodCall): Option[(String, Int)] = {
    def countNonUsingMethodCall(call: ScMethodCall): Int = {
      (if (call.args.isUsing) 0 else 1) + (call.getInvokedExpr match {
        case call: ScMethodCall => countNonUsingMethodCall(call)
        case _ => 0
      })
    }

    @tailrec
    def dig(e: ScExpression): ScExpression =
      e match {
        case ScParenthesisedExpr(inner) => dig(inner)
        case ScGenericCall(actual, _) => dig(actual)
        case _ => e
      }

    val buf = new mutable.StringBuilder
    val qualifier = expr.getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get
    var start = qualifier.endOffset
    buf.append(qualifier.getText)

    /**
     * Checks whether the call is using an implicit argument without giving it explicitly.
     *
     * For example 'implicitly[Int].apply()' should not be transformed into 'implicitly[Int]()'
     * But 'implicitly[Int](1).apply()' can be transformed into 'implicitly[Int](1)()
     *
     * This also applies in Scala 3! But there we have to take using clauses into account as well.
     *
     * For example 'summon[Int].apply()' can be transformed into 'summon[Int]()'
     * because explicitly providing the implicit Int would look like 'summon[Int](using 1)()'
     */
    def usesImplicitArg(fun: ScParameterOwner.WithContextBounds, nonUsingCallClauses: Int): Boolean = {
      val clauses = fun.effectiveParameterClauses
      clauses.lastOption.exists(_.isImplicit) && clauses.count(!_.isUsing) == nonUsingCallClauses + 1
    }

    dig(qualifier) match {
      case ref: ScReferenceExpression =>
        val resolved = ref.resolve()
        resolved match {
          case fun: ScFunction if usesImplicitArg(fun, 0) =>
            return None
          case namedElement: PsiNamedElement =>
            val name = namedElement.name
            val clazz: Option[ScTemplateDefinition] = expr.getParent match {
              case _ if expr.isInstanceOf[ScClassParameter] =>
                Option(PsiTreeUtil.getParentOfType(expr, classOf[ScTemplateDefinition]))
              case _: ScEarlyDefinitions =>
                Option(PsiTreeUtil.getParentOfType(expr, classOf[ScTemplateDefinition]))
              case _: ScTemplateBody =>
                Option(PsiTreeUtil.getParentOfType(expr, classOf[ScTemplateDefinition]))
              case _ => None
            }

            var flag = false
            if (clazz.isDefined) {
              val signs = clazz.get.allSignatures

              for (sign <- signs if !flag) {
                sign.namedElement match {
                  case function: ScFunction =>
                    if (function.name == name && resolved != function) {
                      flag = true
                    } else if (resolved == function) {
                      if (function.parameters.isEmpty) {
                        buf.append("()")
                        start = start + 2
                      }
                    }
                  case method: PsiMethod =>
                    if (method.name == name && resolved != method) {
                      flag = true
                    } else if (resolved == method) {
                      if (method.parameters.isEmpty) {
                        buf.append("()")
                        start = start + 2
                      }
                    }
                  case _ =>
                }
              }
            }

            if (flag) {
              return None
            }
          case _ =>
        }

      case call: ScMethodCall =>
        call.deepestInvokedExpr match {
          case ResolvesTo(fun: ScFunction) if usesImplicitArg(fun, countNonUsingMethodCall(call)) =>
              return None
          case _ => //all is ok
        }

      case templ: ScNewTemplateDefinition =>
        for {
          parent           <- templ.extendsBlock.templateParents
          constrInvocation <- parent.firstParentClause
          ref              <- constrInvocation.reference
        } {
          ref.resolve() match {
            case ScalaConstructor(constr) =>
              val nonUsingArgCount = constrInvocation.arguments.count(!_.isUsing)
              if (usesImplicitArg(constr, nonUsingArgCount)) {
                return None
              }
            case _ =>
          }
        }
      case _ =>
    }

    buf.append(expr.args.getText)
    Some((buf.toString(), start))
  }
}
