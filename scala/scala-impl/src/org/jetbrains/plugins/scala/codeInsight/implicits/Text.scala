package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.markup.TextAttributes

case class Text(string: String, attributes: Option[TextAttributes] = None)