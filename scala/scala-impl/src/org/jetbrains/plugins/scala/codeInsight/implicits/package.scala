package org.jetbrains.plugins.scala.codeInsight

import com.intellij.openapi.editor.{Inlay, InlayModel}
import com.intellij.openapi.util.{Key, TextRange}

import scala.collection.JavaConverters._

package object implicits {
  private val ScalaImplicitHintKey = Key.create[Boolean]("SCALA_IMPLICIT_HINT")

  implicit class Model(val model: InlayModel) extends AnyVal {
    def inlaysIn(range: TextRange): Seq[Inlay] =
      model.getInlineElementsInRange(range.getStartOffset + 1, range.getEndOffset - 1)
        .asScala
        .filter(ScalaImplicitHintKey.isIn)

    def add(hint: Hint): Unit = {
      Option(hint.addTo(model)).foreach(_.putUserData(ScalaImplicitHintKey, true))
    }
  }
}
