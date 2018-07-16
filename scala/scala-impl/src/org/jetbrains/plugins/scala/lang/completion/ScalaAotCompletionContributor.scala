package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable

/**
  * @author Pavel Fatin
  */
class ScalaAotCompletionContributor extends ScalaCompletionContributor {

  import ScalaAotCompletionContributor._

  extend(
    classOf[ScParameter],
    new AotCompletionProvider[ScParameter] {

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): ScParameter =
        ScalaPsiElementFactory.createParamClausesWithContext(text.parenthesize(), context, child)
          .params.head

      override protected def findTypeElement(parameter: ScParameter): Option[ScParameterType] =
        parameter.paramType

      override protected def createConsumer(resultSet: CompletionResultSet)
                                           (implicit position: PsiElement): AotConsumer = new AotConsumer(resultSet) {

        private val maybeRange = for {
          parameter <- position.parentOfType(classOf[ScParameter])

          typeElement <- findTypeElement(parameter)
          bound = typeElement.getTextRange.getEndOffset

          identifier <- findIdentifier(parameter)
          origin = identifier.getTextRange.getEndOffset
        } yield new TextRange(origin, bound)

        override protected def createInsertHandler(itemText: String): AotInsertHandler = new AotInsertHandler(itemText) {

          private val delta = itemText.indexOf(Delimiter) + Delimiter.length

          override def handleInsert(context: InsertionContext, decorator: Decorator): Unit = {
            def withRange(function: (Int, Int) => Unit): Unit =
              maybeRange.foreach { range =>
                function(context.getTailOffset, range.getLength)
              }

            withRange {
              case (tailOffset, length) => context.setTailOffset(tailOffset + length)
            }

            super.handleInsert(context, decorator)

            updateStartOffset(context.getOffsetMap)
            decorator.getDelegate.handleInsert(context)

            withRange {
              case (tailOffset, _) => context.getEditor.getCaretModel.moveToOffset(tailOffset)
            }
          }

          private def updateStartOffset(offsetMap: OffsetMap): Unit = {
            import CompletionInitializationContext.START_OFFSET
            val startOffset = offsetMap.getOffset(START_OFFSET) + delta
            offsetMap.addOffset(START_OFFSET, startOffset)
          }
        }

        override protected def suggestItemText(lookupString: String): String =
          typedItemText(lookupString)
      }
    }
  )

  extend(
    classOf[ScFieldId],
    new AotCompletionProvider[ScValueDeclaration] {

      override protected def addCompletions(resultSet: CompletionResultSet,
                                            prefix: String)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Unit =
        positionFromParameters.getContext.getContext.getContext match {
          case _: ScVariableDeclaration | _: ScValueDeclaration => super.addCompletions(resultSet, prefix)
          case _ =>
        }

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): ScValueDeclaration =
        ScalaPsiElementFactory.createDeclarationFromText(ScalaKeyword.VAL + " " + text, context, child)
          .asInstanceOf[ScValueDeclaration]

      override protected def findTypeElement(declaration: ScValueDeclaration): Option[ScTypeElement] =
        declaration.typeElement

      override protected def findContext(element: ScValueDeclaration): PsiElement =
        super.findContext(element).getContext.getContext

      override protected def createConsumer(resultSet: CompletionResultSet)
                                           (implicit position: PsiElement): AotConsumer = new AotConsumer(resultSet) {

        private val consumed = mutable.Set.empty[String]

        override protected def consume(lookupElement: LookupElement, itemText: String): Unit = {
          if (consumed.add(itemText)) super.consume(lookupElement, itemText)
        }

        override protected def suggestItemText(lookupString: String): String = itemText(lookupString)

        override protected def createRenderer(itemText: String): AotLookupElementRenderer = new AotLookupElementRenderer(itemText) {

          override def renderElement(decorator: Decorator, presentation: LookupElementPresentation): Unit = {
            super.renderElement(decorator, presentation)

            presentation.setIcon(null)
            presentation.setTailText(null)
          }
        }
      }
    }
  )

  private def extend(parentType: Class[_ <: ScalaPsiElement],
                     provider: AotCompletionProvider[_ <: ScalaPsiElement]): Unit = extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(parentType),
    provider
  )
}

object ScalaAotCompletionContributor {

  import StringUtil.{capitalize, decapitalize}

  private val Delimiter = ": "

  private type Decorator = LookupElementDecorator[LookupElement]

  private[completion] trait AotCompletionProvider[E <: ScalaPsiElement] extends DelegatingCompletionProvider[E] {

    override protected def addCompletions(resultSet: CompletionResultSet,
                                          prefix: String)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Unit = {
      implicit val position: PsiElement = positionFromParameters
      if (!ScalaProjectSettings.getInstance(position.getProject).isAotCompletion) return

      val replacement = createElement(Delimiter + capitalize(position.getText), prefix)

      val context = findContext(replacement)
      replacement.setContext(context, context.getLastChild)

      val Some(typeElement) = findTypeElement(replacement)
      val newParameters = createParameters(typeElement, Some(prefix.length))

      val consumer = createConsumer(resultSet)
      consumer.resultSet.runRemainingContributors(newParameters, consumer, true)
    }

    protected def findTypeElement(element: E): Option[ScalaPsiElement]

    protected def findContext(element: E): PsiElement = element.getContext.getContext

    override protected def createConsumer(resultSet: CompletionResultSet)
                                         (implicit position: PsiElement): AotConsumer
  }

  private[completion] abstract class AotConsumer(originalResultSet: CompletionResultSet) extends Consumer[CompletionResult] {

    private val prefixMatcher = originalResultSet.getPrefixMatcher
    private val prefix = prefixMatcher.getPrefix
    private val targetPrefix = capitalize(prefix.takeWhile(_.isLower))

    val resultSet: CompletionResultSet = originalResultSet.withPrefixMatcher(prefixMatcher.cloneWithPrefix(capitalize(prefix)))

    def consume(result: CompletionResult) {
      val lookupElement = result.getLookupElement
      consume(lookupElement, suggestItemText(lookupElement.getLookupString))
    }

    protected def consume(lookupElement: LookupElement, itemText: String): Unit = {
      import LookupElementDecorator._

      val decoratedLookupElement = withInsertHandler(
        withRenderer(lookupElement, createRenderer(itemText)),
        createInsertHandler(itemText)
      )
      resultSet.consume(decoratedLookupElement)
    }

    protected def createRenderer(itemText: String) = new AotLookupElementRenderer(itemText)

    protected def createInsertHandler(itemText: String) = new AotInsertHandler(itemText)

    protected def suggestItemText(lookupString: String): String

    protected final def itemText(lookupString: String): String = decapitalize(
      lookupString.indexOf(targetPrefix) match {
        case -1 => lookupString
        case index => lookupString.substring(index)
      }
    )

    protected final def typedItemText(lookupString: String): String =
      itemText(lookupString) + Delimiter + lookupString
  }

  private[completion] class AotLookupElementRenderer(itemText: String) extends LookupElementRenderer[Decorator] {

    def renderElement(decorator: Decorator, presentation: LookupElementPresentation): Unit = {
      decorator.getDelegate.renderElement(presentation)
      presentation.setItemText(itemText)
    }
  }

  private[completion] class AotInsertHandler(itemText: String) extends InsertHandler[Decorator] {

    def handleInsert(context: InsertionContext, decorator: Decorator): Unit = {
      context.getDocument.replaceString(
        context.getStartOffset,
        context.getTailOffset,
        itemText
      )
      context.commitDocument()
    }
  }

}
