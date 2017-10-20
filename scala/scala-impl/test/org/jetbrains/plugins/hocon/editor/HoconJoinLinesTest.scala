package org.jetbrains.plugins.hocon
package editor

import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_JOIN_LINES
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
  * @author ghik
  */
@RunWith(classOf[AllTests])
class HoconJoinLinesTest extends HoconEditorActionTest(ACTION_EDITOR_JOIN_LINES, "joinLines")

object HoconJoinLinesTest extends TestSuiteCompanion[HoconJoinLinesTest]
