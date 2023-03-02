package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tMULTILINE_STRING, tSTRING}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.withCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScTypeAlias}
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
    psiElement,
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

  import ScalaAfterNewCompletionContributor._
  import ScalaCompletionUtil._

  private[this] final case class CompletionState(place: PsiElement,
                                                 invocationCount: Int,
                                                 isInSimpleString: Boolean,
                                                 isInImport: Boolean,
                                                 isInStableCodeReference: Boolean,
                                                 classesOnly: Boolean,
                                                 annotationsOnly: Boolean) {

    val renamesMap: RenamesMap = createRenamesMap(place)

    def isValidClass(`class`: PsiClass): Boolean =
      isValidAndAccessible(`class`) &&
        (!annotationsOnly || `class`.isAnnotationType) &&
        isApplicable(`class`) &&
        !isExcluded(`class`)

    def createLookupElement(`class`: PsiClass,
                            maybeConstructor: Option[PropsConstructor]): LookupElement =
      maybeConstructor match {
        case Some(constructor) => constructor(`class`).createLookupElement(renamesMap)
        case _ => createLookupItemImpl(`class`)
      }

    def isValidAlias(alias: ScTypeAlias): Boolean =
      !annotationsOnly &&
        (isInImport || classesOnly) &&
        isValidAndAccessible(alias) &&
        !Option(alias.containingClass).exists(isExcluded)

    def createLookupItem(alias: ScTypeAlias): ScalaLookupItem =
      createLookupItemImpl(alias)

    private[this] def createLookupItemImpl(element: PsiNamedElement): ScalaLookupItem = {
      val renamed = renamesMap.get(element.name) match {
        case Some((`element`, name)) => Some(name)
        case _ => None
      }

      new ScalaResolveResult(
        element,
        renamed = renamed
      ).createLookupElement(
        isClassName = true,
        isInImport = isInImport,
        isInStableCodeReference = isInStableCodeReference,
        isInSimpleString = isInSimpleString
      )
    }

    private[this] def isValidAndAccessible(member: PsiMember): Boolean =
      member.isValid &&
        isAccessible(member, invocationCount)(place)

    private[this] def isApplicable(clazz: PsiClass): Boolean = clazz match {
      case _: ScClass => isInImport || classesOnly || place.isInScala3File
      case _: ScTrait => isInImport || classesOnly
      case _: ScObject => isInImport || !classesOnly
      case c: ScEnumCase => isInImport || (classesOnly && c.constructor.isDefined)
      case _ => true
    }
  }

  private[this] object CompletionState {

    def apply(place: PsiElement,
              invocationCount: Int,
              isInSimpleString: Boolean)
             (context: ProcessingContext): CompletionState = {
      val (isInStableCodeReference, classesOnly) = getContextOfType(place, false, classOf[ScStableCodeReference]) match {
        case null => (false, false)
        case codeReference => (true, !codeReference.getContext.is[ScConstructorPattern])
      }

      CompletionState(
        place,
        invocationCount,
        isInSimpleString,
        isInImport(place),
        isInStableCodeReference,
        classesOnly,
        annotationPattern.accepts(place, context)
      )
    }
  }

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
    val isInSimpleString = dummyPosition.getNode.getElementType match {
      case `tSTRING` | `tMULTILINE_STRING` => true
      case _ => false
    }

    val position = if (isInSimpleString) positionInString(dummyPosition) else dummyPosition
    if (!isInScalaContext(position, isInSimpleString)) return true

    val invocationCount = parameters.getInvocationCount
    implicit val project: Project = position.getProject
    implicit val state: CompletionState = CompletionState(position, invocationCount, isInSimpleString)(context)
    val maybeConstructor = expectedTypeAfterNew(dummyPosition, context)

    import scala.jdk.CollectionConverters._

    val QualNameToType = StdTypes.instance.QualNameToType
    val syntheticLookupElements = for {
      clazz <- SyntheticClasses.get(project).getAll
      if !QualNameToType.contains(clazz.qualifiedName)

      if state.isValidClass(clazz)
    } yield state.createLookupElement(clazz, maybeConstructor)

    result.addAllElements(syntheticLookupElements.asJava)

    val prefixMatcher = result.getPrefixMatcher
    AllClassesGetter.processJavaClasses(
      if (state.annotationsOnly) parameters.withInvocationCount(2) else parameters,
      prefixMatcher,
      invocationCount <= 1,
      new Consumer[PsiClass] {
        override def consume(`class`: PsiClass): Unit = `class` match {
          case _: PsiClassWrapper =>
          case _ =>
            //todo: filter according to position
            for {
              companionOrClass <- withCompanionModule(`class`)
              if state.isValidClass(companionOrClass)

              lookupElement = state.createLookupElement(companionOrClass, maybeConstructor)
            } result.addElement(lookupElement)
        }
      }
    )

    if (!state.annotationsOnly) {
      val manager = ScalaPsiManager.instance
      val lookupElements = for {
        name <- manager.getStableTypeAliasesNames
        if prefixMatcher.prefixMatches(name)

        alias <- manager.getTypeAliasesByName(name, position.resolveScope)
        if state.isValidAlias(alias)
      } yield state.createLookupItem(alias)

      result.addAllElements(lookupElements.asJava)
    }

    val lookupElements = for {
      (element, name) <- state.renamesMap.values
      if prefixMatcher.prefixMatches(name)
      if !prefixMatcher.prefixMatches(element.name)

      lookupElement = element match {
        case clazz: PsiClass if state.isValidClass(clazz) => state.createLookupElement(clazz, maybeConstructor)
        case alias: ScTypeAlias if state.isValidAlias(alias) => state.createLookupItem(alias)
        case _ => null
      }
      if lookupElement != null
    } yield lookupElement

    result.addAllElements(lookupElements.asJava)

    if (isInSimpleString) result.stopHere()
    false
  }

  private[this] def positionInString(place: PsiElement)
                                    (implicit parameters: CompletionParameters) =
    ScalaPsiElementFactory.createExpressionWithContextFromText(
      "s" + place.getText,
      place.getContext.getContext
    ).findElementAt(
      parameters.getOffset - parameters.getPosition.getTextRange.getStartOffset + 1
    )
}
