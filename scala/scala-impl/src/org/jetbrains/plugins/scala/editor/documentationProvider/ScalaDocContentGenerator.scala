package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}

trait ScalaDocContentGenerator {
  /**
   * Appends the contents
   */
  def appendTagDescriptionText(
    buffer: StringBuilder,
    tag: ScDocTag
  ): Unit

  @Nls
  def tagDescriptionText(
    tag: ScDocTag
  ): String = {
    val buffer = new StringBuilder
    appendTagDescriptionText(buffer, tag)
    buffer.result()
  }

  def appendDescriptionParts(
    buffer: StringBuilder,
    comment: ScDocComment,
  ): Boolean
}
