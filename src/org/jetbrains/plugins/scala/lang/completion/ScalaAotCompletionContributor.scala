package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupElementRenderer}
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.completion.MyConsumer._
import org.jetbrains.plugins.scala.lang.completion.MyInsertHandler._
import org.jetbrains.plugins.scala.lang.completion.ScalaAotCompletionContributor._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypedDeclaration, ScValueDeclaration, ScVariableDeclaration}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * @author Pavel Fatin
 */
class ScalaAotCompletionContributor extends ScalaCompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
          withParent(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {

    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      addCompletions0(parameters, result, typed = true) { (text, element) =>
        val parameter = createParameterFrom(text, element)
        val context = element.getContext.getContext
        parameter.setContext(context, context.getLastChild)
        typeIdentifierIn(parameter)
      }
    }
  })

  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
          withParent(classOf[ScFieldId]), new CompletionProvider[CompletionParameters] {

    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val scope = positionFromParameters(parameters).getContext.getContext.getContext

      if (!scope.isInstanceOf[ScVariableDeclaration] && !scope.isInstanceOf[ScValueDeclaration]) return

      addCompletions0(parameters, result, typed = false) { (text, element) =>
        val declaration = createValueDeclarationFrom(text, element)
        val context = element.getContext.getContext.getContext.getContext
        declaration.setContext(context, context.getLastChild)
        typeIdentifierIn(declaration)
      }
    }
  })

  private def addCompletions0(parameters: CompletionParameters, result: CompletionResultSet, typed: Boolean)
                             (factory: (String, PsiElement) => PsiElement) {

    val element = positionFromParameters(parameters)
    val settings = ScalaProjectSettings.getInstance(element.getProject)

    if (!settings.isAotCompletion) return

    val text = element.getText
    val prefix = result.getPrefixMatcher.getPrefix

    if (!isSuitableIdentifier(prefix)) return

    val identifier = factory(prefix + ": " + capitalize(text), element)

    val parameters0 = parameters.withPosition(identifier, identifier.getTextRange.getStartOffset + prefix.length)
    val result0 = result.withPrefixMatcher(result.getPrefixMatcher.cloneWithPrefix(capitalize(prefix)))
    result0.runRemainingContributors(parameters0, new MyConsumer(prefix, typed, result0), true)
  }

  private def isSuitableIdentifier(s: String) = ScalaNamesUtil.isIdentifier(s) && s.forall(_.isLetterOrDigit)
}

private object ScalaAotCompletionContributor {
  def typeIdentifierIn(parameter: ScParameter): PsiElement =
    parameter.paramType.get.depthFirst.find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER).get

  def typeIdentifierIn(declaration: ScTypedDeclaration): PsiElement =
    declaration.typeElement.get.depthFirst.find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER).get

  def capitalize(s: String): String =
    if (s.length == 0) s else s.substring(0, 1).toUpperCase + s.substring(1)

  def createParameterFrom(text: String, original: PsiElement): ScParameter = {
    val fragment = new ScalaCodeFragment(original.getProject, "def f(" + text + ") {}")
    fragment.forceResolveScope(original.getResolveScope)
    val function = fragment.getFirstChild.asInstanceOf[ScFunction]
    function.parameters.head
  }

  def createValueDeclarationFrom(text: String, original: PsiElement): ScValueDeclaration = {
    val fragment = new ScalaCodeFragment(original.getProject, "val " + text)
    fragment.forceResolveScope(GlobalSearchScope.fileScope(original.getContainingFile))
    fragment.getFirstChild.asInstanceOf[ScValueDeclaration]
  }
}

private class MyConsumer(prefix: String, typed: Boolean, resultSet: CompletionResultSet) extends Consumer[CompletionResult] {
  private var consumed = Set[String]()

  def consume(result: CompletionResult) {
    val element = result.getLookupElement

    val name = suggestNameFor(prefix, element.getLookupString)

    val renderingDecorator = LookupElementDecorator.withRenderer(element,
      new MyElementRenderer(name, typed))

    val insertionDecorator = LookupElementDecorator.withInsertHandler(renderingDecorator,
      new MyInsertHandler(name, typed))

    if (typed) {
      resultSet.consume(insertionDecorator)
    } else if (!consumed.contains(name)) {
      resultSet.consume(insertionDecorator)
      consumed += name
    }
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

private class MyElementRenderer(name: String, typed: Boolean) extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {
  def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation) = {
    element.getDelegate.renderElement(presentation)

    val text = if (typed) name + ": " + presentation.getItemText else name
    presentation.setItemText(text)

    if (!typed) {
      presentation.setIcon(null)
      presentation.setTailText(null)
    }
  }
}

private class MyInsertHandler(name: String, typed: Boolean) extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement) = {
    val begin = context.getStartOffset
    val end = context.getTailOffset

    val document = context.getDocument
    val text = document.getText(TextRange.create(begin, end))

    val replacement = if (typed) name + ": " + text else name
    document.replaceString(begin, end, replacement)
    context.commitDocument()

    if (typed) {
      val element = ScalaLookupItem.original(item)
      val range = TextRange.from(begin, text.length).shiftRight(name.length + 2)
      element.handleInsert(contextWith(context, range))
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

