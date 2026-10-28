package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class RootQualifiedImplicitImportTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def setUp(): Unit = {
    super.setUp()
    myFixture.addFileToProject("support/Extensions.scala",
      """package support
        |object Extensions {
        |  implicit class RichOption[A](value: Option[A]) {
        |    def or(default: A): A = value.getOrElse(default)
        |  }
        |}
        |""".stripMargin)
  }

  def testRootQualifiedImport(): Unit = check("_root_.support.Extensions.RichOption")

  def testUnqualifiedImport(): Unit = check("support.Extensions.RichOption")

  def testRootQualifiedWildcardImport(): Unit = check("_root_.support.Extensions._")

  def testRootQualifiedSelectorImport(): Unit = check("_root_.support.Extensions.{RichOption}")

  private def check(imported: String): Unit = checkTextHasNoErrors(
    s"""import $imported
       |object Usage {
       |  val result: String = Option.empty[String].or("fallback")
       |  val wrapper: RichOption[String] = new RichOption(Option("value"))
       |}
       |""".stripMargin
  )
}

class RootQualifiedImplicitImportTest_Scala3 extends RootQualifiedImplicitImportTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}
