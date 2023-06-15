package org.jetbrains.plugins.scala.annotator.createFromUsage

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

abstract class CreateAnnotationClassQuickFixTestBase(protected val className: String) extends ScalaAnnotatorQuickFixTestBase {
  protected lazy val hint = ScalaBundle.message("create.annotation.class.named", className)
  override protected lazy val description: String = ScalaBundle.message("cannot.resolve", className)

  protected def doTest(classUsageText: String, classDefinitionText: String): Unit = {
    val before = s"@$CARET$classUsageText object Usage"
    val after =
      s"""import scala.annotation.StaticAnnotation
         |
         |@$classUsageText object Usage
         |
         |$classDefinitionText extends StaticAnnotation""".stripMargin

    testQuickFix(before, after, hint)
  }

  def testCreateClass(): Unit = {
    val usage = s"$className()"
    val definition = s"class $className()"

    doTest(usage, definition)
  }

  def testCreateClass_WithArguments(): Unit = {
    val usage = s"""$className(42, "bar")"""
    val definition = s"class $className(i: Int, str: String)"

    doTest(usage, definition)
  }

  def testCreateClass_WithNamedArguments_All(): Unit = {
    val usage = s"""$className(name1 = 42, name2 = "text", name3 = Some(true))"""
    val definition = s"class $className(name1: Int, name2: String, name3: Some[Boolean])"

    doTest(usage, definition)
  }

  def testCreateClass_WithNamedArguments_SomeInTheBeginning(): Unit = {
    val usage = s"""$className(name1 = 42, name2 = "text", Some(true))"""
    val definition = s"class $className(name1: Int, name2: String, someBoolean: Some[Boolean])"

    doTest(usage, definition)
  }

  def testCreateClass_WithNamedArguments_SomeInTheEnd(): Unit = {
    val usage = s"""$className(42, name2 = "text", name3 = Some(true))"""
    val definition = s"class $className(i: Int, name2: String, name3: Some[Boolean])"

    doTest(usage, definition)
  }

  def testCreateClass_WithNamedArguments_SomeInTheMiddle(): Unit = {
    val usage = s"""$className(42, name2 = "text", Some(true))"""
    val definition = s"class $className(i: Int, name2: String, someBoolean: Some[Boolean])"

    doTest(usage, definition)
  }

  def testCreateClass_WithParenthesis(): Unit = {
    val usage = s"""$className((42))"""
    val definition = s"class $className(i: Int)"

    doTest(usage, definition)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
final class CreateAnnotationClassQuickFixTest_LowerCased
  extends CreateAnnotationClassQuickFixTestBase("foo")

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
final class CreateAnnotationClassQuickFixTest
  extends CreateAnnotationClassQuickFixTestBase("Foo")
