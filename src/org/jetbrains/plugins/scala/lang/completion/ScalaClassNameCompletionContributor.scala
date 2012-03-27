package org.jetbrains.plugins.scala.lang.completion

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.Consumer
import com.intellij.psi.PsiClass
import com.intellij.codeInsight.completion._
import lookups.{ScalaLookupItem, LookupElementManager}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScClass}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ResolveUtils}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScType}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.openapi.module.{ModuleUtil, Module}
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScNewTemplateDefinition}
import collection.mutable.HashMap
import org.jetbrains.plugins.scala.extensions.{toPsiNamedElementExt, toPsiClassExt}

class ScalaClassNameCompletionContributor extends CompletionContributor {
  import ScalaClassNameCompletionContributor._

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

object ScalaClassNameCompletionContributor {
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
    val invocationCount = parameters.getInvocationCount
    if (!insertedElement.getContainingFile.isInstanceOf[ScalaFile]) return true
    val lookingForAnnotations: Boolean = psiElement.afterLeaf("@").accepts(insertedElement)
    val isInImport = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScImportStmt]) != null
    val stableRefElement = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScStableCodeReferenceElement])
    val refElement = ScalaPsiUtil.getParentOfType(insertedElement, classOf[ScReferenceElement])
    val onlyClasses = stableRefElement != null && (stableRefElement.getContext match {
      case _: ScConstructorPattern => false
      case _ => true
    })

    val renamesMap = new HashMap[String, (String, PsiNamedElement)]()
    val reverseRenamesMap = new HashMap[String, PsiNamedElement]()

    refElement match {
      case ref: PsiReference => ref.getVariants().foreach {
        case s: ScalaLookupItem =>
          s.isRenamed match {
            case Some(name) =>
              renamesMap += ((s.element.name, (name, s.element)))
              reverseRenamesMap += ((name, s.element))
            case None =>
          }
        case _ =>
      }
      case _ =>
    }

    def addClass(psiClass: PsiClass) {
      val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
        def compute: Boolean = {
          JavaCompletionUtil.isInExcludedPackage(psiClass, true)
        }
      }).booleanValue
      val isAccessible = invocationCount >= 2 || ResolveUtils.isAccessible(psiClass, insertedElement)
      if (isExcluded) return
      if (!isAccessible) return
      if (lookingForAnnotations && !psiClass.isAnnotationType) return
      psiClass match {
        case _: ScClass | _: ScTrait if !isInImport && !onlyClasses => return
        case _: ScObject if !isInImport && onlyClasses => return
        case _ =>
      }
      val renamed = renamesMap.get(psiClass.getName).filter(_._2 == psiClass).map(_._1)
      for {
        el <- LookupElementManager.getLookupElement(new ScalaResolveResult(psiClass, nameShadow = renamed),
          isClassName = true, isInImport = isInImport, isInStableCodeReference = stableRefElement != null)
      } {
        if (afterNewPattern.accepts(parameters.getPosition, context)) {
          result.addElement(getLookupElementFromClass(expectedTypesAfterNew, psiClass, renamesMap))
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
      if (checkSynthetic || !ScType.baseTypesQualMap.contains(clazz.qualifiedName)) {
        addClass(clazz)
      }
    }

    val prefixMatcher = result.getPrefixMatcher
    AllClassesGetter.processJavaClasses(parameters, prefixMatcher, parameters.getInvocationCount <= 1,
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

    for ((name, elem) <- reverseRenamesMap) {
      elem match {
        case clazz: PsiClass =>
          if (prefixMatcher.prefixMatches(name) && !prefixMatcher.prefixMatches(clazz.name)) {
            addClass(clazz)
          }
        case _ =>
      }
    }

    false
  }
}
