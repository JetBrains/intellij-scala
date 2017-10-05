package org.jetbrains.plugins.hocon.editor

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.plugins.hocon.{HoconEditorActionTest, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
 * @author ghik
 */
object HoconEnterActionTest extends TestSuiteCompanion[HoconEnterActionTest]

@RunWith(classOf[AllTests])
class HoconEnterActionTest extends HoconEditorActionTest(IdeActions.ACTION_EDITOR_ENTER, "enter")
