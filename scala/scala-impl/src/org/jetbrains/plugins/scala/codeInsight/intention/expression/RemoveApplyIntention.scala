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
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
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
    def countMethodCall(call: ScMethodCall): Int = {
      call.getInvokedExpr match {
        case call: ScMethodCall => 1 + countMethodCall(call)
        case _ => 1
      }
    }

    @tailrec
    def dig(e: ScExpression): ScExpression =
      e match {
        case ScParenthesisedExpr(inner) => dig(inner)
        case ScGenericCall(actual, _) => dig(actual)
        case _ => e
      }

    var start = expr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getTextRange.getStartOffset - 1
    val buf = new mutable.StringBuilder
    val qualifier = expr.getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get
    buf.append(qualifier.getText)

    def checkFun(fun: ScFunction, currentCalledClauses: Int): Boolean = {
      // TODO: this needs probably be fixed for using clauses
      val clauses = fun.effectiveParameterClauses
      clauses.lastOption.exists(_.isImplicit) && clauses.length == currentCalledClauses + 1
    }

    dig(qualifier) match {
      case ref: ScReferenceExpression =>
        val resolved = ref.resolve()
        resolved match {
          case fun: ScFunction if checkFun(fun, 0) =>
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
        val cmc = countMethodCall(call)
        call.deepestInvokedExpr match {
          case ResolvesTo(fun: ScFunction) if checkFun(fun, cmc) =>
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
              val argsCount = constrInvocation.arguments.length
              val clauses = constr.effectiveParameterClauses
              if (clauses.length > 1 && clauses.last.isImplicit && clauses.length == argsCount + 1) {
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
