package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueDeclaration, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable

/**
  * @author Pavel Fatin
  */
class ScalaAotCompletionContributor extends ScalaCompletionContributor {

  import ScalaAotCompletionContributor._

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScParameter]),
    new CompletionProvider[CompletionParameters] {

      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        addCompletions0(parameters, result, typed = true) { (text, element) =>
          val parameter = createParameterFrom(text, element)
          val context = element.getContext.getContext
          (parameter, context, parameter.paramType.get)
        }
      }
    })

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScFieldId]),
    new CompletionProvider[CompletionParameters] {

      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val scope = positionFromParameters(parameters).getContext.getContext.getContext

        if (!scope.isInstanceOf[ScVariableDeclaration] && !scope.isInstanceOf[ScValueDeclaration]) return

        addCompletions0(parameters, result, typed = false) { (text, element) =>
          val declaration = createValueDeclarationFrom(text, element)
          val context = element.getContext.getContext.getContext.getContext
          (declaration, context, declaration.typeElement.get)
        }
      }
    })

  private def addCompletions0(parameters: CompletionParameters, result: CompletionResultSet, typed: Boolean)
                             (factory: (String, PsiElement) => (ScalaPsiElement, PsiElement, ScalaPsiElement)): Unit = {
    import StringUtil.capitalize
    val element = positionFromParameters(parameters)
    val settings = ScalaProjectSettings.getInstance(element.getProject)

    if (!settings.isAotCompletion) return

    val prefixMatcher = result.getPrefixMatcher
    val prefix = prefixMatcher.getPrefix

    if (!isSuitableIdentifier(prefix)) return

    val (replacement, context, typeElement) = factory(prefix + ": " + capitalize(element.getText), element)
    replacement.setContext(context, context.getLastChild)
    val identifier = typeIdentifierIn(typeElement)

    val parameters0 = parameters.withPosition(identifier, identifier.getTextRange.getStartOffset + prefix.length)
    val result0 = result.withPrefixMatcher(prefixMatcher.cloneWithPrefix(capitalize(prefix)))
    result0.runRemainingContributors(parameters0, new MyConsumer(prefix, typed, result0), true)
  }
}

private object ScalaAotCompletionContributor {

  def typeIdentifierIn(element: PsiElement): PsiElement =
    element.depthFirst()
      .find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)
      .get

  def isSuitableIdentifier(s: String): Boolean = isIdentifier(s) && s.forall(_.isLetterOrDigit)

  import ScalaPsiElementFactory.{createDeclarationFromText, createParamClausesWithContext}

  def createParameterFrom(text: String, original: PsiElement): ScParameter =
    createParamClausesWithContext(s"($text)", original.getContext, original).params.head

  def createValueDeclarationFrom(text: String, original: PsiElement): ScValueDeclaration =
    createDeclarationFromText(s"val $text", original.getContext, original).asInstanceOf[ScValueDeclaration]
}

private class MyConsumer(prefix: String, typed: Boolean, resultSet: CompletionResultSet) extends Consumer[CompletionResult] {

  private val consumed = mutable.Set.empty[String]
  private val lowerCasePrefix = prefix.takeWhile(_.isLower)

  def consume(result: CompletionResult) {
    val element = result.getLookupElement
    val name = suggestNameFor(element.getLookupString)

    if (typed || consumed.add(name)) {
      import LookupElementDecorator._

      val renderingDecorator = withRenderer(element, new MyElementRenderer(name, typed))
      resultSet.consume(withInsertHandler(renderingDecorator, new MyInsertHandler(name, typed)))
    }
  }

  private def suggestNameFor(entity: String): String = {
    import StringUtil.{capitalize, decapitalize}

    val name = entity.indexOf(capitalize(lowerCasePrefix)) match {
      case -1 => entity
      case index => entity.substring(index)
    }

    decapitalize(name)
  }
}

private class MyElementRenderer(name: String, typed: Boolean) extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {
  def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation): Unit = {
    element.getDelegate.renderElement(presentation)

    val text = name + (if (typed) ": " + presentation.getItemText else "")
    presentation.setItemText(text)

    if (!typed) {
      presentation.setIcon(null)
      presentation.setTailText(null)
    }
  }
}

private class MyInsertHandler(name: String, typed: Boolean) extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
    val begin = context.getStartOffset
    val end = context.getTailOffset

    val document = context.getDocument
    val text = document.getText(TextRange.create(begin, end))

    val replacement = name + (if (typed) ": " + text else "")
    document.replaceString(begin, end, replacement)
    context.commitDocument()

    if (typed) {
      val element = ScalaLookupItem.original(item)
      val range = TextRange.from(begin, text.length).shiftRight(name.length + 2)
      element.handleInsert(MyInsertHandler.contextWith(context, range))
    }
  }
}

private object MyInsertHandler {
  private def contextWith(context: InsertionContext, range: TextRange): InsertionContext = {
    val map = new OffsetMap(context.getDocument)
    map.addOffset(CompletionInitializationContext.START_OFFSET, range.getStartOffset)
    map.addOffset(InsertionContext.TAIL_OFFSET, range.getEndOffset)

    new InsertionContext(map, context.getCompletionChar, context.getElements, context.getFile,
      context.getEditor, context.shouldAddCompletionChar)
  }
}

