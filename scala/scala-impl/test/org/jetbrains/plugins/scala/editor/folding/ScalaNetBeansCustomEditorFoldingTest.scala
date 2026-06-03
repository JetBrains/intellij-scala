package org.jetbrains.plugins.scala.editor.folding

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion

/**
 * @see [[com.intellij.lang.customFolding.NetBeansCustomFoldingProvider]]
 */
abstract class ScalaNetBeansCustomEditorFoldingTest extends ScalaCustomEditorFoldingTestBase {
  override protected def customFoldingStart(description: String): String = s"""<editor-fold desc="$description">"""
  override protected def customFoldingEnd: String = "</editor-fold>"

  override def testCustomFoldingsAreNotMixed(): Unit = {
    // the second one is not folded because there is another custom folding provider before
    @Language("Scala")
    val text =
    s"""
       |${ST("some region")}//region some region
       |// Some comment in the region
       |//endregion$END
       |
       |//<editor-fold desc="not folded">
       |class A
       |//</editor-fold>
       |
       |${ST("another region")}//region another region
       |// Some comment in the region
       |//endregion$END
       |
       |""".stripMargin

    genericCheckRegions(text, sortFoldings = true)
  }
}

final class ScalaNetBeansCustomEditorFoldingTest_Scala2 extends ScalaNetBeansCustomEditorFoldingTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2
}

final class ScalaNetBeansCustomEditorFoldingTest_Scala3 extends ScalaNetBeansCustomEditorFoldingTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}
