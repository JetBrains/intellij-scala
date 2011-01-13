package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.LogicalPosition

/**
 * Pavel Fatin
 */

class CaretModelMock(offset: Int, pos: LogicalPosition) extends CaretModelStub {
  override def getOffset: Int = offset

  override def getLogicalPosition: LogicalPosition = pos
}