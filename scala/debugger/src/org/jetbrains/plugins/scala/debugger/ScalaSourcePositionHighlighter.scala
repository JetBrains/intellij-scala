package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaLanguage

class ScalaSourcePositionHighlighter extends SourcePositionHighlighter {
  override def getHighlightRange(sourcePosition: SourcePosition): TextRange = sourcePosition match {
    case _ if !isScalaLanguage(sourcePosition) => null
    case _: ScalaSourcePositionWithWholeLineHighlighted => null
    case l: ScalaLambdaSourcePosition => l.getElementAt.getTextRange
    case _ => null
  }

  private def isScalaLanguage(sourcePosition: SourcePosition): Boolean =
    sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
}
