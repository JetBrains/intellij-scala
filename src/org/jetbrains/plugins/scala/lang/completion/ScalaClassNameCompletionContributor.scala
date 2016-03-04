package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, _}
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.{ClassTypeToImport, PrefixPackageToImport, TypeAliasToImport, TypeToImport}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.lookups.{LookupElementManager, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScType, StdType}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_9
import org.jetbrains.plugins.scala.project._

import scala.collection.mutable

class ScalaClassNameCompletionContributor extends ScalaCompletionContributor {
  import org.jetbrains.plugins.scala.lang.completion.ScalaClassNameCompletionContributor._
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
    withParent(classOf[ScReferenceElement]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      if (shouldRunClassNameCompletion(positionFromParameters(parameters), parameters, result.getPrefixMatcher)) {
        completeClassName(positionFromParameters(parameters), parameters, context, result)
      }
      result.stopHere()
    }
  })

  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      parameters.getPosition.getNode.getElementType match {
        case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
          if (shouldRunClassNameCompletion(positionFromParameters(parameters), parameters, result.getPrefixMatcher)) {
            completeClassName(positionFromParameters(parameters), parameters, context, result)
          }
        case _ =>
      }
    }
  })
}

object ScalaClassNameCompletionContributor {
  def completeClassName(dummyPosition: PsiElement, parameters: CompletionParameters, context: ProcessingContext,
                        result: CompletionResultSet): Boolean = {
    val expectedTypesAfterNew: Array[ScType] =
      if (afterNewPattern.accepts(dummyPosition, context)) {
        val element = dummyPosition
        val newExpr = PsiTreeUtil.getContextOfType(element, classOf[ScNewTemplateDefinition])
        //todo: probably we need to remove all abstracts here according to variance
        newExpr.expectedTypes().map {
          case ScAbstractType(_, lower, upper) => upper
          case tp => tp
        }
      } else Array.empty
    val (position, inString) = dummyPosition.getNode.getElementType match {
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tMULTILINE_STRING =>
        val position = dummyPosition
        //It's ok here to use parameters.getPosition
        val offsetInString = parameters.getOffset - parameters.getPosition.getTextRange.getStartOffset + 1
        val interpolated =
          ScalaPsiElementFactory.createExpressionFromText("s" + position.getText, position.getContext.getContext)
        (interpolated.findElementAt(offsetInString), true)
      case _ => (dummyPosition, false)
    }
    val invocationCount = parameters.getInvocationCount
    if (!inString && !ScalaPsiUtil.fileContext(position).isInstanceOf[ScalaFile]) return true
    val lookingForAnnotations: Boolean = psiElement.afterLeaf("@").accepts(position)
    val isInImport = ScalaPsiUtil.getContextOfType(position, false, classOf[ScImportStmt]) != null
    val stableRefElement = ScalaPsiUtil.getContextOfType(position, false, classOf[ScStableCodeReferenceElement])
    val refElement = ScalaPsiUtil.getContextOfType(position, false, classOf[ScReferenceElement])
    val onlyClasses = stableRefElement != null && !stableRefElement.getContext.isInstanceOf[ScConstructorPattern]

    val renamesMap = new mutable.HashMap[String, (String, PsiNamedElement)]()
    val reverseRenamesMap = new mutable.HashMap[String, PsiNamedElement]()

    refElement match {
      case ref: PsiReference => ref.getVariants.foreach {
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
          typeToImport match {
            case ClassTypeToImport(classToImport) =>
              JavaCompletionUtil.isInExcludedPackage(classToImport, false)
            case TypeAliasToImport(alias) =>
              val containingClass = alias.containingClass
              if (containingClass == null) return false
              JavaCompletionUtil.isInExcludedPackage(containingClass, false)
            case PrefixPackageToImport(pack) =>
              JavaProjectCodeInsightSettings.getSettings(pack.getProject).isExcluded(pack.getQualifiedName)
          }
        }
      })
      if (isExcluded) return

      val isAccessible =
        invocationCount >= 2 || (typeToImport.element match {
          case member: PsiMember => ResolveUtils.isAccessible(member, position, forCompletion = true)
          case _ => true
        })
      if (!isAccessible) return

      if (lookingForAnnotations && !typeToImport.isAnnotationType) return
      typeToImport.element match {
        case _: ScClass | _: ScTrait | _: ScTypeAlias if !isInImport && !onlyClasses => return
        case _: ScObject if !isInImport && onlyClasses => return
        case _ =>
      }
      val renamed = renamesMap.get(typeToImport.name).filter(_._2 == typeToImport.element).map(_._1)
      for {
        el <- LookupElementManager.getLookupElement(new ScalaResolveResult(typeToImport.element, nameShadow = renamed),
          isClassName = true, isInImport = isInImport, isInStableCodeReference = stableRefElement != null,
          isInSimpleString = inString)
      } {
        if (!afterNewPattern.accepts(dummyPosition, context)) result.addElement(el)
        else {
          typeToImport match {
            case ClassTypeToImport(clazz) =>
              result.addElement(getLookupElementFromClass(expectedTypesAfterNew, clazz, renamesMap))
            case _ =>
          }
        }
      }
    }

    val project = position.getProject

    val checkSynthetic = parameters.getOriginalFile.scalaLanguageLevel.map(_ < Scala_2_9).getOrElse(true)

    for {
      clazz <- SyntheticClasses.get(project).all.valuesIterator
      if checkSynthetic || !StdType.QualNameToType.contains(clazz.qualifiedName)
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
      alias <- ScalaPsiManager.instance(project).getStableAliasesByName(name, position.getResolveScope)
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

    if (inString) result.stopHere()
    false
  }
}
