package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil

class ScalaSourcePositionHighlighter extends SourcePositionHighlighter {
  override def getHighlightRange(sourcePosition: SourcePosition): TextRange = {
    if (sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
      Option(sourcePosition.getElementAt)
        .flatMap { element =>
          if (ScalaLineBreakpointType.isReturnKeyword(element) &&
              ScalaLineBreakpointType.findSingleConditionalReturn(sourcePosition).contains(element)) {
            Some(element)
          } else DebuggerUtil.getContainingMethod(element)
        }
        .map(_.getTextRange).orNull
    }
    else null
  }
}
