package org.jetbrains.plugins.scala.codeInsight.implicits

import org.jetbrains.plugins.scala.annotator.hints.Text

case class TextAtPoint(text: Text, inlay: Inlay, relativeX: Int)