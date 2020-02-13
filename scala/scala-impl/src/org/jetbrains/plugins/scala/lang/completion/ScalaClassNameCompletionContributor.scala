package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi._
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionContributor._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tMULTILINE_STRING, tSTRING}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{fileContext, getCompanionModule, getContextOfType}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScalaClassNameCompletionContributor extends ScalaCompletionContributor {

  import ScalaClassNameCompletionContributor._

  extend(
    CompletionType.BASIC,
    identifierWithParentPattern(classOf[ScReference]),
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
          case `tSTRING` | `tMULTILINE_STRING` =>
            completeClassName(result)(parameters, context)
          case _ =>
        }
    }
  )
}

object ScalaClassNameCompletionContributor {

  private def completeClassName(result: CompletionResultSet)
                               (implicit parameters: CompletionParameters,
                                context: ProcessingContext): Unit =
    positionFromParameters match {
      case dummyPosition if shouldRunClassNameCompletion(dummyPosition, result.getPrefixMatcher) =>
        completeClassName(dummyPosition, result)
      case _ =>
    }

  private[completion] def completeClassName(dummyPosition: PsiElement, result: CompletionResultSet)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Boolean = {
    val (position, inString) = dummyPosition.getNode.getElementType match {
      case `tSTRING` | `tMULTILINE_STRING` =>
        //It's ok here to use parameters.getPosition
        val offsetInString = parameters.getOffset - parameters.getPosition.getTextRange.getStartOffset + 1
        val interpolated = ScalaPsiElementFactory.createExpressionFromText(
          "s" + dummyPosition.getText,
          dummyPosition.getContext.getContext
        )
        (interpolated.findElementAt(offsetInString), true)
      case _ => (dummyPosition, false)
    }

    val invocationCount = parameters.getInvocationCount
    if (!inString && !fileContext(position).isInstanceOf[ScalaFile]) return true
    val lookingForAnnotations: Boolean = psiElement.afterLeaf("@").accepts(position)
    val isInImport = getContextOfType(position, false, classOf[ScImportStmt]) != null
    val stableRefElement = getContextOfType(position, false, classOf[ScStableCodeReference])
    val onlyClasses = stableRefElement != null && !stableRefElement.getContext.isInstanceOf[ScConstructorPattern]

    val renamesMap = createRenamesMap(position)
    val maybeExpectedTypes = expectedTypeAfterNew(dummyPosition)

    implicit val project: Project = position.getProject

    def createLookupElement(element: PsiNamedElement): Some[ScalaLookupItem] = {
      val renamed = renamesMap.get(element.name).collect {
        case (`element`, s) => s
      }

      new ScalaResolveResult(element, renamed = renamed).getLookupElement(
        isClassName = true,
        isInImport = isInImport,
        isInStableCodeReference = stableRefElement != null,
        isInSimpleString = inString
      ).asInstanceOf[Some[ScalaLookupItem]]
    }

    def isApplicable(clazz: PsiClass) = (clazz match {
      case _: ScClass |
           _: ScTrait => isInImport || onlyClasses
      case _: ScObject => isInImport || !onlyClasses
      case _ => true
    }) && (!lookingForAnnotations || clazz.isAnnotationType)

    def createLookupElementForClass(clazz: PsiClass): Option[ScalaLookupItem] =
      if (isValidAndAccessible(clazz, invocationCount)(position) &&
        isApplicable(clazz) &&
        !isExcluded(clazz)) {
        maybeExpectedTypes match {
          case Some(createLookups) => Some(createLookups(clazz, renamesMap))
          case _ => createLookupElement(clazz)
        }
      } else {
        None
      }

    def isValidAlias(alias: ScTypeAlias): Boolean =
      isValidAndAccessible(alias, invocationCount)(position) &&
        !Option(alias.containingClass).exists(isExcluded) &&
        (isInImport || onlyClasses)

    import collection.JavaConverters._

    val QualNameToType = StdTypes.instance.QualNameToType
    val syntheticLookupElements = for {
      clazz <- SyntheticClasses.get(project).all.values
      if !QualNameToType.contains(clazz.qualifiedName)

      lookupElement <- createLookupElementForClass(clazz)
    } yield lookupElement

    result.addAllElements(syntheticLookupElements.asJava)

    val prefixMatcher = result.getPrefixMatcher
    AllClassesGetter.processJavaClasses(
      if (lookingForAnnotations) parameters.withInvocationCount(2) else parameters,
      prefixMatcher,
      invocationCount <= 1,
      new Consumer[PsiClass] {
        override def consume(clazz: PsiClass): Unit = clazz match {
          case _: PsiClassWrapper =>
          case _ =>
            //todo: filter according to position
            val classes = clazz :: getCompanionModule(clazz).toList
            classes.flatMap(createLookupElementForClass).foreach(result.addElement)
        }
      }
    )

    if (!lookingForAnnotations) {
      val manager = ScalaPsiManager.instance
      val lookupElements = for {
        name <- manager.getStableTypeAliasesNames
        if prefixMatcher.prefixMatches(name)

        alias <- manager.getStableAliasesByName(name, position.resolveScope)
        if isValidAlias(alias)
      } yield createLookupElement(alias).get

      result.addAllElements(lookupElements.asJava)
    }

    val lookupElements = for {
      (element, name) <- renamesMap.values
      if prefixMatcher.prefixMatches(name)
      if !prefixMatcher.prefixMatches(element.name)

      lookupItem <- element match {
        case clazz: PsiClass => createLookupElementForClass(clazz)
        case alias: ScTypeAlias if !lookingForAnnotations && isValidAlias(alias) => createLookupElement(alias)
        case _ => None
      }
    } yield lookupItem

    result.addAllElements(lookupElements.asJava)

    if (inString) result.stopHere()
    false
  }

  private[this] def isValidAndAccessible(member: PsiMember,
                                         invocationCount: Int)
                                        (implicit place: PsiElement): Boolean =
    member.isValid && isAccessible(member, invocationCount)
}
