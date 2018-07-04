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
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable

/**
  * @author Pavel Fatin
  */
class ScalaAotCompletionContributor extends ScalaCompletionContributor {

  import ScalaAotCompletionContributor._
  import ScalaPsiElementFactory.{createDeclarationFromText, createParamClausesWithContext}

  extend(
    classOf[ScParameter],
    new AotCompletionProvider[ScParameter] {

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): (ScParameter, Option[ScParameterType]) = {
        val parameter = createParamClausesWithContext(text.parenthesize(), context, child).params.head
        (parameter, parameter.paramType)
      }

      override protected def createConsumer(resultSet: CompletionResultSet): AotConsumer = new AotConsumer(resultSet) {

        override protected def createInsertHandler(itemText: String): AotInsertHandler = new AotInsertHandler(itemText) {

          override def handleInsert(context: InsertionContext, lookupElement: LookupElement): Unit = {
            val range = TextRange.create(context.getStartOffset, context.getTailOffset)
              .shiftRight(itemText.indexOf(Delimiter) + Delimiter.length)

            super.handleInsert(context, lookupElement)

            lookups.ScalaLookupItem.original(lookupElement)
              .handleInsert(createContext(range, context))
          }

          private def createContext(range: TextRange, context: InsertionContext) = {
            val InsertionContextExt(editor, document, file, _) = context

            val offsetMap = new OffsetMap(document)
            offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, range.getStartOffset)
            offsetMap.addOffset(InsertionContext.TAIL_OFFSET, range.getEndOffset)

            new InsertionContext(
              offsetMap,
              context.getCompletionChar,
              context.getElements,
              file,
              editor,
              context.shouldAddCompletionChar
            )
          }
        }

        override protected def suggestItemText(lookupString: String): String =
          super.suggestItemText(lookupString) + Delimiter + lookupString
      }
    }
  )

  extend(
    classOf[ScFieldId],
    new AotCompletionProvider[ScValueDeclaration] {

      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit =
        positionFromParameters(parameters).getContext.getContext.getContext match {
          case _: ScVariableDeclaration | _: ScValueDeclaration => super.addCompletions(parameters, context, resultSet)
          case _ =>
        }

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): (ScValueDeclaration, Option[ScTypeElement]) = {
        val declaration = createDeclarationFromText(kVAL + " " + text, context, child).asInstanceOf[ScValueDeclaration]
        (declaration, declaration.typeElement)
      }

      override protected def findContext(element: ScValueDeclaration): PsiElement =
        super.findContext(element).getContext.getContext

      override protected def createConsumer(resultSet: CompletionResultSet): AotConsumer = new AotConsumer(resultSet) {

        private val consumed = mutable.Set.empty[String]

        override protected def consume(lookupElement: LookupElement, itemText: String): Unit = {
          if (consumed.add(itemText)) super.consume(lookupElement, itemText)
        }

        override protected def createRenderer(itemText: String): AotLookupElementRenderer = new AotLookupElementRenderer(itemText) {

          override def renderElement(decorator: LookupElementDecorator[LookupElement],
                                     presentation: LookupElementPresentation): Unit = {
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
    PlatformPatterns.psiElement(tIDENTIFIER).withParent(parentType),
    provider
  )
}

object ScalaAotCompletionContributor {

  import StringUtil.{capitalize, decapitalize}

  private val Delimiter = tCOLON + " "

  private trait AotCompletionProvider[E <: ScalaPsiElement] extends CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit = {
      val element = positionFromParameters(parameters)
      if (!ScalaProjectSettings.getInstance(element.getProject).isAotCompletion) return

      val prefix = resultSet.getPrefixMatcher.getPrefix
      if (!ScalaNamesValidator.isIdentifier(prefix) || prefix.exists(!_.isLetterOrDigit)) return

      val (replacement, Some(typeElement)) = createElement(
        prefix + Delimiter + capitalize(element.getText),
        element.getContext,
        element
      )

      val context = findContext(replacement)
      replacement.setContext(context, context.getLastChild)

      val newParameters = createParameters(typeElement, prefix, parameters)
      val consumer = createConsumer(resultSet)
      consumer.resultSet.runRemainingContributors(newParameters, consumer, true)
    }

    protected def createElement(text: String,
                                context: PsiElement,
                                child: PsiElement): (E, Option[ScalaPsiElement])

    protected def findContext(element: E): PsiElement = element.getContext.getContext

    protected def createConsumer(resultSet: CompletionResultSet): AotConsumer

    private def createParameters(typeElement: ScalaPsiElement,
                                 prefix: String,
                                 parameters: CompletionParameters) = {
      val Some(identifier) = typeElement.depthFirst()
        .find(_.getNode.getElementType == tIDENTIFIER)

      parameters.withPosition(identifier, prefix.length + identifier.getTextRange.getStartOffset)
    }
  }

  private abstract class AotConsumer(originalResultSet: CompletionResultSet) extends Consumer[CompletionResult] {

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

      resultSet.consume(withInsertHandler(
        withRenderer(lookupElement, createRenderer(itemText)),
        createInsertHandler(itemText)
      ))
    }

    protected def createRenderer(itemText: String) = new AotLookupElementRenderer(itemText)

    protected def createInsertHandler(itemText: String) = new AotInsertHandler(itemText)

    protected def suggestItemText(lookupString: String): String = decapitalize(
      lookupString.indexOf(targetPrefix) match {
        case -1 => lookupString
        case index => lookupString.substring(index)
      }
    )
  }

  private class AotLookupElementRenderer(itemText: String) extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {

    def renderElement(decorator: LookupElementDecorator[LookupElement],
                      presentation: LookupElementPresentation): Unit = {
      decorator.getDelegate.renderElement(presentation)
      presentation.setItemText(itemText)
    }
  }

  protected class AotInsertHandler(itemText: String) extends InsertHandler[LookupElement] {

    def handleInsert(context: InsertionContext, lookupElement: LookupElement): Unit = {
      context.getDocument.replaceString(
        context.getStartOffset,
        context.getTailOffset,
        itemText
      )
      context.commitDocument()
    }
  }

}
