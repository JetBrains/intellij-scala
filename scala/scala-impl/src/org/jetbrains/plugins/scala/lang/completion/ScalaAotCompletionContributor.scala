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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
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
  import ScalaTokenTypes._

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(tIDENTIFIER).withParent(classOf[ScParameter]),
    new AotCompletionProvider[ScParameter] {

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): ScParameter =
        createParamClausesWithContext(text.parenthesize(), context, child).params.head

      override protected def typeElement(parameter: ScParameter): Option[ScalaPsiElement] = parameter.paramType

      override protected def createConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet): MyConsumer = new MyConsumer(prefixMatcher, resultSet) {

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
    CompletionType.BASIC,
    PlatformPatterns.psiElement(tIDENTIFIER).withParent(classOf[ScFieldId]),
    new AotCompletionProvider[ScValueDeclaration] {

      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit =
        positionFromParameters(parameters).getContext.getContext.getContext match {
          case _: ScVariableDeclaration | _: ScValueDeclaration => super.addCompletions(parameters, context, resultSet)
          case _ =>
        }

      override protected def createElement(text: String,
                                           context: PsiElement,
                                           child: PsiElement): ScValueDeclaration =
        createDeclarationFromText(kVAL + " " + text, context, child).asInstanceOf[ScValueDeclaration]

      override protected def context(element: ScValueDeclaration): PsiElement =
        super.context(element).getContext.getContext

      override protected def typeElement(declaration: ScValueDeclaration): Option[ScalaPsiElement] = declaration.typeElement

      override protected def createConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet): MyConsumer = new MyConsumer(prefixMatcher, resultSet) {

        private val consumed = mutable.Set.empty[String]

        override protected def consume(element: LookupElement, name: String): Unit = {
          if (consumed.add(name)) super.consume(element, name)
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

  private trait AotCompletionProvider[E <: ScalaPsiElement] extends CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                resultSet: CompletionResultSet): Unit = {
      import StringUtil.capitalize
      val element = positionFromParameters(parameters)
      if (!ScalaProjectSettings.getInstance(element.getProject).isAotCompletion) return

      val prefixMatcher = resultSet.getPrefixMatcher
      val prefix = prefixMatcher.getPrefix

      if (!ScalaNamesValidator.isIdentifier(prefix) || prefix.exists(!_.isLetterOrDigit)) return

      val replacement = createElement(
        prefix + Delimiter + capitalize(element.getText),
        element.getContext,
        element
      )

      val context = this.context(replacement)
      replacement.setContext(context, context.getLastChild)

      val identifier = typeElement(replacement)
        .flatMap(findIdentifier)
        .get

      val newParameters = parameters.withPosition(identifier, identifier.getTextRange.getStartOffset + prefix.length)

      val newResultSet = resultSet.withPrefixMatcher(prefixMatcher.cloneWithPrefix(capitalize(prefix)))
      newResultSet.runRemainingContributors(newParameters, createConsumer(prefixMatcher, newResultSet), true)
    }

    protected def createElement(text: String,
                                context: PsiElement,
                                child: PsiElement): E

    protected def context(element: E): PsiElement = element.getContext.getContext

    protected def typeElement(element: E): Option[ScalaPsiElement]

    protected def createConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet): MyConsumer

    private def findIdentifier(element: ScalaPsiElement): Option[PsiElement] =
      element.depthFirst()
        .find(_.getNode.getElementType == tIDENTIFIER)

    protected abstract class MyConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet) extends Consumer[CompletionResult] {

      private val lowerCasePrefix = prefixMatcher.getPrefix.takeWhile(_.isLower)

      def consume(result: CompletionResult) {
        val element = result.getLookupElement
        consume(element, suggestItemText(element.getLookupString))
      }

      protected def consume(element: LookupElement, itemText: String): Unit = {
        import LookupElementDecorator._

        resultSet.consume(withInsertHandler(
          withRenderer(element, createRenderer(itemText)),
          createInsertHandler(itemText)
        ))
      }

      protected def createRenderer(itemText: String) = new AotLookupElementRenderer(itemText)

      protected def createInsertHandler(itemText: String) = new AotInsertHandler(itemText)

      protected def suggestItemText(lookupString: String): String = {
        import StringUtil.{capitalize, decapitalize}

        val name = lookupString.indexOf(capitalize(lowerCasePrefix)) match {
          case -1 => lookupString
          case index => lookupString.substring(index)
        }

        decapitalize(name)
      }
    }

  }

}

object ScalaAotCompletionContributor {

  private val Delimiter = ScalaTokenTypes.tCOLON + " "

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
