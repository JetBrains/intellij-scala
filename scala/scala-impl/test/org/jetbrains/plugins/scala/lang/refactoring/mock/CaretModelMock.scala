package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.{CaretState, LogicalPosition}

import java.util

class CaretModelMock(offset: Int, pos: LogicalPosition) extends CaretModelStub {

  override def setCaretsAndSelections(caretStates: util.List[_ <: CaretState]): Unit = ()

  override def setCaretsAndSelections(caretStates: util.List[_ <: CaretState], updateSystemSelection: Boolean): Unit = ()

  override def getOffset: Int = offset

  override def getLogicalPosition: LogicalPosition = pos
}