package org.jetbrains.plugins.scala.annotator.createFromUsage

;

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
    val definitionBefore = s"object Bar {\n}"
    val definitionAfter  = s"object Bar {\n  $methodDefinitionText\n}"
    val usage            = s"object Usage {\n  Bar.$Caret$methodUsageText\n}"

    val before = s"$definitionBefore\n$usage"
    val after  = s"$definitionAfter\n${removeCarets(usage)}"

    testQuickFix(before, after, hint)
  }

  private def doTestInClass(methodUsageText: String, methodDefinitionText: String): Unit = {
    val definitionBefore = s"class Bar {\n}"
    val definitionAfter  = s"class Bar {\n  $methodDefinitionText\n}"
    val usage            = s"object Usage {\n  new Bar().$Caret$methodUsageText\n}"

    val before = s"$definitionBefore\n$usage"
    val after  = s"$definitionAfter\n${removeCarets(usage)}"

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