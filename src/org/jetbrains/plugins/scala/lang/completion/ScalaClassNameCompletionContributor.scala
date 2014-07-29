package org.jetbrains.plugins.scala
package lang.completion

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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScObject, ScTrait, ScClass}
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
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.extensions.{toPsiNamedElementExt, toPsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.config.ScalaVersionUtil
import scala.collection.mutable
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.{TypeAliasToImport, ClassTypeToImport, TypeToImport}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

class ScalaClassNameCompletionContributor extends CompletionContributor {
  import ScalaClassNameCompletionContributor._
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
        //todo: probably we need to remove all abstracts here according to variance
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
    val onlyClasses = stableRefElement != null && !stableRefElement.getContext.isInstanceOf[ScConstructorPattern]

    val renamesMap = new mutable.HashMap[String, (String, PsiNamedElement)]()
    val reverseRenamesMap = new mutable.HashMap[String, PsiNamedElement]()

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

    def addTypeForCompletion(typeToImport: TypeToImport) {
      val isExcluded: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
        def compute: Boolean = {
          val clazz = typeToImport match {
            case ClassTypeToImport(clazz) => clazz
            case TypeAliasToImport(alias) =>
              val containingClass = alias.containingClass
              if (containingClass == null) return false
              containingClass
          }
          JavaCompletionUtil.isInExcludedPackage(clazz, false)
        }
      })
      if (isExcluded) return

      val isAccessible =
        invocationCount >= 2 || ResolveUtils.isAccessible(typeToImport.element, insertedElement, forCompletion = true)
      if (!isAccessible) return

      if (lookingForAnnotations && !typeToImport.isAnnotationType) return
      typeToImport.element match {
        case _: ScClass | _: ScTrait | _: ScTypeAlias if !isInImport && !onlyClasses => return
        case _: ScObject if !isInImport && onlyClasses => return
        case _ =>
      }
      val renamed = renamesMap.get(typeToImport.name).filter(_._2 == typeToImport).map(_._1)
      for {
        el <- LookupElementManager.getLookupElement(new ScalaResolveResult(typeToImport.element, nameShadow = renamed),
          isClassName = true, isInImport = isInImport, isInStableCodeReference = stableRefElement != null)
      } {
        if (!afterNewPattern.accepts(parameters.getPosition, context)) result.addElement(el)
        else {
          typeToImport match {
            case ClassTypeToImport(clazz) =>
              result.addElement(getLookupElementFromClass(expectedTypesAfterNew, clazz, renamesMap))
            case _ =>
          }
        }
      }
    }

    val project = insertedElement.getProject

    import org.jetbrains.plugins.scala.config.ScalaVersionUtil._
    val checkSynthetic = ScalaVersionUtil.isGeneric(parameters.getOriginalFile, true, SCALA_2_7, SCALA_2_8)
    for {
      clazz <- SyntheticClasses.get(project).all.valuesIterator
      if checkSynthetic || !ScType.baseTypesQualMap.contains(clazz.qualifiedName)
    } addTypeForCompletion(ClassTypeToImport(clazz))

    val prefixMatcher = result.getPrefixMatcher
    AllClassesGetter.processJavaClasses(if (lookingForAnnotations) parameters.withInvocationCount(2) else parameters,
      prefixMatcher, parameters.getInvocationCount <= 1, new Consumer[PsiClass] {
        def consume(psiClass: PsiClass) {
          //todo: filter according to position
          if (psiClass.isInstanceOf[PsiClassWrapper]) return
          ScalaPsiUtil.getCompanionModule(psiClass).foreach(clazz => addTypeForCompletion(ClassTypeToImport(clazz)))
          addTypeForCompletion(ClassTypeToImport(psiClass))
        }
      })

    for {
      name <- ScalaPsiManager.instance(project).getStableTypeAliasesNames
      if prefixMatcher.prefixMatches(name)
      alias <- ScalaPsiManager.instance(project).getStableAliasesByName(name, insertedElement.getResolveScope)
    } {
      addTypeForCompletion(TypeAliasToImport(alias))
    }

    for {
      (name, elem: PsiNamedElement) <- reverseRenamesMap
      if prefixMatcher.prefixMatches(name)
      if !prefixMatcher.prefixMatches(elem.name)
    } {
      elem match {
        case clazz: PsiClass => addTypeForCompletion(ClassTypeToImport(clazz))
        case ta: ScTypeAlias => addTypeForCompletion(TypeAliasToImport(ta))
        case _ =>
      }
    }

    false
  }
}
