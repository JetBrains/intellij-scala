package org.jetbrains.plugins.scala.externalLibraries.bm4

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class BetterMonadicForImplicit0SupportTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "better-monadic-for"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testCaseClause(): Unit =
    checkTextHasNoErrors(
      """
        |case class ImplicitTest(id: String)
        |
        |(1, "foo", ImplicitTest("eggs")) match {
        |  case (_, "foo", implicit0(it: ImplicitTest)) => assert(implicitly[ImplicitTest] eq it)
        |}
      """.stripMargin
    )

  def testForPattern(): Unit =
    checkTextHasNoErrors(
      """
        |case class ImplicitTest(id: String)
        |
        |for {
        |  x <- Option(42)
        |  implicit0(it: ImplicitTest) <- Option(ImplicitTest("eggs"))
        |  _ <- Option("dummy")
        |  _ = "dummy"
        |  _ = assert(implicitly[ImplicitTest] eq it)
        |} yield "ok"
      """.stripMargin
    )
}
