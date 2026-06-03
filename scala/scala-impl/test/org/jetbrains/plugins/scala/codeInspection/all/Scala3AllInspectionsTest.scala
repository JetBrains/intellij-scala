package org.jetbrains.plugins.scala.codeInspection.all

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3AllInspectionsTest extends AllInspectionsTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3

  // SCL-21867
  def test_nameId_throws_no_npe_in_any_inspection(): Unit = checkHighlightingThrowsNoExceptions(
    """
      |object A {
      |  given (using)
      |}
      |""".stripMargin
  )
}
