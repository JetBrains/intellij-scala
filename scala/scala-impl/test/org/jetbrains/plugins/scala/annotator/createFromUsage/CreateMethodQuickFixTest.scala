package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12, TestScalaVersion.Scala_2_13))
class CreateMethodQuickFixTest extends ScalaAnnotatorQuickFixTestBase {
  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End   = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  private val methodName = "foo"
  private val hint       = s"Create method '$methodName'"

  override protected val description = s"Cannot resolve symbol $methodName"

  private def doTest(before: String, after: String): Unit =
    testQuickFix(before, after, hint)

  private def doCompoundTest(methodUsageText: String, methodDefinitionText: String): Unit = {
    doTestInObject(methodUsageText, methodDefinitionText)
    doTestInClass(methodUsageText, methodDefinitionText)
  }

  private def doTestInObject(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""object Bar {
         |}
         |object Usage {
         |  Bar.$Caret$methodUsageText
         |}""".stripMargin
    val after  =
      s"""object Bar {
         |  $methodDefinitionText
         |}
         |object Usage {
         |  Bar.$methodUsageText
         |}""".stripMargin
    doTest(before, after)
  }

  private def doTestInClass(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""class Bar {
         |}
         |object Usage {
         |  new Bar().$Caret$methodUsageText
         |}""".stripMargin
    val after  =
      s"""class Bar {
         |  $methodDefinitionText
         |}
         |object Usage {
         |  new Bar().$methodUsageText
         |}""".stripMargin

    doTest(before, after)
  }

  private def doTestInTopLevel(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""$methodUsageText""".stripMargin
    val after  =
      s"""$methodDefinitionText
         |
         |$methodUsageText""".stripMargin

    testQuickFix(before, after, hint)
  }

  def testCreateMethod(): Unit = {
    val usage      = """foo(42, "text", Some(true))"""
    val definition = """def foo(i: Int, str: String, someBoolean: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_All(): Unit = {
    val usage      = """foo(name1 = 42, name2 = "text", name3 = Some(true))"""
    val definition = """def foo(name1: Int, name2: String, name3: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheBeginning(): Unit = {
    val usage      = """foo(name1 = 42, name2 = "text", Some(true))"""
    val definition = """def foo(name1: Int, name2: String, someBoolean: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheEnd(): Unit = {
    val usage      = """foo(42, name2 = "text", name3 = Some(true))"""
    val definition = """def foo(i: Int, name2: String, name3: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheMiddle(): Unit = {
    val usage      = """foo(42, name2 = "text", Some(true))"""
    val definition = """def foo(i: Int, name2: String, someBoolean: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithParenthesis(): Unit = {
    val usage      = """foo((42))"""
    val definition = """def foo(i: Int) = ???"""
    doCompoundTest(usage, definition)
  }

  private val TopLevelUsage  = """foo(42)"""
  private val TopLevelDefinition  = """def foo(i: Int) = ???"""

  def testTopLevelFirstElementInFile(): Unit = {
    doTest(
      s"""$TopLevelUsage""".stripMargin,
      s"""$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }

  def testTopLevelFirstElementInFile_1(): Unit = {
    doTest(
      s"""
         |$TopLevelUsage""".stripMargin,
      s"""
         |$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }

  def testTopLevelInTheMiddle(): Unit = {
    doTest(
      s"""val x = 42
         |$TopLevelUsage""".stripMargin,
      s"""val x = 42
         |
         |$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }

  def testTopLevelInTheMiddle_1(): Unit = {
    doTest(
      s"""val x = 42
         |
         |$TopLevelUsage""".stripMargin,
      s"""val x = 42
         |
         |$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }
}