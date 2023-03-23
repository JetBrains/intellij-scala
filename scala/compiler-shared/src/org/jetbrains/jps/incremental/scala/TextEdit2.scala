package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.scala.Client.PosInfo
import xsbti.TextEdit

case class TextEdit2(position: Position2, newText: String)

case class Position2(startOffset: Int, endOffset: Int)

object TextEdit2 {
  def fromTextEdit(textEdit: TextEdit): TextEdit2 = {
    val position = Position2(textEdit.position().startOffset().get(), textEdit.position().endOffset().get())
    TextEdit2(position, textEdit.newText())
  }
}
