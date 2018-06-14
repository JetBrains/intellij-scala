package org.jetbrains.plugins.scala.codeInsight

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.openapi.editor.{Inlay, InlayModel}
import com.intellij.openapi.util.{Key, TextRange}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.JavaConverters._

package object implicits {
  private val ScalaImplicitHintKey = Key.create[Boolean]("SCALA_IMPLICIT_HINT")

  type Hint = (String, ScExpression, String)

  implicit class Model(val model: InlayModel) extends AnyVal {
    def inlaysIn(range: TextRange): Seq[Inlay] =
      model.getInlineElementsInRange(range.getStartOffset + 1, range.getEndOffset - 1)
        .asScala
        .filter(ScalaImplicitHintKey.isIn)

    def addInlay(info: InlayInfo, error: Boolean): Unit = {
      val renderer = new TextRenderer(info.getText,
        error = error,
        suffix = info.getRelatesToPrecedingText)

      val inlay = model.addInlineElement(info.getOffset, info.getRelatesToPrecedingText, renderer)
      Option(inlay).foreach(_.putUserData(ScalaImplicitHintKey, true))
    }
  }
}
