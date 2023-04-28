package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}

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
