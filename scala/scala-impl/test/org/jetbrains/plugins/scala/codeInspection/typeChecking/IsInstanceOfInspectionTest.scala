package org.jetbrains.plugins.scala.codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1
))
class IsInstanceOfInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[IsInstanceOfInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("missing.explicit.type.in.isinstanceof.call")

  def testIsInstanceOfWithoutExplicitType(): Unit = checkTextHasError(
    s"""
       |def test(x: AnyRef): Boolean = ${START}x.isInstanceOf${END}
       |""".stripMargin
  )

  def testIsInstanceOfWithoutExplicitTypeParens(): Unit = checkTextHasError(
    s"""
       |val x = "123"
       |if (${START}x.isInstanceOf${END}) x.toInt else x.toLong""".stripMargin
  )
}
