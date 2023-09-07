package org.jetbrains.plugins.scalaDirective.lang.completion.lookups

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import org.jetbrains.plugins.scala.extensions.ElementType
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

object ScalaDirectiveDependencyLookupItem {
  def apply(text: String, valueKind: ScalaDirectiveValueKind): LookupElement = LookupElementBuilder
    .create(text)
    .withInsertHandler { (context, item) =>
      context.getFile.findElementAt(context.getStartOffset) match {
        case value@ElementType(ScalaDirectiveTokenTypes.tDIRECTIVE_VALUE) =>
          val newValueText = valueKind.wrap(item.getLookupString)
          val newValue = ScalaPsiElementFactory.createDirectiveValueFromText(newValueText)(context.getProject)
          value.replace(newValue)
        case _ =>
      }
    }
}
