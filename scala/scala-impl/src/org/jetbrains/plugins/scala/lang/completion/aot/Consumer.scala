package org.jetbrains.plugins.scala.lang
package completion
package aot

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionResult, CompletionResultSet}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation}
import com.intellij.openapi.util.text.StringUtil.{capitalize, decapitalize}
import com.intellij.util.{Consumer => IJConsumer}

import scala.collection.mutable

private[completion] sealed abstract class Consumer(originalResultSet: CompletionResultSet) extends IJConsumer[CompletionResult] {

  private val prefixMatcher = originalResultSet.getPrefixMatcher
  private val prefix = prefixMatcher.getPrefix
  private val targetPrefix = capitalize(prefix.takeWhile(_.isLower))

  private val resultSet: CompletionResultSet = originalResultSet.withPrefixMatcher {
    prefixMatcher.cloneWithPrefix(capitalize(prefix))
  }

  final def runRemainingContributors(parameters: CompletionParameters): Unit = {
    resultSet.runRemainingContributors(parameters, this, true)
  }

  override final def consume(result: CompletionResult): Unit = {
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

  protected def createRenderer(itemText: String): LookupElementRenderer

  protected def createInsertHandler(itemText: String) = new InsertHandler(itemText)

  protected def suggestItemText(lookupString: String): String = decapitalize(
    lookupString.indexOf(targetPrefix) match {
      case -1 => lookupString
      case index => lookupString.substring(index)
    }
  )
}

private[completion] class TypedConsumer(originalResultSet: CompletionResultSet) extends Consumer(originalResultSet) {

  override final protected def suggestItemText(lookupString: String): String =
    super.suggestItemText(lookupString) + Delimiter + lookupString

  override final protected def createRenderer(itemText: String) =
    new LookupElementRenderer(itemText)
}

private[completion] final class UntypedConsumer(originalResultSet: CompletionResultSet) extends Consumer(originalResultSet) {

  private val consumed = mutable.Set.empty[String]

  override protected def consume(lookupElement: LookupElement, itemText: String): Unit =
    if (consumed.add(itemText)) {
      super.consume(lookupElement, itemText)
    }

  //noinspection TypeAnnotation
  override protected def createRenderer(itemText: String) = new LookupElementRenderer(itemText) {

    override def renderElement(decorator: Decorator, presentation: LookupElementPresentation): Unit = {
      super.renderElement(decorator, presentation)

      presentation.setIcon(null)
      presentation.setTypeText(null)
      presentation.setTailText(null)
      presentation.setStrikeout(false)
    }
  }
}