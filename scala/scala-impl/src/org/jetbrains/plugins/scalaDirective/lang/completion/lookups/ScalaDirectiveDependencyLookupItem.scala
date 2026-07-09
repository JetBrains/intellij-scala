package org.jetbrains.plugins.scalaDirective.lang.completion.lookups

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import org.jetbrains.plugins.scala.extensions.ElementType
import org.jetbrains.plugins.scala.lang.completion.InsertionContextExt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

import javax.swing.Icon

object ScalaDirectiveDependencyLookupItem {
  def apply(text: String, obj: AnyRef, valueKind: ScalaDirectiveValueKind,
            scheduleAutoPopupAfterInsert: Boolean = false,
            icon: Option[Icon] = None): LookupElement = {
    val builder = LookupElementBuilder
      .create(obj, text)
      .withInsertHandler { (context, item) =>
        context.getFile.findElementAt(context.getStartOffset) match {
          case value@ElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE) =>
            val newValueText = valueKind.wrap(item.getLookupString)
            val newValue = ScalaPsiElementFactory.createDirectiveValueFromText(newValueText)(context.getProject)
            value.replace(newValue)
            if (scheduleAutoPopupAfterInsert) {
              context.scheduleAutoPopup()
            }
          case _ =>
        }
      }
    icon.fold(builder)(builder.withIcon)
  }

  def apply(text: String, valueKind: ScalaDirectiveValueKind, scheduleAutoPopupAfterInsert: Boolean, icon: Option[Icon]): LookupElement =
    apply(text, text, valueKind, scheduleAutoPopupAfterInsert, icon)
}
