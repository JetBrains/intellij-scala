package org.jetbrains.plugins.scala
package lang

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.patterns.{ElementPattern, PlatformPatterns, StandardPatterns}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiFile}
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaByExpectedTypeWeigher
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters

package object completion {

  import ScalaTokenTypes._

  private[completion] def identifierPattern =
    PlatformPatterns.psiElement(tIDENTIFIER)

  private[completion] def identifierWithParentPattern(clazz: Class[_ <: ScalaPsiElement]) =
    identifierPattern.withParent(clazz)

  private[completion] def identifierWithParentsPattern(classes: Class[_ <: ScalaPsiElement]*) =
    identifierPattern.withParents(classes: _*)

  private[completion] def isExcluded(clazz: PsiClass) = inReadAction {
    JavaCompletionUtil.isInExcludedPackage(clazz, false)
  }

  implicit class CaptureExt(private val pattern: ElementPattern[_ <: PsiElement]) extends AnyVal {

    import StandardPatterns.{and, or}

    def &&(pattern: ElementPattern[_ <: PsiElement]): ElementPattern[_ <: PsiElement] = and(this.pattern, pattern)

    def ||(pattern: ElementPattern[_ <: PsiElement]): ElementPattern[_ <: PsiElement] = or(this.pattern, pattern)
  }

  private[completion] implicit class OffsetMapExt(private val offsetMap: OffsetMap) extends AnyVal {

    def apply(key: OffsetKey): Int = offsetMap.getOffset(key)

    def update(key: OffsetKey, offset: Int): Unit = offsetMap.addOffset(key, offset)
  }

  private[completion] implicit class InsertionContextExt(private val context: InsertionContext) extends AnyVal {

    def offsetMap: OffsetMap = context.getOffsetMap

    def setStartOffset(offset: Int): Unit = {
      offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, offset)
    }
  }

  private[completion] object InsertionContextExt {

    def unapply(context: InsertionContext): Some[(Editor, Document, PsiFile, Project)] =
      Some(context.getEditor, context.getDocument, context.getFile, context.getProject)
  }

  def positionFromParameters(implicit parameters: CompletionParameters): PsiElement =
    findMirrorPosition(parameters.getOriginalPosition)(parameters.getOriginalFile, parameters.getOffset)
      .getOrElse(parameters.getPosition)

  @tailrec
  private[this] def findMirrorPosition(element: PsiElement)
                                      (implicit originalFile: PsiFile,
                                       offset: Int): Option[PsiElement] =
    element match {
      case null => None // we got to the top of the tree and didn't find a modificationTrackerOwner
      case owner: ScExpression if owner.shouldntChangeModificationCount =>
        owner.getContainingFile match {
          case `originalFile` => owner.mirrorPosition(dummyIdentifier(offset), offset)
          case _ => None
        }
      case _ => findMirrorPosition(element.getContext)
    }

  private[completion] def dummyIdentifier(offset: Int)
                                         (implicit file: PsiFile): String = {
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

  private[this] def requiresSuffix(element: PsiElement) =
    element != null && element.getNode.getElementType == tSTUB

  abstract class ScalaCompletionContributor extends CompletionContributor {

    override def fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet): Unit = {
      val prefixMatcher = resultSet.getPrefixMatcher

      val sorter = CompletionSorter.defaultSorter(parameters, prefixMatcher) match {
        case defaultSorter if parameters.getCompletionType == CompletionType.SMART => defaultSorter
        case defaultSorter =>
          val position = positionFromParameters(parameters)
          val isAfterNew = ScalaAfterNewCompletionContributor.isAfterNew(position)

          val maybeDefinition = position match {
            case ScalaSmartCompletionContributor.Reference(reference) => Some(reference)
            case _ if isAfterNew => ScalaAfterNewCompletionContributor.findNewTemplate(position)
            case _ => None
          }

          defaultSorter
            .weighBefore("liftShorter", new ScalaByTypeWeigher(position))
            .weighAfter(if (isAfterNew) "scalaTypeCompletionWeigher" else "scalaKindWeigher", new ScalaByExpectedTypeWeigher(maybeDefinition)(position))
      }

      val updatedResultSet = resultSet
        .withPrefixMatcher(new BacktickPrefixMatcher(prefixMatcher))
        .withRelevanceSorter(sorter)
      super.fillCompletionVariants(parameters, updatedResultSet)
    }
  }

  abstract class ScalaCompletionProvider extends CompletionProvider[CompletionParameters] {

    protected def completionsFor(position: PsiElement)
                                (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[LookupElement]

    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      implicit val p: CompletionParameters = parameters
      implicit val c: ProcessingContext = context
      val lookupElements = completionsFor(positionFromParameters)

      import JavaConverters._
      result.addAllElements(lookupElements.asJava)
    }
  }

  private[completion] trait DelegatingCompletionProvider[E <: ScalaPsiElement] extends CompletionProvider[CompletionParameters] {

    override final def addCompletions(parameters: CompletionParameters,
                                      context: ProcessingContext,
                                      resultSet: CompletionResultSet): Unit =
      resultSet.getPrefixMatcher.getPrefix match {
        case prefix if ScalaNamesValidator.isIdentifier(prefix) && prefix.forall(_.isLetterOrDigit) =>
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

  private class ScalaByTypeWeigher(position: PsiElement) extends LookupElementWeigher("scalaTypeCompletionWeigher") {

    override def weigh(element: LookupElement, context: WeighingContext): Comparable[_] =
      if (ScalaAfterNewCompletionContributor.isInTypeElement(position)) {
        element match {
          case ScalaLookupItem(_, namedElement) => namedElement match {
            case typeAlias: ScTypeAlias if typeAlias.isLocal => 1 // localType
            case typeDefinition: ScTypeDefinition if isValid(typeDefinition) => 1 // localType
            case _: ScTypeAlias | _: PsiClass => 2 // typeDefinition
            case _ => 3 // normal
          }
          case _ => null
        }
      } else null

    private def isValid(typeDefinition: ScTypeDefinition) = !typeDefinition.isObject &&
      (typeDefinition.isLocal || PsiTreeUtil.getParentOfType(typeDefinition, classOf[ScBlockExpr]) != null)
  }

}
