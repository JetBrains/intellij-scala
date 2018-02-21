package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion._
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi._
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getContextOfType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_9
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters

class ScalaClassNameCompletionContributor extends ScalaCompletionContributor {

  import ScalaClassNameCompletionContributor._

  extend(
    CompletionType.BASIC,
    psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceElement]),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        completeClassName(result)(parameters, context)
        result.stopHere()
      }
    }
  )

  extend(
    CompletionType.BASIC,
    psiElement(),
    new CompletionProvider[CompletionParameters]() {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet): Unit =
        parameters.getPosition.getNode.getElementType match {
          case ScalaTokenTypes.tSTRING |
               ScalaTokenTypes.tMULTILINE_STRING => completeClassName(result)(parameters, context)
          case _ =>
        }
    }
  )
}

object ScalaClassNameCompletionContributor {

  private def completeClassName(result: CompletionResultSet)
                               (implicit parameters: CompletionParameters,
                                context: ProcessingContext): Unit =
    positionFromParameters(parameters) match {
      case dummyPosition if shouldRunClassNameCompletion(dummyPosition, result.getPrefixMatcher) =>
        completeClassName(dummyPosition, result)
      case _ =>
    }

  private[completion] def completeClassName(dummyPosition: PsiElement, result: CompletionResultSet)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Boolean = {
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
    val isInImport = getContextOfType(position, false, classOf[ScImportStmt]) != null
    val stableRefElement = getContextOfType(position, false, classOf[ScStableCodeReferenceElement])
    val onlyClasses = stableRefElement != null && !stableRefElement.getContext.isInstanceOf[ScConstructorPattern]

    val renamesMap = createRenamesMap(position)
    val maybeExpectedTypes = expectedTypeAfterNew(dummyPosition)

    implicit val project: Project = position.getProject

    def addTypeForCompletion(typeToImport: TypeToImport): Unit = {
      if (inReadAction(isExcluded(typeToImport))) return

      val TypeToImport(element, name) = typeToImport

      val isAccessible = invocationCount >= 2 || (element match {
        case member: PsiMember => ResolveUtils.isAccessible(member, position, forCompletion = true)
        case _ => true
      })
      if (!isAccessible) return

      if (lookingForAnnotations && !typeToImport.isAnnotationType) return
      element match {
        case _: ScClass | _: ScTrait | _: ScTypeAlias if !isInImport && !onlyClasses => return
        case _: ScObject if !isInImport && onlyClasses => return
        case _ =>
      }

      val lookups = (typeToImport, maybeExpectedTypes) match {
        case (ClassTypeToImport(clazz), Some(createLookups)) =>
          Seq(createLookups(clazz, renamesMap))
        case _ =>
          val nameShadow = renamesMap.get(name).collect {
            case (`element`, s) => s
          }

          new ScalaResolveResult(element, nameShadow = nameShadow).getLookupElement(
            isClassName = true,
            isInImport = isInImport,
            isInStableCodeReference = stableRefElement != null,
            isInSimpleString = inString
          )
      }

      import JavaConverters._
      result.addAllElements(lookups.asJava)
    }

    val QualNameToType = StdTypes.instance.QualNameToType
    for {
      clazz <- SyntheticClasses.get(project).all.valuesIterator
      if !QualNameToType.contains(clazz.qualifiedName)
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

    val manager = ScalaPsiManager.instance
    for {
      name <- manager.getStableTypeAliasesNames
      if prefixMatcher.prefixMatches(name)
      alias <- manager.getStableAliasesByName(name, position.resolveScope)
    } {
      addTypeForCompletion(TypeAliasToImport(alias))
    }

    for {
      (elem, name) <- renamesMap.values
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

  private[this] def isExcluded(`type`: TypeToImport)
                              (implicit project: Project): Boolean = `type` match {
    case ClassTypeToImport(classToImport) =>
      JavaCompletionUtil.isInExcludedPackage(classToImport, false)
    case TypeAliasToImport(alias) =>
      alias.containingClass match {
        case null => false
        case containingClass => JavaCompletionUtil.isInExcludedPackage(containingClass, false)
      }
    case PrefixPackageToImport(pack) =>
      JavaProjectCodeInsightSettings.getSettings(project).isExcluded(pack.getQualifiedName)
  }
}
