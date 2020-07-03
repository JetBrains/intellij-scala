package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiMethod, PsiNamedElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScalaConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class RemoveApplyIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.remove.unnecessary.apply")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val methodCallExpr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (methodCallExpr == null) return false

    methodCallExpr.getInvokedExpr match {
      case ref: ScReferenceExpression =>
        val range: TextRange = ref.nameId.getTextRange
        val offset = editor.getCaretModel.getOffset

        if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false
        if (ref.isQualified && ref.nameId.textMatches("apply")) return true
      case _ =>
    }

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    def countMethodCall(call: ScMethodCall): Int = {
      call.getInvokedExpr match {
        case call: ScMethodCall => 1 + countMethodCall(call)
        case _ => 1
      }
    }

    def showErrorHint(@Nls hint: String): Unit = {
      if (ApplicationManager.getApplication.isUnitTestMode) {
        throw new RuntimeException(hint)
      } else {
        HintManager.getInstance().showErrorHint(editor, hint)
      }
    }

    val expr: ScMethodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall], false)
    if (expr == null || !expr.isValid) return

    var start = expr.getInvokedExpr.asInstanceOf[ScReferenceExpression].nameId.getTextRange.getStartOffset - 1
    val buf = new StringBuilder
    var qualifier = expr.getInvokedExpr.asInstanceOf[ScReferenceExpression].qualifier.get
    buf.append(qualifier.getText)

    qualifier match {
      case parenth: ScParenthesisedExpr =>
        qualifier = parenth.innerElement.get
      case _ =>
    }

    qualifier match {
      case ref: ScReferenceExpression =>
        val resolved = ref.resolve()
        resolved match {
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
              showErrorHint(ScalaInspectionBundle.message("remove.apply.overloaded",
                namedElement.name))
              return
            }
          case _ =>
        }

      case call: ScMethodCall =>
        val cmc = countMethodCall(call)
        call.deepestInvokedExpr match {
          case ref: ScReferenceExpression =>
            val resolve: PsiElement = ref.resolve()
            resolve match {
              case fun: ScFunction =>
                val clauses = fun.effectiveParameterClauses
                if (clauses.length > 1 && clauses.last.isImplicit && clauses.length == cmc + 1) {
                  showErrorHint(ScalaInspectionBundle.message("remove.apply.implicit.parameter",
                    resolve.asInstanceOf[PsiNamedElement].name))
                  return
                }
              case _ => //all is ok
            }
          case _ => //all is ok
        }

      case templ: ScNewTemplateDefinition =>
        for {
          parent <- templ.extendsBlock.templateParents
          constrInvocation <- parent.constructorInvocation
          ref <- constrInvocation.reference
        } {
          ref.resolve() match {
            case ScalaConstructor(constr) =>
              val argsCount = constrInvocation.arguments.length
              val clauses = constr.effectiveParameterClauses
              if (clauses.length > 1 && clauses.last.isImplicit && clauses.length == argsCount + 1) {
                showErrorHint(ScalaInspectionBundle.message("remove.apply.constructor.implicit.parameter", constrInvocation.getText))
                return
              }
            case _ =>
          }
        }
      case _ =>
    }

    buf.append(expr.args.getText)
    inWriteAction {
      expr.replace(createExpressionFromText(buf.toString())(element.getManager))
      editor.getCaretModel.moveToOffset(start)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}
