package org.jetbrains.plugins.hocon
package editor

import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
  * @author ghik
  */
@RunWith(classOf[AllTests])
class HoconEnterActionTest extends HoconEditorActionTest(ACTION_EDITOR_ENTER, "enter")

object HoconEnterActionTest extends TestSuiteCompanion[HoconEnterActionTest]
