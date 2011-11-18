package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import psi.api.ScalaFile
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns
import lexer.ScalaTokenTypes
import scala.util.Random
import psi.api.statements.ScFun
import psi.api.base.patterns.ScBindingPattern
import psi.ScalaPsiUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.ApplicationManager
import refactoring.util.ScalaNamesUtil
import psi.impl.base.ScStableCodeReferenceElementImpl
import lang.resolve.processor.CompletionProcessor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import lang.resolve.{ScalaResolveResult, ResolveUtils}
import psi.impl.expr.ScReferenceExpressionImpl
import psi.impl.base.types.ScTypeProjectionImpl
import com.intellij.codeInsight.lookup.LookupElement
import psi.api.toplevel.imports.ScImportStmt
import psi.api.expr.ScNewTemplateDefinition
import psi.types.{ScAbstractType, ScType}
import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import psi.api.statements.params.ScClassParameter
import psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}

/**
 * @author Alexander Podkhalyuzin
 * Date: 16.05.2008
 */

class ScalaCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val expectedTypesAfterNew: Array[ScType] =
      if (afterNewPattern.accepts(parameters.getPosition, context)) {
        val element = parameters.getPosition
        val newExpr = PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])
        newExpr.expectedTypes().map(tp => tp match {
          case ScAbstractType(_, lower, upper) => upper
          case _ => tp
        })
      } else Array.empty
      //if prefix is capitalized, class name completion is enabled
      val classNameCompletion = shouldRunClassNameCompletion(parameters, result.getPrefixMatcher)
      parameters.getPosition.getParent match {
        case ref: ScReferenceElement => {
          val isInImport = ScalaPsiUtil.getParentOfType(ref, classOf[ScImportStmt]) != null
          def applyVariant(variant: Object) {
            variant match {
              case (el: LookupElement, elem: PsiElement, _) => {
                elem match {
                  case fun: ScFun => result.addElement(el)
                  case clazz: PsiClass => {
                    val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
                      def compute: Boolean = {
                        JavaCompletionUtil.isInExcludedPackage(clazz)
                      }
                    }).booleanValue

                    if (!isExcluded && !classNameCompletion) {
                      if (afterNewPattern.accepts(parameters.getPosition, context)) {
                        result.addElement(getLookupElementFromClass(expectedTypesAfterNew, clazz))
                      } else {
                        result.addElement(el)
                      }
                    }
                  }
                  case param: ScClassParameter =>
                    result.addElement(el)
                  case memb: PsiMember => {
                    if (parameters.getInvocationCount > 1 || ResolveUtils.isAccessible(memb, parameters.getPosition))
                      result.addElement(el)
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
          def postProcessMethod(result: ScalaResolveResult) {
            import org.jetbrains.plugins.scala.lang.psi.types.Nothing
            val qualifier = result.fromType.getOrElse(Nothing)
            for (variant <- ResolveUtils.getLookupElement(result, isInImport = isInImport, qualifierType = qualifier,
              isInStableCodeReference = ref.isInstanceOf[ScStableCodeReferenceElement])) {
              applyVariant(variant)
            }
          }
          ref match {
            case refImpl: ScStableCodeReferenceElementImpl =>
              refImpl.doResolve(refImpl,
                new CompletionProcessor(refImpl.getKinds(false, true), postProcess = postProcessMethod _)
              )
            case refImpl: ScReferenceExpressionImpl =>
              refImpl.doResolve(refImpl,
                new CompletionProcessor(refImpl.getKinds(false, true), collectImplicits = true,
                  postProcess = postProcessMethod _)
              )
            case refImpl: ScTypeProjectionImpl =>
              refImpl.doResolve(new CompletionProcessor(refImpl.getKinds(false, true), postProcess = postProcessMethod _))
            case _ =>
              for (variant <- ref.getVariants()) {
                applyVariant(variant)
              }
          }
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

override def beforeCompletion(context: CompletionInitializationContext) {
    val offset = context.getStartOffset - 1
    val file = context.getFile
    val element = file.findElementAt(offset);
    val ref = file.findReferenceAt(offset)
    if (element != null && ref != null) {
      val text = ref match {
        case ref: PsiElement => ref.getText
        case ref: PsiReference => ref.getElement.getText //this case for anonymous method in ScAccessModifierImpl
      }
      val rest = ref match {
        case ref: PsiElement => text.substring(offset - ref.getTextRange.getStartOffset + 1)
        case ref: PsiReference => text.substring(offset - ref.getElement.getTextRange.getStartOffset + 1)
      }
      if (isOpChar(text(text.length - 1))) {
        context.setDummyIdentifier("+++++++++++++++++++++++")
      } else if (ScalaNamesUtil.isKeyword(rest)) {
        context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER)
      } else {
        context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
      }
    } else {
      val actualElement = file.findElementAt(offset + 1)
      if (actualElement != null && ScalaNamesUtil.isKeyword(actualElement.getText)) {
        context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER)
      } else {
        context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
      }
    }
    super.beforeCompletion(context)
  }

  private def isOpChar(c: Char): Boolean = {
    ScalaNamesUtil.isIdentifier("+" + c)
  }
}