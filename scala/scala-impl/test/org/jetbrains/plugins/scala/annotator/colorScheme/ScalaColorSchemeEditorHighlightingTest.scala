package org.jetbrains.plugins.scala.annotator.colorScheme

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture.ExpectedHighlight
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.RevertableChange.withModifiedSetting
import org.junit.Test

class ScalaColorSchemeEditorHighlightingTest extends ScalaLightCodeInsightFixtureTestCase {

  private lazy val editorHighlightingFixture = new ScalaColorSchemeEditorHighlightingFixture(getFixture)

  @Test
  def testImplicitConversionsUseTheirColorSchemeKeyInTheEditor(): Unit = {
    val setting = withModifiedSetting(ScalaProjectSettings.getInstance(getProject))(true)(
      _.isShowImplicitConversions,
      _.setShowImplicitConversions(_)
    )

    setting.run {
      val source =
        """object Conversions {
          |  class Source
          |  class Target {
          |    def member: Int = 1
          |  }
          |
          |  implicit def sourceToTarget(source: Source): Target = new Target
          |
          |  val result = new Source().member
          |}
          |""".stripMargin

      editorHighlightingFixture.assertHighlights(
        source,
        ExpectedHighlight("member", DefaultHighlighter.IMPLICIT_CONVERSIONS, occurrence = 1)
      )
    }
  }
}
