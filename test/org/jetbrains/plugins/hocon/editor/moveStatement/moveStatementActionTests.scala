package org.jetbrains.plugins.hocon.editor.moveStatement

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.plugins.hocon.{HoconEditorActionTest, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
 * @author ghik
 */

object HoconMoveStatementDownActionTest extends TestSuiteCompanion[HoconMoveStatementDownActionTest]

@RunWith(classOf[AllTests])
class HoconMoveStatementDownActionTest
  extends HoconEditorActionTest(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION, "moveStatement/both")

object HoconMoveStatementDownOnlyActionTest extends TestSuiteCompanion[HoconMoveStatementDownOnlyActionTest]

@RunWith(classOf[AllTests])
class HoconMoveStatementDownOnlyActionTest
  extends HoconEditorActionTest(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION, "moveStatement/down")

object HoconMoveStatementUpActionTest extends TestSuiteCompanion[HoconMoveStatementUpActionTest]

@RunWith(classOf[AllTests])
class HoconMoveStatementUpActionTest
  extends HoconEditorActionTest(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION, "moveStatement/both") {

  override protected def preprocessData(parts: Seq[String]) = parts.reverse
}
