package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupElementRenderer}
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.completion.MyConsumer._
import org.jetbrains.plugins.scala.lang.completion.MyInsertHandler._
import org.jetbrains.plugins.scala.lang.completion.ScalaParameterCompletionContributor._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * @author Pavel Fatin
 */
class ScalaParameterCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
          withParent(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {

    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val settings = ScalaProjectSettings.getInstance(element.getProject)
      if (settings.isAotCompletion) {
        addCompletions0(element, parameters, result)
      }
    }
  })

  private def addCompletions0(element: PsiElement, parameters: CompletionParameters, result: CompletionResultSet) {
    val text = element.getText
    val prefix = text.substring(0, text.length - CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.length)

    if (prefix.isEmpty) return

    val parameter = createParameterFrom(prefix + ": " + capitalize(text), element)
    parameter.setContext(element.getParent.getParent, null)

    val identifier = typeIdentifierIn(parameter)
    val parameters0 = parameters.withPosition(identifier, identifier.getTextRange.getStartOffset + prefix.length)
    val result0 = result.withPrefixMatcher(result.getPrefixMatcher.cloneWithPrefix(capitalize(prefix)))
    result0.runRemainingContributors(parameters0, new MyConsumer(prefix, result0), true)
  }
}

private object ScalaParameterCompletionContributor {
  def typeIdentifierIn(parameter: ScParameter): PsiElement =
    parameter.paramType.get.depthFirst.find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER).get

  def capitalize(s: String): String =
    if (s.length == 0) s else s.substring(0, 1).toUpperCase + s.substring(1)

  def createParameterFrom(text: String, original: PsiElement): ScParameter = {
    val fragment = new ScalaCodeFragment(original.getProject, "def f(" + text + ") {}")
    fragment.forceResolveScope(original.getResolveScope)
    val function = fragment.getFirstChild.asInstanceOf[ScFunction]
    function.parameters(0)
  }
}

private class MyConsumer(prefix: String, resultSet: CompletionResultSet) extends Consumer[CompletionResult] {
  def consume(result: CompletionResult) {
    val name = suggestNameFor(prefix, result.getLookupElement.getLookupString)
    val renderingDecorator = LookupElementDecorator.withRenderer(result.getLookupElement, new MyElementRenderer(name))
    val insertionDecorator = LookupElementDecorator.withInsertHandler(renderingDecorator, new MyInsertHandler(name))
    resultSet.consume(insertionDecorator)
  }
}

private object MyConsumer {
  def suggestNameFor(prefix: String, entity: String): String = {
    val i = entity.indexOf(capitalize(prefix.takeWhile(_.isLower)))
    val name = if (i >= 0) entity.substring(i) else entity
    decapitalize(name)
  }

  def decapitalize(s: String): String =
    if (s.length == 0) s else s.substring(0, 1).toLowerCase + s.substring(1)
}

private class MyElementRenderer(name: String) extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {
  def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation) = {
    element.getDelegate.renderElement(presentation)
    presentation.setItemText(name + ": " + presentation.getItemText)
  }
}

private class MyInsertHandler(name: String) extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement) = {
    val begin = context.getStartOffset
    val end = context.getTailOffset

    val document = context.getDocument
    val text = document.getText(TextRange.create(begin, end))

    document.replaceString(begin, end, name + ": " + text)
    context.commitDocument()

    val element = ScalaLookupItem.original(item)
    val range = TextRange.from(begin, text.length).shiftRight(name.length + 2)
    element.handleInsert(contextWith(context, range))
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

