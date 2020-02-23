package org.jetbrains.plugins.scala.lang.completion.lookups

import java.util

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.{LookupElementDecorator, LookupElementPresentation}
import gnu.trove.THashSet
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaInsertHandler

/**
  * @author Alefas
  * @since 31.03.12
  */

class ScalaChainLookupElement(val prefix: ScalaLookupItem, val element: ScalaLookupItem) extends LookupElementDecorator[ScalaLookupItem](element) {
  override def getAllLookupStrings: util.Set[String] = {
    val strings: util.Set[String] = getDelegate.getAllLookupStrings
    val result: THashSet[String] = new THashSet[String]
    result.addAll(strings)
    result.add(getLookupString)
    result
  }

  override def getLookupString: String = prefix.getLookupString + "." + element.getLookupString

  override def toString: String = getLookupString

  override def renderElement(presentation: LookupElementPresentation): Unit = {
    val prefixPresentation: LookupElementPresentation = new LookupElementPresentation
    prefix.renderElement(prefixPresentation)
    val old = element.someSmartCompletion
    element.someSmartCompletion = false
    element.renderElement(presentation)
    element.someSmartCompletion = old
    presentation.setItemText(prefixPresentation.getItemText + "." + presentation.getItemText)
    if (element.someSmartCompletion) {
      presentation.setItemText("Some(" + presentation.getItemText + ")")
    }
  }

  override def handleInsert(context: InsertionContext): Unit = {
    val editor = context.getEditor
    val caretModel = editor.getCaretModel
    val offsetForPrefix = caretModel.getOffset + (if (element.someSmartCompletion) 5 else 0) - element.getLookupString.length - 1
    element.handleInsert(context)
    val document = context.getDocument
    val (count, isAccessor) = ScalaInsertHandler.getItemParametersAndAccessorStatus(prefix.element)
    val addParams = count >= 0 && !(count == 0 && isAccessor)
    if (addParams) {
      document.insertString(offsetForPrefix, "()")
      //      val offset = editor.getCaretModel.getOffset
      //      editor.getCaretModel.moveToOffset(offset + 2)
    }
  }
}
