package org.jetbrains.plugins.scala.lang.completion

import com.intellij.patterns.PlatformPatterns
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScAssignStmt, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, Nothing, ScType}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import com.intellij.psi.{PsiMethod, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTyped

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  extend(CompletionType.SMART, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val element = parameters.getPosition
        if (element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER) return
        element.getParent match {
          case ref: ScReferenceExpression => {
            def expectedType: ScType = {
              ref.getParent match {
              // f = ref | f(...) = ref
                case assign: ScAssignStmt if assign.getRExpression == Some(ref) => {
                  val leftExpr = assign.getLExpression
                  leftExpr match {
                    case call: ScMethodCall => {
                      //todo:
                      return Nothing
                    }
                    case _ => {
                      //we can expect that the type is same for left and right parts.
                      return leftExpr.cachedType
                    }
                  }
                }
                case _ => return Nothing //todo:
              }
            }
            val tp = expectedType
            if (tp == Nothing) return
            val variants = ref.getVariants
            for (variant <- variants) {
              variant match {
                case (el: LookupElement, elem: PsiElement, subst: ScSubstitutor) => {
                  def checkType(typez: ScType): Unit = {
                    if (subst.subst(typez) conforms tp) result.addElement(el)
                  }
                  elem match {
                    case fun: ScSyntheticFunction => checkType(fun.retType)
                    case fun: ScFunction => checkType(fun.returnType)
                    case meth: PsiMethod => checkType(ScType.create(meth.getReturnType, meth.getProject))
                    case typed: ScTyped => checkType(typed.calcType)
                    case _ =>
                  }
                }
                case _ =>
              }
            }
          }
          case _ => //todo:
        }
      }
    })
}