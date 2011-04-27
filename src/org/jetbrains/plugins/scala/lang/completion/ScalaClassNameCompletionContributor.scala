package org.jetbrains.plugins.scala.lang.completion

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.Consumer
import com.intellij.psi.PsiClass
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScClass}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ResolveUtils}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

class ScalaClassNameCompletionContributor extends CompletionContributor {
  def completeClassName(parameters: CompletionParameters, result: CompletionResultSet): Boolean = {
    val insertedElement: PsiElement = parameters.getPosition
    if (!insertedElement.getContainingFile.isInstanceOf[ScalaFile]) return true
    val lookingForAnnotations: Boolean = psiElement.afterLeaf("@").accepts(insertedElement)
    val isInImport = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScImportStmt]) != null
    val refElement = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScStableCodeReferenceElement])
    val onlyClasses = refElement != null && (refElement.getContext match {
      case _: ScConstructorPattern => false
      case _ => true
    })
    AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher, parameters.getInvocationCount <= 1,
      new Consumer[PsiClass] {
        def consume(psiClass: PsiClass) {
          def addClass(psiClass: PsiClass) {
            val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
              def compute: Boolean = {
                return JavaCompletionUtil.isInExcludedPackage(psiClass)
              }
            }).booleanValue
            if (isExcluded) return
            if (lookingForAnnotations && !psiClass.isAnnotationType) return
            psiClass match {
              case _: ScClass | _: ScTrait if !isInImport && !onlyClasses => return
              case _: ScObject if !isInImport && onlyClasses => return
              case _ =>
            }
            for {
              (el, _, _) <- ResolveUtils.getLookupElement(new ScalaResolveResult(psiClass),
                isClassName = true, isInImport = isInImport)
            } result.addElement(el)
          }
          //todo: filter according to position
          ScalaPsiUtil.getCompanionModule(psiClass) match {
            case Some(c) => addClass(c)
            case _ =>
          }
          addClass(psiClass)
        }
      })
    false
  }

  extend(CompletionType.CLASS_NAME, psiElement, new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, matchingContext: ProcessingContext,
                       result: CompletionResultSet) {
      if (completeClassName(parameters, result)) return
      result.stopHere()
    }
  })

  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val prefix = result.getPrefixMatcher.getPrefix
      if (prefix.length() > 0 && prefix.substring(0, 1).capitalize == prefix.substring(0, 1)) {
        completeClassName(parameters, result)
      }
    }
  })
}
