package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * @see [[org.jetbrains.plugins.scala.annotator.element.ScLiteralTypeElementAnnotatorTestBase]]
 */
abstract class LiteralTypesHighlightingTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike {

  def folderPath: Path = Path.of(TestUtils.getTestDataPath, "annotator", "literalTypes")

  def doTest(expectedErrors: List[Message] = Nil, fileText: Option[String] = None, settingOn: Boolean = false): Unit = {
    val text = fileText.getOrElse {
      val filePath: Path = folderPath / s"${getTestName(true)}.scala"
      filePath.readAllBytesToString(StandardCharsets.UTF_8)
    }

    if (settingOn) {
      val profile = ScalaCompilerSettingsProfile.forModule(myFixture.getModule)
      val newSettings = profile.getSettings.copy(
        additionalCompilerOptions = Seq("-Yliteral-types")
      )
      profile.setSettings(newSettings)
    }

    val errors = errorsFromScalaCode(text)
    assertMessages(errors)(expectedErrors: _*)
  }
}
