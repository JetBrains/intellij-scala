package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class Scala3DeprecatedPackageObjectInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[Scala3DeprecatedPackageObjectInspection]

  override protected val description = Scala3DeprecatedPackageObjectInspection.message

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testDeprecatedPackageObject(): Unit = {
    val code =
      s"""
         |package object ${START}test$END {
         |  def foo(): Int = 123
         |  class Foo { def bar: String = "" }
         |  type A <: AnyRef
         |  type B = Boolean
         |  val (a, b) = (1, "2")
         |  val Some(x) = Option(1d)
         |}
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """
        |def foo(): Int = 123
        |
        |class Foo { def bar: String = "" }
        |
        |type A <: AnyRef
        |type B = Boolean
        |val (a, b) = (1, "2")
        |val Some(x) = Option(1d)
        |""".stripMargin

    testQuickFix(code, expected, Scala3DeprecatedPackageObjectInspection.fixId)
  }

  def testEmptyPackageObject(): Unit = {
    val code =
      s"""
         |package object ${START}foo$END {}
         |""".stripMargin
    checkTextHasError(code)
    testQuickFix(code, "", Scala3DeprecatedPackageObjectInspection.fixId)
  }

  def testPackageObjectWithInehritance(): Unit = {
    val code =
      s"""
         |trait Bar
         |trait Baz
         |
         |package object ${START}foo$END extends Bar with Baz { def fooBarBaz: Int = 123}
         |""".stripMargin
    checkTextHasError(code)
    checkNotFixable(code, Scala3DeprecatedPackageObjectInspection.fixId)
  }
}
