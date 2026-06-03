package org.jetbrains.plugins.scala.annotator.createFromUsage

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

abstract class CreateClassQuickFixTestBase extends ScalaAnnotatorQuickFixTestBase {
  protected def className: String

  protected lazy val hint = ScalaBundle.message("create.class.named", className)
  override protected lazy val description = ScalaBundle.message("cannot.resolve", className)

  protected def doTest(classUsageText: String, classDefinitionText: String): Unit = {
    val before =
      s"""object Usage {
         |  val f = $CARET$classUsageText
         |}""".stripMargin

    val after =
      s"""object Usage {
         |  val f = $classUsageText
         |}
         |
         |$classDefinitionText""".stripMargin

    testQuickFix(before, after, hint)
  }

  protected def doTestNotFixable(classUsageText: String): Unit = {
    val text =
      s"""object Usage {
         |  val f = $CARET$classUsageText
         |}""".stripMargin

    checkNotFixable(text, hint)
  }
}

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
final class CreateClassQuickFixTest_LowerCased extends CreateClassQuickFixTestBase {
  override val className = "foo"

  @Test
  def testNotFixableWhenLowerCased(): Unit =
    doTestNotFixable("foo()")
}

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
final class CreateClassQuickFixTest_Scala2 extends CreateClassQuickFixTestBase {
  override val className = "Foo"

  @Test
  def testNotFixableInScala2(): Unit =
    doTestNotFixable("Foo()")
}

final class CreateClassQuickFixTest_Scala3 extends CreateClassQuickFixTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override val className = "Foo"

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
