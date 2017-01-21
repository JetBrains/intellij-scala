package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}

/**
 * Nikolay.Tropin
 * 6/25/13
 */
class UnitMethodDefinedLikeProcedureInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodDefinedLikeProcedureInspection]

  protected override val description: String =
    InspectionBundle.message("unit.method.like.procedure.name")

  private val hint = InspectionBundle.message("insert.return.type.and.equals")

  def test1(): Unit = {
    val selected = s"def ${START}foo$END() {println()}"
    checkTextHasError(selected)
    val text = "def foo() {println()}"
    val result = "def foo(): Unit = {println()}"
    testQuickFix(text, result, hint)
  }

  def test2(): Unit = {
    val selected = s"""def haha() {}
                     |def ${START}hoho$END() {}
                     |def hihi()"""
    checkTextHasError(selected)
    val text = s"""def haha() {}
                 |def ho${CARET_MARKER}ho() {}
                 |def hihi()"""
    val result = """def haha() {}
                   |def hoho(): Unit = {}
                   |def hihi()"""
    testQuickFix(text, result, hint)
  }

  def test3(): Unit = {
    val selected = s"def ${START}foo$END(x: Int) {}"
    checkTextHasError(selected)
    val text = "def foo(x: Int) {}"
    val result = "def foo(x: Int): Unit = {}"
    testQuickFix(text, result, hint)
  }

  def test4(): Unit = {
    val selected = s"def ${START}foo$END {}"
    checkTextHasError(selected)
    val text = "def foo {}"
    val result = "def foo: Unit = {}"
    testQuickFix(text, result, hint)
  }

  def test5(): Unit = {
    val text = """class A(val x: Int, val y: Int) {
                 |    def this(x: Int) {
                 |      this(x, 0)
                 |    }
                 |  }""".stripMargin.replace("\r", "")
    checkTextHasNoErrors(text)
  }
}
