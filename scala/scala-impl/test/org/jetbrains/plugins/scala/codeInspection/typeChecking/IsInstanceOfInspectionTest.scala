package org.jetbrains.plugins.scala.codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_7
))
class IsInstanceOfInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[IsInstanceOfInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("missing.explicit.type.in.isinstanceof.call")

  @Test
  def testIsInstanceOfWithoutExplicitType(): Unit = checkTextHasError(
    s"""
       |def test(x: AnyRef): Boolean = ${START}x.isInstanceOf${END}
       |""".stripMargin
  )

  @Test
  def testIsInstanceOfWithoutExplicitTypeIf(): Unit = checkTextHasError(
    s"""
       |val x = "123"
       |if (${START}x.isInstanceOf${END}) x.toInt else x.toLong""".stripMargin
  )

  @Test
  def testIsInstanceOfWithoutExplicitTypeParens(): Unit = checkTextHasError(
    s"""
       |val bool = false || ((${START}x.isInstanceOf${END}) && true) ^^ false""".stripMargin
  )

  @Test
  def testIsInstanceOfAsValueName(): Unit = checkTextHasNoErrors(
    s"""
       |val isInstanceOf: String = "abc"
       |val list = List(isInstanceOf)
       |""".stripMargin
  )

  @Test
  def testIsInstanceOfAsVariable(): Unit = checkTextHasNoErrors(
    s"""
       |var isInstanceOf: String = "abc"
       |isInstanceOf = "def"
       |println(isInstanceOf.length())
       |""".stripMargin
  )
}
