package org.jetbrains.plugins.scala.annotator.element

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScGivenAliasDeclarationAnnotatorTest extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testAnonymousGivenDeclaration(): Unit =
    assertErrors(
      """trait Foo:
        |  given String
        |end Foo
        |""".stripMargin,
      Error("given String", ScalaBundle.message("given.alias.declaration.must.be.named"))
    )

  def testNamedGivenDeclaration(): Unit =
    assertNoErrors(
      """trait Foo:
        |  given s: String
        |end Foo
        |""".stripMargin
    )
}
