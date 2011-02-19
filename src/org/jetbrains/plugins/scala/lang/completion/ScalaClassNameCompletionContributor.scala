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

class ScalaClassNameCompletionContributor extends CompletionContributor {
  extend(CompletionType.CLASS_NAME, psiElement, new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, matchingContext: ProcessingContext, result: CompletionResultSet): Unit = {
      val insertedElement: PsiElement = parameters.getPosition
      if (!insertedElement.getContainingFile.isInstanceOf[ScalaFile]) return
      val lookingForAnnotations: Boolean = psiElement.afterLeaf("@").accepts(insertedElement)
      val isInImport = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScImportStmt]) != null
      val refElement = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScStableCodeReferenceElement])
      val onlyClasses = refElement != null && (refElement.getContext match {
        case _: ScConstructorPattern => false
        case _ => true
      })
      AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher, parameters.getInvocationCount <= 1,
        new Consumer[PsiClass] {
          def consume(psiClass: PsiClass): Unit = {
            def addClass(psiClass: PsiClass): Unit = {
              if (lookingForAnnotations && !psiClass.isAnnotationType) return
              psiClass match {
                case _: ScClass | _: ScTrait if !isInImport && !onlyClasses => return
                case _: ScObject if !isInImport && onlyClasses => return
                case _ =>
              }
              result.addElement(ResolveUtils.getLookupElement(new ScalaResolveResult(psiClass), isClassName = true)._1)
            }
            //todo: filter according to position
            ScalaPsiUtil.getCompanionModule(psiClass) match {
              case Some(c) => addClass(c)
              case _ =>
            }
            addClass(psiClass)
          }
        })
      result.stopHere
    }
  })
}
