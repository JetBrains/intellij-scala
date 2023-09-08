package org.jetbrains.plugins.scala.lang

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.patterns._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil.{getContextOfType, getParentOfType}
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.caches.BlockModificationTracker.hasStableType
import org.jetbrains.plugins.scala.caches.{CachesUtil, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaByExpectedTypeWeigher
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr, ScExpression, ScNewTemplateDefinition, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

import java.util.function.Predicate

package object completion {

  import PlatformPatterns.psiElement
  import ScalaTokenTypes._

  @inline def condition[T](debugName: String)(predicate: Predicate[T]): PatternCondition[T] = new PatternCondition[T](debugName) {
    override def accepts(element: T, context: ProcessingContext): Boolean = predicate.test(element)
  }

  private[completion] def identifierPattern =
    psiElement(tIDENTIFIER)

  private[completion] def identifierWithParentPattern(clazz: Class[_ <: ScalaPsiElement]) =
    identifierPattern.withParent(clazz)

  private[completion] def identifierWithParentsPattern(classes: Class[_ <: ScalaPsiElement]*) =
    identifierPattern.withParents(classes: _*)

  private[completion] def annotationPattern =
    psiElement.afterLeaf(psiElement(tAT))

  private[completion] def afterNewKeywordPattern = identifierWithParentsPattern(
    classOf[ScStableCodeReference],
    classOf[ScSimpleTypeElement],
    classOf[ScConstructorInvocation],
    classOf[ScTemplateParents],
    classOf[ScExtendsBlock],
    classOf[ScNewTemplateDefinition]
  )

  private[completion] def universalApplyPattern: PsiElementPattern.Capture[PsiElement] = identifierPattern
    .isInScala3File
    .withParent(
      psiElement(classOf[ScReferenceExpression])
        .without(condition[ScReferenceExpression]("isChildOfScPattern")(_.parent.exists(_.is[ScPattern])))
    )

  private[completion] def insideTypePattern =
    psiElement.inside(classOf[ScTypeElement]) ||
      afterNewKeywordPattern

  private[completion] def isInScala3FilePattern =
    condition[PsiElement]("isInScala3FilePattern")(_.isInScala3File)

  implicit class PsiElementPatternExt[T <: PsiElement](private val pattern: PsiElementPattern.Capture[T]) extends AnyVal {
    def isInScala3File: PsiElementPattern.Capture[T] = pattern.`with`(isInScala3FilePattern)

    def notAfterLeafSkippingWhitespaceComment(tp: IElementType): PsiElementPattern.Capture[T] =
      pattern.`with`(condition[T](s"prevVisibleLeaf(skipComments = true).elementType != $tp") { element =>
        element.prevVisibleLeaf(skipComments = true).forall(_.elementType != tp)
      })

    def withPrevSiblingNotWhitespace(prevSiblingType: IElementType): PsiElementPattern.Capture[T] =
      pattern.`with`(condition[T](s"prevSiblingNotWhitespace.elementType = $prevSiblingType") { element =>
        element.prevSiblingNotWhitespace.exists(_.elementType == prevSiblingType)
      })

    def withNextSiblingNotWhitespaceComment(nextSiblingType: IElementType): PsiElementPattern.Capture[T] =
      pattern.`with`(condition[T](s"nextSiblingNotWhitespaceComment.elementType = $nextSiblingType") { element =>
        element.nextSiblingNotWhitespaceComment.exists(_.elementType == nextSiblingType)
      })

    def withType(fqn: String): PsiElementPattern.Capture[T] =
      pattern.`with`(condition[T](s"hasType $fqn") {
        case typeable: Typeable =>
          val psiManager = ScalaPsiManager.instance(typeable.getProject)
          val resolveScope = typeable.resolveScope
          val cls = psiManager.getCachedClass(resolveScope, fqn).orNull
          cls != null &&
            typeable.`type`().exists(_.conforms(ScDesignatorType(cls)))
        case _ => false
      })
  }

  private[completion] def isExcluded(clazz: PsiClass) = inReadAction {
    JavaCompletionUtil.isInExcludedPackage(clazz, false)
  }

  // TODO to be reused
  def isAccessible(member: PsiMember)
                  (implicit place: PsiElement): Boolean =
    ResolveUtils.isAccessible(member, place, forCompletion = true)

  def isAccessible(member: PsiMember,
                   invocationCount: Int)
                  (implicit place: PsiElement): Boolean =
    regardlessAccessibility(invocationCount) ||
      isAccessible(member)

  def regardlessAccessibility(invocationCount: Int): Boolean =
    invocationCount >= 2

  def accessAll(invocationCount: Int): Boolean =
    invocationCount >= 3

  def isInImport(place: PsiElement): Boolean =
    getContextOfType(place, classOf[ScImportStmt]) != null

  def isInTypeElement(place: PsiElement): Boolean =
    getContextOfType(place, classOf[ScTypeElement]) != null

  def isInScalaContext(place: PsiElement,
                       isInSimpleString: Boolean,
                       isInInterpolatedString: Boolean = false): Boolean =
    isInSimpleString ||
      isInInterpolatedString ||
      getContextOfType(place, classOf[ScalaFile]) != null

  def createLookupElementWithPrefix(namedElement: PsiNamedElement,
                                    containingClass: PsiClass): ScalaLookupItem = {
    val resolveResult = new ScalaResolveResult(namedElement)

    val result = resolveResult.createLookupElement(
      isClassName = true,
      shouldImport = true,
      containingClass = Some(containingClass)
    )

    result.addLookupStrings(result.containingClassName + "." + result.getPsiElement.name)
    result
  }

  implicit class CaptureExt(private val pattern: ElementPattern[_ <: PsiElement]) extends AnyVal {

    import StandardPatterns.{and, or}

    def &&(pattern: ElementPattern[_ <: PsiElement]): ElementPattern[_ <: PsiElement] = and(this.pattern, pattern)

    def ||(pattern: ElementPattern[_ <: PsiElement]): ElementPattern[_ <: PsiElement] = or(this.pattern, pattern)
  }

  private[completion] implicit class LookupElementExt[E <: LookupElement](private val lookupElement: E) extends AnyVal {

    def withBooleanUserData(key: Key[java.lang.Boolean]): E = {
      lookupElement.putUserData(key, java.lang.Boolean.TRUE)
      lookupElement
    }
  }

  private[completion] implicit class OffsetMapExt(private val offsetMap: OffsetMap) extends AnyVal {

    def apply(key: OffsetKey): Int = offsetMap.getOffset(key)

    def update(key: OffsetKey, offset: Int): Unit = offsetMap.addOffset(key, offset)
  }

  implicit class InsertionContextExt(private val context: InsertionContext) extends AnyVal {

    def offsetMap: OffsetMap = context.getOffsetMap

    def scheduleAutoPopup(): Unit = {
      context.setLaterRunnable(() => {
        AutoPopupController.getInstance(context.getProject).scheduleAutoPopup(
          context.getEditor,
          CompletionType.BASIC,
          (_: PsiFile) == context.getFile
        )
      })
    }
  }

  private[completion] object InsertionContextExt {

    def unapply(context: InsertionContext): Some[(Editor, Document, PsiFile, Project)] =
      Some(context.getEditor, context.getDocument, context.getFile, context.getProject)
  }


  /**
   * "Completion file" is used for analysis for completion by default. It is a copy of original file with
   * dummy identifier inserted at current offset to ensure we have a non-empty reference there.
   *
   * See [[com.intellij.codeInsight.completion.CompletionParameters#getPosition()]]
   *
   * This approach doesn't work well with Scala Plugin caches, because there are no psi events in non-physical files.
   * Also, reanalyzing the whole file may have bad performance. Instead, we create a
   * copy a fragment of a "completion file" and insert it to the original file. This fragment is invalidated for any
   * physical psi change, so we don't need to care about caches there.
   *
   */
  def positionFromParameters(implicit parameters: CompletionParameters): PsiElement = {
    val defaultPosition = parameters.getPosition

    mirrorPosition(parameters.getOriginalFile, defaultPosition)
      .getOrElse(defaultPosition)
  }

  private def mirrorPosition(originalFile: PsiFile, positionInCompletionFile: PsiElement): Option[PsiElement] = {
    //similar to BlockModificationTracker.parentWithStableType, but may return last expression in block without explicit type
    @annotation.tailrec
    def locallyStableParent(element: PsiElement): Option[ScExpression] =
      element match {
        case null | _: ScalaFile => None
        case (expr: ScExpression) childOf (_: ScBlock) => Some(expr)
        case expr: ScExpression if hasStableType(expr) => Some(expr)
        case element => locallyStableParent(element.getParent)
      }

    cachedInUserData("mirrorPosition", originalFile, CachesUtil.fileModTracker(originalFile), Tuple1(positionInCompletionFile)) {
      val placeOffset = positionInCompletionFile match {
        case ElementType(ScalaTokenTypes.tIDENTIFIER) => positionInCompletionFile.getParent.startOffset
        case _                                        => positionInCompletionFile.startOffset
      }
      val placeInOriginalFile = originalFile.findElementAt(placeOffset)

      //todo: we may probably choose a smaller fragment to copy in many cases SCL-17106
      for {
        anchor           <- locallyStableParent(placeInOriginalFile)
        expressionToCopy <- locallyStableParent(positionInCompletionFile)
      } yield {

        val copy = expressionToCopy.copy().asInstanceOf[ScExpression]
        copy.context = anchor.getContext
        copy.child = anchor

        val newOffset = positionInCompletionFile.startOffset - expressionToCopy.startOffset + copy.startOffset
        copy.getContainingFile.findElementAt(newOffset)
      }
    }
  }

  private[completion] def dummyIdentifier(file: PsiFile, offset: Int): String = {
    import CompletionUtil.{DUMMY_IDENTIFIER, DUMMY_IDENTIFIER_TRIMMED}
    import ScalaNamesUtil.isBacktickedName.BackTick
    import ScalaNamesValidator._

    file.findReferenceAt(offset) match {
      case null =>
        file.findElementAt(offset) match {
          case element if requiresSuffix(element) => DUMMY_IDENTIFIER_TRIMMED + BackTick
          case _ =>
            file.findElementAt(offset + 1) match {
              case psiElement: PsiElement if isKeyword(psiElement.getText) => DUMMY_IDENTIFIER
              case _ => DUMMY_IDENTIFIER_TRIMMED
            }
        }
      case ref =>
        val e = ref match {
          case psiElement: PsiElement => psiElement
          case _ => ref.getElement //this case for anonymous method in ScAccessModifierImpl
        }

        val id = e.getText match {
          case text if isIdentifier("+" + text.last) => "+++++++++++++++++++++++"
          case text =>
            val substring = text.drop(offset - e.getTextRange.getStartOffset + 1)
            if (isKeyword(substring)) DUMMY_IDENTIFIER else DUMMY_IDENTIFIER_TRIMMED
        }

        val suffix = if (ref.getElement.nullSafe
          .map(_.getPrevSibling)
          .exists(requiresSuffix)) BackTick
        else ""

        id + suffix
    }
  }

  private[completion] def definitionByPosition(place: PsiElement) = place match {
    case ScalaSmartCompletionContributor.Reference(reference) =>
      //When we have a reference in postfix expression, select postfix expression itself
      //This is in order these two examples behave the same during completion:
      //val x: MyType = 42.method<caret>
      //val x: MyType = 42 method<caret>
      val result = reference.getContext match {
        case postfix: ScPostfixExpr => postfix
        case _ => reference
      }
      Some(result)
    case _ =>
      Option(getContextOfType(place, classOf[ScNewTemplateDefinition]))
  }

  private[this] def requiresSuffix(element: PsiElement) =
    element != null && element.getNode.getElementType == tSTUB

  private[completion] def toValueType(`type`: ScType) =
    `type`.extractDesignatorSingleton.getOrElse(`type`)

  abstract class ScalaCompletionContributor extends CompletionContributor {

    override def fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet): Unit = {
      val prefixMatcher = resultSet.getPrefixMatcher

      val sorter = CompletionSorter.defaultSorter(parameters, prefixMatcher) match {
        case defaultSorter if parameters.getCompletionType == CompletionType.SMART => defaultSorter
        case defaultSorter =>
          val position = positionFromParameters(parameters)
          val isAfterNew = afterNewKeywordPattern.accepts(position)
          val maybeDefinition = definitionByPosition(position)

          val newSorter = if (insideTypePattern.accepts(position))
            defaultSorter.weighBefore("liftShorter", new ScalaByTypeWeigher)
          else
            defaultSorter

          newSorter.weighAfter(
            if (isAfterNew) "scalaTypeCompletionWeigher" else "scalaKindWeigher",
            new ScalaByExpectedTypeWeigher(maybeDefinition)(position)
          )
      }

      val updatedResultSet = resultSet
        .withPrefixMatcher(new BacktickPrefixMatcher(prefixMatcher))
        .withRelevanceSorter(sorter)
      super.fillCompletionVariants(parameters, updatedResultSet)
    }
  }

  abstract class ScalaCompletionProvider extends CompletionProvider[CompletionParameters] {

    protected def completionsFor(position: PsiElement)
                                (implicit parameters: CompletionParameters,
                                 context: ProcessingContext): Iterable[LookupElement]

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                result: CompletionResultSet): Unit = {
      implicit val p: CompletionParameters = parameters
      implicit val c: ProcessingContext = context
      val lookupElements = completionsFor(positionFromParameters)
      result.addAllElements(lookupElements)
    }
  }

  private[completion] implicit class CompletionResultSetExt(private val set: CompletionResultSet) extends AnyVal {

    def addAllElements(lookupElements: Iterable[_ <: LookupElement]): Unit = {
      import scala.jdk.CollectionConverters._
      set.addAllElements(lookupElements.asJava)
    }
  }

  private[completion] trait DelegatingCompletionProvider[E <: ScalaPsiElement] extends CompletionProvider[CompletionParameters] {

    override final def addCompletions(parameters: CompletionParameters,
                                      context: ProcessingContext,
                                      resultSet: CompletionResultSet): Unit =
      resultSet.getPrefixMatcher.getPrefix match {
        case prefix if ScalaNamesValidator.isIdentifier(prefix) && !ScalaNamesValidator.isSoftKeyword(prefix) && prefix.forall(_.isLetterOrDigit) =>
          addCompletions(resultSet, prefix)(parameters, context)
        case _ =>
      }

    protected def addCompletions(resultSet: CompletionResultSet,
                                 prefix: String)
                                (implicit parameters: CompletionParameters,
                                 context: ProcessingContext): Unit

    protected final def createElement(text: String, prefix: String, position: PsiElement): E =
      createElement(prefix + text, position.getContext, position)

    protected def createElement(text: String,
                                context: PsiElement,
                                child: PsiElement): E

    protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer[CompletionResult]

    protected final def createParameters(typeElement: ScalaPsiElement,
                                         maybeLength: Option[Int] = None)
                                        (implicit parameters: CompletionParameters): CompletionParameters = {
      val Some(identifier) = findIdentifier(typeElement)
      val range = identifier.getTextRange

      val length = maybeLength.getOrElse(range.getLength)
      parameters.withPosition(identifier, range.getStartOffset + length)
    }

    protected final def findIdentifier(element: ScalaPsiElement): Option[PsiElement] =
      element.depthFirst()
        .find(_.getNode.getElementType == tIDENTIFIER)
  }

  private class BacktickPrefixMatcher(private val delegate: PrefixMatcher) extends PrefixMatcher(delegate.getPrefix) {

    import ScalaNamesUtil._
    import isBacktickedName._

    private val backticklessMatcher = delegate.cloneWithPrefix {
      withoutBackticks(myPrefix)
    }

    override def prefixMatches(name: String): Boolean = {
      val (matcher, scalaName) = findDelegate(name)
      matcher.prefixMatches(scalaName)
    }

    override def isStartMatch(name: String): Boolean = {
      val (matcher, scalaName) = findDelegate(name)
      matcher.isStartMatch(scalaName)
    }

    override def cloneWithPrefix(prefix: String): PrefixMatcher =
      backticklessMatcher.cloneWithPrefix(prefix)

    private def findDelegate(name: String) = myPrefix match {
      case BackTick => (delegate, name)
      case _ => (backticklessMatcher, clean(name))
    }
  }

  private final class ScalaByTypeWeigher extends LookupElementWeigher("scalaTypeCompletionWeigher") {

    override def weigh(element: LookupElement, context: WeighingContext): Comparable[_] =
      element.getPsiElement match {
        case typeAlias: ScTypeAlias if typeAlias.isLocal => 1 // localType
        case typeDefinition: ScTypeDefinition if isLocal(typeDefinition) => 1 // localType
        case _: ScTypeAlias |
             _: PsiClass => 2 // typeDefinition
        case _ => 3 // normal
      }

    private def isLocal(typeDefinition: ScTypeDefinition) = typeDefinition match {
      case _: ScObject => false
      case _ => typeDefinition.isLocal || getParentOfType(typeDefinition, classOf[ScBlockExpr]) != null
    }
  }
}
