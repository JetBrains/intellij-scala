package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.apache.commons.lang.StringEscapeUtils

trait TextEscaper {
  def escape(text: String): String
}

object TextEscaper {
  object Html extends TextEscaper {
    override def escape(text: String): String = StringEscapeUtils.escapeHtml(text)
  }
  object Noop extends TextEscaper {
    override def escape(text: String): String = text
  }
}