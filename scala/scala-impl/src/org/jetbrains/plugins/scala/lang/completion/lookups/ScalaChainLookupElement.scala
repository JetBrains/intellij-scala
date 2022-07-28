package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.{LookupElementDecorator, LookupElementPresentation}
import gnu.trove.THashSet

import java.util

final class ScalaChainLookupElement(override val getDelegate: ScalaLookupItem,
                                    private val prefix: ScalaLookupItem)
  extends LookupElementDecorator[ScalaLookupItem](getDelegate) {

  override def getAllLookupStrings: util.Set[String] = {
    val result = new THashSet[String](getDelegate.getAllLookupStrings)
    result.add(getLookupString)
    result
  }

  override def getLookupString: String = prefix.getLookupString + "." + getDelegate.getLookupString

  override def toString: String = getLookupString

  override def renderElement(presentation: LookupElementPresentation): Unit = {
    val prefixPresentation = new LookupElementPresentation
    prefix.renderElement(prefixPresentation)

    val element = getDelegate
    val old = element.someSmartCompletion
    element.someSmartCompletion = false
    element.renderElement(presentation)
    element.someSmartCompletion = old

    presentation.setItemText(prefixPresentation.getItemText + "." + presentation.getItemText)
    element.wrapOptionIfNeeded(prefixPresentation)
  }

  override def handleInsert(context: InsertionContext): Unit = {
    val element = getDelegate
    val editor = context.getEditor
    val offsetForPrefix = editor.getCaretModel.getOffset + element.shiftOptionIfNeeded - element.getLookupString.length - 1
    element.handleInsert(context)

    val document = context.getDocument
    val (count, isAccessor) = handlers.ScalaInsertHandler.getItemParametersAndAccessorStatus(prefix.getPsiElement)
    val addParams = count >= 0 && !(count == 0 && isAccessor)
    if (addParams) {
      document.insertString(offsetForPrefix, "()")
      //      val offset = editor.getCaretModel.getOffset
      //      editor.getCaretModel.moveToOffset(offset + 2)
    }
  }
}
