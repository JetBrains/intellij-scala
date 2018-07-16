package org.jetbrains.plugins.scala
package lang

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiFile}
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.getDummyIdentifier
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaByExpectedTypeWeigher
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScModificationTrackerOwner}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters

package object completion {

  private[completion] object InsertionContextExt {

    def unapply(context: InsertionContext): Some[(Editor, Document, PsiFile, Project)] =
      Some(context.getEditor, context.getDocument, context.getFile, context.getProject)
  }

  def positionFromParameters(implicit parameters: CompletionParameters): PsiElement = {
    @tailrec
    def position(element: PsiElement): PsiElement = element match {
      case null => parameters.getPosition // we got to the top of the tree and didn't find a modificationTrackerOwner
      case owner@ScModificationTrackerOwner() =>
        val maybeMirrorPosition = parameters.getOriginalFile match {
          case file if owner.containingFile.contains(file) =>
            val offset = parameters.getOffset
            val dummyId = getDummyIdentifier(offset, file)
            owner.mirrorPosition(dummyId, offset)
          case _ => None
        }

        maybeMirrorPosition.getOrElse(parameters.getPosition)
      case _ => position(element.getContext)
    }

    position(parameters.getOriginalPosition)
  }

  abstract class ScalaCompletionContributor extends CompletionContributor {

    override def fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet): Unit = {
      val prefixMatcher = resultSet.getPrefixMatcher

      val sorter = CompletionSorter.defaultSorter(parameters, prefixMatcher) match {
        case defaultSorter if parameters.getCompletionType == CompletionType.SMART => defaultSorter
        case defaultSorter =>
          val position = positionFromParameters(parameters)
          val isAfterNew = ScalaAfterNewCompletionUtil.afterNewPattern.accepts(position)

          defaultSorter
            .weighBefore("liftShorter", new ScalaByTypeWeigher(position, isAfterNew))
            .weighAfter(if (isAfterNew) "scalaTypeCompletionWeigher" else "scalaKindWeigher", new ScalaByExpectedTypeWeigher(position, isAfterNew))
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

    protected final def createElement(text: String, prefix: String)
                                     (implicit position: PsiElement): E =
      createElement(prefix + text, position.getContext, position)

    protected def createElement(text: String,
                                context: PsiElement,
                                child: PsiElement): E

    protected def createConsumer(resultSet: CompletionResultSet)
                                (implicit position: PsiElement): Consumer[CompletionResult]

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
        .find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)
  }

  private class BacktickPrefixMatcher(other: PrefixMatcher) extends PrefixMatcher(other.getPrefix) {

    private val matcherWithoutBackticks = other.cloneWithPrefix(cleanHelper(myPrefix))

    override def prefixMatches(name: String): Boolean =
      if (myPrefix == "`") other.prefixMatches(name)
      else matcherWithoutBackticks.prefixMatches(ScalaNamesUtil.clean(name))

    override def cloneWithPrefix(prefix: String): PrefixMatcher = matcherWithoutBackticks.cloneWithPrefix(prefix)

    override def isStartMatch(name: String): Boolean =
      if (myPrefix == "`") other.isStartMatch(name)
      else matcherWithoutBackticks.isStartMatch(ScalaNamesUtil.clean(name))

    private def cleanHelper(prefix: String): String = {
      if (prefix == null || prefix.isEmpty || prefix == "`") prefix
      else prefix match {
        case ScalaNamesUtil.isBacktickedName(s) => s
        case p if p.head == '`' => p.substring(1)
        case p if p.last == '`' => prefix.substring(0, prefix.length - 1)
        case _ => prefix
      }
    }
  }

  private class ScalaByTypeWeigher(position: PsiElement, isAfterNew: Boolean) extends LookupElementWeigher("scalaTypeCompletionWeigher") {

    override def weigh(element: LookupElement, context: WeighingContext): Comparable[_] =
      if (ScalaCompletionUtil.isTypeDefiniton(position) || isAfterNew) {
        ScalaLookupItem.original(element) match {
          case ScalaLookupItem(typeAlias: ScTypeAlias) if typeAlias.isLocal => 1 // localType
          case ScalaLookupItem(typeDefinition: ScTypeDefinition) if isValid(typeDefinition) => 1 // localType
          case ScalaLookupItem(_: ScTypeAlias | _: PsiClass) => 2 // typeDefinition
          case ScalaLookupItem(_) => 3 // normal
          case _ => null
        }
      } else null

    private def isValid(typeDefinition: ScTypeDefinition) = !typeDefinition.isObject &&
      (typeDefinition.isLocal || PsiTreeUtil.getParentOfType(typeDefinition, classOf[ScBlockExpr]) != null)
  }

}
