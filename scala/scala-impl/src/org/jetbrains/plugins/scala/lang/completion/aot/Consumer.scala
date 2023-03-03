package org.jetbrains.plugins.scala.lang.completion.aot

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionResult, CompletionResultSet}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupElementRenderer}
import com.intellij.openapi.util.text.StringUtil.{capitalize, decapitalize}
import com.intellij.util.{Consumer => IJConsumer}

import scala.annotation.nowarn
import scala.collection.mutable

@nowarn("msg=trait Consumer in package util is deprecated") //We have to use deprecated consumer because it's still used in upstream API
private[completion] sealed abstract class Consumer(originalResultSet: CompletionResultSet)
  extends IJConsumer[CompletionResult] {

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
    val rendererDecorator = new LookupElementDecorator[LookupElement](lookupElement) {
      override def renderElement(presentation: LookupElementPresentation): Unit = {
        super.renderElement(presentation)
        augmentPresentation(itemText)(presentation)
      }

      override def getExpensiveRenderer: LookupElementRenderer[_ <: LookupElement] = {
        val renderer = getDelegate.getExpensiveRenderer.asInstanceOf[LookupElementRenderer[LookupElement]]
        if (renderer eq null) null
        else (element: LookupElementDecorator[_], presentation) => {
          renderer.renderElement(element.getDelegate.asInstanceOf[LookupElement], presentation)
          augmentPresentation(itemText)(presentation)
        }
      }
    }

    val decoratedLookupElement = LookupElementDecorator.withInsertHandler(rendererDecorator, createInsertHandler(itemText))
    resultSet.consume(decoratedLookupElement)
  }

  protected def augmentPresentation(itemText: String): LookupElementPresentation => Unit

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

  override final protected def augmentPresentation(itemText: String): LookupElementPresentation => Unit = { presentation =>
    presentation.setItemText(itemText)
    presentation.setTypeText(null)
  }
}

private[completion] final class UntypedConsumer(originalResultSet: CompletionResultSet) extends Consumer(originalResultSet) {

  private val consumed = mutable.Set.empty[String]

  override protected def consume(lookupElement: LookupElement, itemText: String): Unit =
    if (consumed.add(itemText)) {
      super.consume(lookupElement, itemText)
    }

  override protected def augmentPresentation(itemText: String): LookupElementPresentation => Unit = { presentation =>
    presentation.setItemText(itemText)
    presentation.setTypeText(null)
    presentation.setIcon(null)
    presentation.setTailText(null)
    presentation.setStrikeout(false)
  }
}