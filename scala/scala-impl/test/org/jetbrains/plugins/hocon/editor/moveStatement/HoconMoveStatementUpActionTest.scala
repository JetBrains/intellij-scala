package org.jetbrains.plugins.hocon
package editor
package moveStatement

import com.intellij.openapi.actionSystem.IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
  * @author ghik
  */
@RunWith(classOf[AllTests])
class HoconMoveStatementUpActionTest extends HoconEditorActionTest(ACTION_MOVE_STATEMENT_UP_ACTION, "moveStatement/both") {

  override protected def preprocessData(parts: Seq[String]): Seq[String] = parts.reverse
}

object HoconMoveStatementUpActionTest extends TestSuiteCompanion[HoconMoveStatementUpActionTest]
