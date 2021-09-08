package org.jetbrains.plugins.scala.lang.imports

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase._

class GivenImportsTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0
  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[GivenImportsTest])

  private def replaceImportExprWithSelector(text: String): String =
    text.replaceAll(raw"(import .+)\.([^{.\n]+)", "$1.{$2}")

  private def alsoInSelector(test: String => Unit)(text: String): Unit = {
    test(text)
    test(replaceImportExprWithSelector(text))
  }

  def test_wildcard_does_not_import_givens(): Unit = alsoInSelector(testNoResolve(_))(
    s"""
      |object Source {
      |  given Int = 0
      |}
      |
      |object Target {
      |  import Source.*
      |  given${REFSRC}_Int
      |}
      |""".stripMargin
  )

  def test_wildcard_does_import_implicits(): Unit = alsoInSelector(doResolveTest(_))(
    s"""
       |object Source {
       |  implicit val ${REFTGT}given_Int: Int = 0
       |}
       |
       |object Target {
       |  import Source.*
       |  ${REFSRC}given_Int
       |}
       |""".stripMargin
  )
}
