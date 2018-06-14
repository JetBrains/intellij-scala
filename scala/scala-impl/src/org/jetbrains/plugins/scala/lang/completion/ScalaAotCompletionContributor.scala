package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
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

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScParameter]),
    new AotCompletionProvider[ScParameter] {

      override protected def createElement(text: String, child: PsiElement): ScParameter =
        ScalaPsiElementFactory.createParamClausesWithContext(s"($text)", child.getContext, child).params.head

      override protected def typeElement(parameter: ScParameter): Option[ScalaPsiElement] = parameter.paramType

      override protected def createConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet): MyConsumer = new MyConsumer(prefixMatcher, resultSet) {

        override protected def createRenderer(name: String): MyElementRenderer =
          (presentation: LookupElementPresentation) => s"$name: ${presentation.getItemText}"

        override protected def createInsertHandler(name: String): MyInsertHandler = new MyInsertHandler {

          override protected def handleInsert(item: LookupElement, range: TextRange)
                                             (implicit context: InsertionContext): Unit = {
            super.handleInsert(item, range)

            val newContext = contextWith(range.shiftRight(name.length + 2))
            lookups.ScalaLookupItem.original(item).handleInsert(newContext)
          }

          override protected def replacement(document: Document, range: TextRange): String =
            s"$name: ${document.getText(range)}"

          private def offsetMap(document: Document, range: TextRange) = {
            val map = new OffsetMap(document)
            map.addOffset(CompletionInitializationContext.START_OFFSET, range.getStartOffset)
            map.addOffset(InsertionContext.TAIL_OFFSET, range.getEndOffset)
            map
          }

          private def contextWith(range: TextRange)
                                 (implicit context: InsertionContext) = new InsertionContext(
            offsetMap(context.getDocument, range),
            context.getCompletionChar,
            context.getElements,
            context.getFile,
            context.getEditor,
            context.shouldAddCompletionChar
          )
        }
      }
    }
  )

  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScFieldId]),
    new AotCompletionProvider[ScValueDeclaration] {

      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit =
        positionFromParameters(parameters).getContext.getContext.getContext match {
          case _: ScVariableDeclaration | _: ScValueDeclaration => super.addCompletions(parameters, context, resultSet)
          case _ =>
        }

      override protected def createElement(text: String, child: PsiElement): ScValueDeclaration =
        ScalaPsiElementFactory.createDeclarationFromText(s"val $text", child.getContext, child)
          .asInstanceOf[ScValueDeclaration]

      override protected def context(element: ScValueDeclaration): PsiElement =
        super.context(element).getContext.getContext

      override protected def typeElement(declaration: ScValueDeclaration): Option[ScalaPsiElement] = declaration.typeElement

      override protected def createConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet): MyConsumer = new MyConsumer(prefixMatcher, resultSet) {

        private val consumed = mutable.Set.empty[String]

        override protected def consume(element: LookupElement, name: String): Unit = {
          if (consumed.add(name)) super.consume(element, name)
        }

        override protected def createRenderer(name: String): MyElementRenderer = new MyElementRenderer {

          override def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation): Unit = {
            super.renderElement(element, presentation)

            presentation.setIcon(null)
            presentation.setTailText(null)
          }

          override protected def itemText(presentation: LookupElementPresentation): String = name
        }

        override protected def createInsertHandler(name: String): MyInsertHandler =
          (_: Document, _: TextRange) => name
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

      val replacement = createElement(prefix + ": " + capitalize(element.getText), element)

      val context = this.context(replacement)
      replacement.setContext(context, context.getLastChild)

      val identifier = typeElement(replacement)
        .flatMap(findIdentifier)
        .get

      val newParameters = parameters.withPosition(identifier, identifier.getTextRange.getStartOffset + prefix.length)

      val newResultSet = resultSet.withPrefixMatcher(prefixMatcher.cloneWithPrefix(capitalize(prefix)))
      newResultSet.runRemainingContributors(newParameters, createConsumer(prefixMatcher, newResultSet), true)
    }

    protected def createElement(text: String, child: PsiElement): E

    protected def context(element: E): PsiElement = element.getContext.getContext

    protected def typeElement(element: E): Option[ScalaPsiElement]

    protected def createConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet): MyConsumer

    private def findIdentifier(element: ScalaPsiElement): Option[PsiElement] =
      element.depthFirst()
        .find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)

    protected abstract class MyConsumer(prefixMatcher: PrefixMatcher, resultSet: CompletionResultSet) extends Consumer[CompletionResult] {

      private val lowerCasePrefix = prefixMatcher.getPrefix.takeWhile(_.isLower)

      def consume(result: CompletionResult) {
        val element = result.getLookupElement
        consume(element, suggestName(element.getLookupString))
      }

      protected def consume(element: LookupElement, name: String): Unit = {
        import LookupElementDecorator._

        resultSet.consume(withInsertHandler(
          withRenderer(element, createRenderer(name)),
          createInsertHandler(name)
        ))
      }

      protected def createRenderer(name: String): MyElementRenderer

      protected def createInsertHandler(name: String): MyInsertHandler

      private def suggestName(entity: String): String = {
        import StringUtil.{capitalize, decapitalize}

        val name = entity.indexOf(capitalize(lowerCasePrefix)) match {
          case -1 => entity
          case index => entity.substring(index)
        }

        decapitalize(name)
      }

      protected trait MyElementRenderer extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {

        def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation): Unit = {
          element.getDelegate.renderElement(presentation)
          presentation.setItemText(itemText(presentation))
        }

        protected def itemText(presentation: LookupElementPresentation): String
      }

      protected trait MyInsertHandler extends InsertHandler[LookupElement] {

        def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
          val range = TextRange.create(context.getStartOffset, context.getTailOffset)
          handleInsert(item, range)(context)
        }

        protected def handleInsert(item: LookupElement, range: TextRange)
                                  (implicit context: InsertionContext): Unit = {
          val document = context.getDocument
          document.replaceString(
            range.getStartOffset,
            range.getEndOffset,
            replacement(document, range)
          )
          context.commitDocument()
        }

        protected def replacement(document: Document, range: TextRange): String
      }

    }

  }

}
