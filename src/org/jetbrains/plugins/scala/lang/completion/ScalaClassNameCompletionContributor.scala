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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScClass}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ResolveUtils}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScType}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.openapi.module.{ModuleUtil, Module}

class ScalaClassNameCompletionContributor extends CompletionContributor {
  import ScalaSmartCompletionContributor._
  def completeClassName(parameters: CompletionParameters, context: ProcessingContext,
                        result: CompletionResultSet): Boolean = {
    val expectedTypesAfterNew: Array[ScType] =
      if (afterNewPattern.accepts(parameters.getPosition, context)) {
        val element = parameters.getPosition
        val newExpr = PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])
        newExpr.expectedTypes().map(tp => tp match {
          case ScAbstractType(_, lower, upper) => upper
          case _ => tp
        })
      } else Array.empty
    val insertedElement: PsiElement = parameters.getPosition
    if (!insertedElement.getContainingFile.isInstanceOf[ScalaFile]) return true
    val lookingForAnnotations: Boolean = psiElement.afterLeaf("@").accepts(insertedElement)
    val isInImport = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScImportStmt]) != null
    val refElement = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScStableCodeReferenceElement])
    val onlyClasses = refElement != null && (refElement.getContext match {
      case _: ScConstructorPattern => false
      case _ => true
    })
    def addClass(psiClass: PsiClass) {
      val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
        def compute: Boolean = {
          JavaCompletionUtil.isInExcludedPackage(psiClass)
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
          isClassName = true, isInImport = isInImport, isInStableCodeReference = refElement != null)
      } {
        if (afterNewPattern.accepts(parameters.getPosition, context)) {
          result.addElement(getLookupElementFromClass(expectedTypesAfterNew, psiClass))
        } else {
          result.addElement(el)
        }
      }
    }

    val project = insertedElement.getProject
    val module: Module = ModuleUtil.findModuleForPsiElement(parameters.getOriginalPosition)
     val checkSynthetic = if (module == null) true else ScalaFacet.findIn(module).map(facet => {
       val version = facet.version
       if (version.length() < 3) true //let's think about 2.9
       else {
         val substring = version.substring(0, 3)
         try {
           substring.toDouble < 2.9 - Double.MinPositiveValue
         }
         catch {
           case n: NumberFormatException => true //let's think about 2.9
         }
       }
     }).getOrElse(true)
    for (clazz <- SyntheticClasses.get(project).all.valuesIterator) {
      if (checkSynthetic || !ScType.baseTypesQualMap.contains(clazz.getQualifiedName)) {
        addClass(clazz)
      }
    }

    AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher, parameters.getInvocationCount <= 1,
      new Consumer[PsiClass] {
        def consume(psiClass: PsiClass) {
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
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      if (completeClassName(parameters, context, result)) return
      result.stopHere()
    }
  })

  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
    withParent(classOf[ScReferenceElement]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      if (shouldRunClassNameCompletion(parameters, result.getPrefixMatcher)) {
        completeClassName(parameters, context, result)
      }
      result.stopHere()
    }
  })
}
