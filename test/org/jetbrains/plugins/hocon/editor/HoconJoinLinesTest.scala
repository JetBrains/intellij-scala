package org.jetbrains.plugins.hocon.editor

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.plugins.hocon.{HoconEditorActionTest, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
 * @author ghik
 */
object HoconJoinLinesTest extends TestSuiteCompanion[HoconJoinLinesTest]

@RunWith(classOf[AllTests])
class HoconJoinLinesTest extends HoconEditorActionTest(IdeActions.ACTION_EDITOR_JOIN_LINES, "joinLines")