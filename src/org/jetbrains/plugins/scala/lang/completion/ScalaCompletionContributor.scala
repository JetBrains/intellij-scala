package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import psi.api.ScalaFile
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.{PlatformPatterns}
import lexer.ScalaTokenTypes
import scala.util.Random
import psi.api.statements.ScFun
import psi.api.base.patterns.ScBindingPattern
import psi.ScalaPsiUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.ApplicationManager
import refactoring.util.ScalaNamesUtil
import psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}
import psi.impl.base.ScStableCodeReferenceElementImpl
import lang.resolve.processor.CompletionProcessor
import com.intellij.psi._
import lang.resolve.{ScalaResolveResult, ResolveUtils}
import psi.impl.expr.ScReferenceExpressionImpl
import psi.impl.base.types.ScTypeProjectionImpl
;

/**
 * @author Alexander Podkhalyuzin
 * Date: 16.05.2008
 */

class ScalaCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      parameters.getPosition.getParent match {
        case ref: ScReferenceElement => {
          def applyVariant(variant: Object): Unit = {
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
          def postProcessMethod(result: ScalaResolveResult): Unit = {
            applyVariant(ResolveUtils.getLookupElement(result))
          }
          ref match {
            case refImpl: ScStableCodeReferenceElementImpl =>
              refImpl.doResolve(refImpl,
                new CompletionProcessor(refImpl.getKinds(false), postProcess = postProcessMethod _)
              )
            case refImpl: ScReferenceExpressionImpl =>
              refImpl.doResolve(refImpl,
                new CompletionProcessor(refImpl.getKinds(false), collectImplicits = true,
                  postProcess = postProcessMethod _)
              )
            case refImpl: ScTypeProjectionImpl =>
              refImpl.doResolve(new CompletionProcessor(refImpl.getKinds(false), postProcess = postProcessMethod _))
            case _ =>
              for (variant <- ref.getVariants()) {
                applyVariant(variant)
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
      val text = ref match {
        case ref: PsiElement => ref.getText
        case ref: PsiReference => ref.getElement.getText //this case for anonymous method in ScAccessModifierImpl
      }
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