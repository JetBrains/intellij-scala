package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.{VisualPosition, Caret, LogicalPosition}
import java.util
import com.intellij.openapi.util.Segment

/**
 * Pavel Fatin
 */

class CaretModelMock(offset: Int, pos: LogicalPosition) extends CaretModelStub {
  override def getOffset: Int = offset

  override def getLogicalPosition: LogicalPosition = pos
}