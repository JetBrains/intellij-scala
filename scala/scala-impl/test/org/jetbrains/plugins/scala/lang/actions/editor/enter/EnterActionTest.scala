package org.jetbrains.plugins.scala.lang.actions.editor.enter

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language

import java.nio.file.Path

class EnterActionTest extends AbstractEnterActionTestBase {
  override protected def relativeTestDataPath: Path = Path.of("actions", "editor", "enter", "data")

  override protected def setUp(): Unit = {
    super.setUp()
    scalaCodeStyleSettings.USE_SCALADOC2_FORMATTING = true
    CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = false // No, we don't need it.
  }

  override protected def tearDown(): Unit = {
    CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = true
    super.tearDown()
  }
}

// Added Scala 3 tests to ensure that all Scala 2 test cases don't fail in Scala 3 as well
class EnterActionTest_Scala3 extends EnterActionTest {
  override protected def language: Language = Scala3Language.INSTANCE
}
