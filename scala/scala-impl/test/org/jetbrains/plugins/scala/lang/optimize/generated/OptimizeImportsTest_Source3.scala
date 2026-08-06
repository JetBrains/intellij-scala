package org.jetbrains.plugins.scala.lang.optimize.generated

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class OptimizeImportsTest_Source3 extends OptimizeImportsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    ScalaVersion.Latest.Scala_2_13 <= version && version <= ScalaVersion.Latest.Scala_2

  override def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = Seq("-Xsource:3")
    )
    defaultProfile.setSettings(newSettings)
  }

  def testGivenWildcardImport_unused(): Unit = {
    doTest(
      """object Source {
        |  implicit val x: Int = 0
        |}
        |
        |object Test {
        |  import Source.{given, *}
        |}
        |""".stripMargin,
      """object Source {
        |  implicit val x: Int = 0
        |}
        |
        |object Test {
        |}
        |""".stripMargin,
      expectedNotificationText = "Removed 2 imports"
    )
  }

  def testGivenWildcardImport_unused_but_wildcard_used(): Unit = {
    doTest(
      """object Source {
        |  implicit val x: Int = 0
        |  def fun(): Unit = ()
        |}
        |
        |object Test {
        |  import Source.{*, given}
        |
        |  fun()
        |}
        |""".stripMargin
    )
  }

  def testGivenWildcardImport_used(): Unit = {
    doTest(
      """object Source {
        |  implicit val x: Int = 0
        |}
        |
        |object Test {
        |  import Source.{*, given}
        |
        |  implicitly[Int]
        |}
        |""".stripMargin
    )
  }

  def testMultiGivenWildcard(): Unit = {
    doTest(
      """object Source {
        |  implicit val x: Int = 0
        |}
        |
        |object Test {
        |  import Source.{given, *, given}
        |
        |  implicitly[Int]
        |}
        |""".stripMargin,
      """object Source {
        |  implicit val x: Int = 0
        |}
        |
        |object Test {
        |  import Source.{*, given}
        |
        |  implicitly[Int]
        |}
        |""".stripMargin,
      expectedNotificationText = "Removed 1 import"
    )
  }
}
