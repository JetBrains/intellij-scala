package org.jetbrains.plugins.scala.editor.folding

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion

/**
 * @see [[com.intellij.lang.customFolding.VisualStudioCustomFoldingProvider]]
 */
abstract class ScalaVisualStudioCustomEditorFoldingTest extends ScalaCustomEditorFoldingTestBase {
  override protected def customFoldingStart(description: String): String = s"region $description"
  override protected def customFoldingEnd: String = "endregion"

  override def testCustomFoldingsAreNotMixed(): Unit = {
    // the second one is not folded because there is another custom folding provider before
    @Language("Scala")
    val text =
    s"""
       |${ST("some region")}//<editor-fold desc="some region">
       |// Some comment in the region
       |//</editor-fold>$END
       |
       |// region not folded
       |class A
       |// endregion
       |
       |${ST("another region")}//<editor-fold desc="another region">
       |// Some comment in the region
       |//</editor-fold>$END
       |
       |""".stripMargin

    genericCheckRegions(text, sortFoldings = true)
  }
}

final class ScalaVisualStudioCustomEditorFoldingTest_Scala2 extends ScalaVisualStudioCustomEditorFoldingTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2
}

final class ScalaVisualStudioCustomEditorFoldingTest_Scala3 extends ScalaVisualStudioCustomEditorFoldingTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}
