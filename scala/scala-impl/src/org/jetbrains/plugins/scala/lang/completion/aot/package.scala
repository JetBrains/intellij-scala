package org.jetbrains.plugins.scala.lang
package completion

import com.intellij.codeInsight.completion.{InsertionContext, InsertHandler => IJInsertHandler}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementDecorator, LookupElementPresentation, LookupElementRenderer => IJLookupElementRenderer}

package object aot {

  private[aot] type Decorator = LookupElementDecorator[LookupElement]

  private[aot] class InsertHandler(itemText: String) extends IJInsertHandler[Decorator] {

    def handleInsert(context: InsertionContext,
                     decorator: Decorator): Unit = {
      context.getDocument.replaceString(
        context.getStartOffset,
        context.getTailOffset,
        itemText
      )
      context.commitDocument()
    }
  }

  private[aot] class LookupElementRenderer(itemText: String) extends IJLookupElementRenderer[Decorator] {

    def renderElement(decorator: Decorator,
                      presentation: LookupElementPresentation): Unit = {
      decorator.getDelegate.renderElement(presentation)
      presentation.setItemText(itemText)
    }
  }

}
