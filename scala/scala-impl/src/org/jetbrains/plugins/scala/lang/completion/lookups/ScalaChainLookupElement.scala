package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupElementRenderer}

final class ScalaChainLookupElement(delegate: ScalaLookupItem, prefix: ScalaLookupItem)
  extends LookupElementDecorator[ScalaLookupItem](delegate) {

  override def getAllLookupStrings: java.util.Set[String] = {
    val result = new java.util.HashSet[String](super.getAllLookupStrings)
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

  override def getExpensiveRenderer: LookupElementRenderer[_ <: LookupElement] = {
    (_: LookupElementDecorator[_], presentation) => {
      val prefixPresentation = new LookupElementPresentation()
      prefix.renderElement(prefixPresentation)

      val element = getDelegate
      val old = element.someSmartCompletion
      element.someSmartCompletion = false
      element.getExpensiveRenderer.asInstanceOf[LookupElementRenderer[LookupElement]].renderElement(element, presentation)
      element.someSmartCompletion = old

      presentation.setItemText(prefixPresentation.getItemText + "." + presentation.getItemText)
      element.wrapOptionIfNeeded(prefixPresentation)
    }
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
