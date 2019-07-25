package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

class CreateMethodQuickFixTest extends ScalaAnnotatorQuickFixTestBase {
  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End   = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  private val methodName = "foo"
  private val hint       = s"Create method '$methodName'"

  override protected val description = s"Cannot resolve symbol $methodName"

  private def doTest(methodUsageText: String, methodDefinitionText: String): Unit = {
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
    testQuickFix(before, after, hint)
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

    testQuickFix(before, after, hint)
  }

  def testCreateMethod(): Unit = {
    val usage      = """foo(42, "text", Some(true))"""
    val definition = """def foo(i: Int, str: String, someBoolean: Some[Boolean]) = ???"""
    doTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_All(): Unit = {
    val usage      = """foo(name1 = 42, name2 = "text", name3 = Some(true))"""
    val definition = """def foo(name1: Int, name2: String, name3: Some[Boolean]) = ???"""
    doTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheBeginning(): Unit = {
    val usage      = """foo(name1 = 42, name2 = "text", Some(true))"""
    val definition = """def foo(name1: Int, name2: String, someBoolean: Some[Boolean]) = ???"""
    doTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheEnd(): Unit = {
    val usage      = """foo(42, name2 = "text", name3 = Some(true))"""
    val definition = """def foo(i: Int, name2: String, name3: Some[Boolean]) = ???"""
    doTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheMiddle(): Unit = {
    val usage      = """foo(42, name2 = "text", Some(true))"""
    val definition = """def foo(i: Int, name2: String, someBoolean: Some[Boolean]) = ???"""
    doTest(usage, definition)
  }
}