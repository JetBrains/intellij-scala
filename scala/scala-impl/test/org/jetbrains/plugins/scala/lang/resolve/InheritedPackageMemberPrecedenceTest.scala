package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.ScalaVersion

class InheritedPackageMemberPrecedenceTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testExplicitImport(): Unit = checkImportedMember("import other.Choice")

  def testWildcardImport(): Unit = checkImportedMember("import other._")

  private def checkImportedMember(importText: String): Unit = doResolveTest(
    (
      s"""package other
         |
         |object ${REFTGT}Choice""".stripMargin,
      "other/Choice.scala"
    ),
    (
      """package support
        |
        |trait Aliases {
        |  val Choice = other.Choice
        |}
        |""".stripMargin,
      "support/Aliases.scala"
    ),
    (
      """package object demo
        |  extends support.Aliases
        |""".stripMargin,
      "demo/package.scala"
    ),
    (
      s"""package demo
         |$importText
         |class Page {
         |  def selected = ${REFSRC}Choice
         |}
         |""".stripMargin,
      "Page.scala"
    )
  )

  def testInheritedMemberWithoutImport(): Unit = doResolveTest(
    (
      """package other
        |
        |object Choice
        |""".stripMargin,
      "other/Choice.scala"
    ),
    (
      s"""package support
         |
         |trait Aliases {
         |  val ${REFTGT}Choice = other.Choice
         |}
         |""".stripMargin,
      "support/Aliases.scala"
    ),
    (
      "package object demo extends support.Aliases",
      "demo/package.scala"
    ),
    (
      s"""package demo
         |
         |class Page {
         |  def selected = ${REFSRC}Choice
         |}""".stripMargin,
      "Page.scala"
    )
  )

  def testOrdinaryInheritedMemberKeepsPrecedence(): Unit = doResolveTest(
    (
      """package other
        |
        |object Choice
        |""".stripMargin,
      "other/Choice.scala"
    ),
    (
      s"""package support
         |
         |trait Aliases {
         |  val ${REFTGT}Choice = other.Choice
         |}
         |""".stripMargin,
      "support/Aliases.scala"
    ),
    (
      s"""package demo
         |
         |import other.Choice
         |
         |class Page extends support.Aliases {
         |  def selected = ${REFSRC}Choice
         |}
         |""".stripMargin,
      "Page.scala"
    )
  )
}

class InheritedPackageMemberPrecedenceTest_Scala3 extends InheritedPackageMemberPrecedenceTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_9
}
