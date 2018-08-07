package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
  * Nikolay.Tropin
  * 6/25/13
  */
class UnitMethodProceduralDefinitionInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.ProceduralDefinition]

  protected override val description: String =
    InspectionBundle.message("method.signature.unit.procedural.definition")

  private val hint = InspectionBundle.message("insert.return.type.and.equals")

  def test1(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END() {println()}"
    )

    testQuickFix(
      text = "def foo() {println()}",
      expected = "def foo(): Unit = {println()}",
      hint
    )
  }

  def test2(): Unit = {
    checkTextHasError(
      text =
        s"""def haha() {}
           |def ${START}hoho$END() {}
           |def hihi()"""
    )

    testQuickFix(
      text =
        s"""def haha() {}
           |def ho${CARET_MARKER}ho() {}
           |def hihi()""",
      expected =
        """def haha() {}
          |def hoho(): Unit = {}
          |def hihi()""",
      hint
    )
  }

  def test3(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END(x: Int) {}"
    )

    testQuickFix(
      text = "def foo(x: Int) {}",
      expected = "def foo(x: Int): Unit = {}",
      hint
    )
  }

  def test4(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END {}"
    )

    testQuickFix(
      text = "def foo {}",
      expected = "def foo: Unit = {}",
      hint
    )
  }

  def test5(): Unit = checkTextHasNoErrors(
    text =
      """class A(val x: Int, val y: Int) {
        |    def this(x: Int) {
        |      this(x, 0)
        |    }
        |  }""".stripMargin
  )
}
