package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import psi.api.expr.{ScPostfixExpr, ScInfixExpr, ScReferenceExpression}
import psi.api.ScalaFile
import psi.api.base.ScReferenceElement
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.{PlatformPatterns}
import lexer.ScalaTokenTypes
import scala.util.Random
import resolve.ResolveUtils
import psi.api.statements.ScFun
import psi.api.base.patterns.ScBindingPattern
import psi.ScalaPsiUtil
import com.intellij.psi.{PsiClass, PsiMember, PsiElement}
import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.ApplicationManager
import refactoring.util.ScalaNamesUtil;

/**
 * @author Alexander Podkhalyuzin
 * Date: 16.05.2008
 */

class ScalaCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      parameters.getPosition.getParent match {
        case ref: ScReferenceElement => {
          val variants: Array[Object] = ref.getVariants
          for (variant <- variants) {
            variant match {
              case (el: LookupElement, elem: PsiElement, _) => {
                elem match {
                  case fun: ScFun => result.addElement(el)
                  case clazz: PsiClass => {
                    val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
                      def compute: Boolean = {
                        return JavaCompletionUtil.isInExcludedPackage(clazz)
                      }
                    }).booleanValue

                    if (!isExcluded) {
                      result.addElement(el)
                    }
                  }
                  case memb: PsiMember => {
                    if (parameters.getInvocationCount > 1 ||
                            ResolveUtils.isAccessible(memb, parameters.getPosition)) result.addElement(el)
                  }
                  case patt: ScBindingPattern => {
                    val context = ScalaPsiUtil.nameContext(patt)
                    context match {
                      case memb: PsiMember => {
                        if (parameters.getInvocationCount > 1 ||
                            ResolveUtils.isAccessible(memb, parameters.getPosition)) result.addElement(el)
                      }
                      case _ => result.addElement(el)
                    }
                  }
                  case _ => result.addElement(el)
                }
              }
              case _ =>
            }
          }
          result.stopHere
        }
        case _ =>
      }
    }
  })

  override def advertise(parameters: CompletionParameters): String = {
    if (!parameters.getOriginalFile.isInstanceOf[ScalaFile]) return null
    val messages = Array[String](
      null
    )
    messages apply (new Random).nextInt(messages.size)
  }

  override def beforeCompletion(context: CompletionInitializationContext) = {
    val rulezzz = CompletionInitializationContext.DUMMY_IDENTIFIER
    val offset = context.getStartOffset() - 1
    val file = context.getFile
    val element = file.findElementAt(offset);
    val ref = file.findReferenceAt(offset)
    if (element != null && ref != null) {
      val text = ref.asInstanceOf[PsiElement].getText
      if (isOpChar(text(text.length - 1))) {
       context.setFileCopyPatcher(new DummyIdentifierPatcher("+++++++++++++++++++++++"))
     }
    }
    super.beforeCompletion(context)
  }

  private def isOpChar(c: Char): Boolean = {
    ScalaNamesUtil.isIdentifier("+" + c)
  }
}