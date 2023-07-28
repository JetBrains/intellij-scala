package org.jetbrains.plugins.scala
package annotator

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File

abstract class LiteralTypesHighlightingTestBase extends ScalaHighlightingTestBase {
  def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/"

  def doTest(expectedErrors: List[Message] = Nil, fileText: Option[String] = None, settingOn: Boolean = false): Unit = {
    val text = fileText.getOrElse {
      val filePath = folderPath + getTestName(true) + ".scala"
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }

    if (settingOn) {
      import org.jetbrains.plugins.scala.project._
      val profile = myFixture.getModule.scalaCompilerSettingsProfile
      val newSettings = profile.getSettings.copy(
        additionalCompilerOptions = Seq("-Yliteral-types")
      )
      profile.setSettings(newSettings)
    }

    val errors = errorsFromScalaCode(text)
    assertMessages(errors)(expectedErrors: _*)
  }
}
