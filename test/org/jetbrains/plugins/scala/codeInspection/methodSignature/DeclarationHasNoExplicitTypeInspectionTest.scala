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
class DeclarationHasNoExplicitTypeInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[DeclarationHasNoExplicitTypeInspection]

  protected override val description: String = InspectionBundle.message("declaration.has.no.explicit.type.name")

  private val hint = InspectionBundle.message("add.unit.type.to.declaration")

  private def testFix(text: String, result: String): Unit = testQuickFix(text, result, hint)

  def test1(): Unit = {
    val selected = s"def ${START}foo$END()"
    checkTextHasError(selected)
    val text = "def foo()"
    val result = "def foo(): Unit"
    testFix(text, result)
  }

  def test2(): Unit = {
    val selected = s"""def haha()
                     |def ${START}hoho$END()
                     |def hihi()"""
    checkTextHasError(selected)
    val text = s"""def haha()
                 |def ho${CARET_MARKER}ho()
                 |def hihi()"""
    val result = """def haha()
                   |def hoho(): Unit
                   |def hihi()"""
    testFix(text, result)
  }

  def test3(): Unit = {
    val selected = s"def ${START}foo$END(x: Int)"
    checkTextHasError(selected)
    val text = "def foo(x: Int)"
    val result = "def foo(x: Int): Unit"
    testFix(text, result)
  }

  def test4(): Unit = {
    val selected = s"def ${START}foo$END"
    checkTextHasError(selected)
    val text = "def foo"
    val result = "def foo: Unit"
    testFix(text, result)
  }
}
