package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.completion3.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import java.lang.String
import com.intellij.openapi.actionSystem.{DataContext, IdeActions}

/**
 * User: Dmitry Naydanov
 * Date: 12/29/11
 */

class DocStubGenerationTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  import DocStubGenerationTest._

  private def checkStubFromText(header: String,  footer: String,  assumedStub: String) {
    configureFromFileTextAdapter("dummy.scala", header + standartDocCommentStart + footer)
    getEditorAdapter.getCaretModel.moveToOffset(header.length + standartDocCommentStart.length - 1)
    val enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
    
    enterHandler.execute(getEditorAdapter, new DataContext {
      def getData(dataId: String): AnyRef = {
        dataId match {
          case "Language" | "language" => getFileAdapter.getLanguage
          case "Project" | "project" => getFileAdapter.getProject
          case _ => null
        }
      }
    })
    
    assert(getFileAdapter.getText.substring(header.length,
      getFileAdapter.getText.length - footer.length - 1).equals(assumedStub))
  }

  def testSimpleMethodParamStub() {
    val stub = "  /**\n   * \n   * @param i\n   * @param j\n   */"
    checkStubFromText(standartHeader, " def f(i: Int, j: Int) " + standartFooter, stub)
  }

  def testMethodSpecificStub() {
    val stub = "  /**\n   * \n   * @throws java.io.IOException \n   * @return \n     */"
    checkStubFromText(standartHeader, " @throws(classOf[java.io.IOException])\n def f(): Int = {1}}", stub)
  }

  def testMixedMethodStub() {
    val stub = "  /**\n   * \n   * @param a\n   * @param b\n   * @tparam C\n   * @return\n   */"
    checkStubFromText(standartHeader, " def f[C](a: String, b: String): Int = 1 }", stub)
  }

  def testClassStub() {
    val stub = "/**\n * \n * @param i\n * @param j\n * @tparam E\n * @tparam T\n */"
    checkStubFromText("", " class A[E, T](i: Int, j: String) {}", stub)
  }

  def testTraitStub() {
    val stub = "/**\n * \n * @tparam A\n * @tparam B\n * @tparam C\n */"
    checkStubFromText("", " trait F[A, B, C] {}", stub)
  }

  def testTypeAliasStub() {
    val stub = "  /**\n   * \n   * @tparam A\n   * @tparam B\n   */"
    checkStubFromText(standartHeader, " type AA[A, B] = String", stub)
  }

  def testInhFromScala() {
    val stub = "  /**\n   * \n   * @param a ytrewq\n   * @param b 54321\n   * @param c\n   " +
            "* @tparam T qwerty\n   * @tparam E 12345\n   * @tparam U\n   */"
    checkStubFromText("trait GGJKGH {\n  /**\n * @tparam T qwerty\n * @param a ytrewq\n * @tparam E 12345\n * @param b 54321\n " +
            " */\n class A[T, E](a: Int, b: String){}\n\n",
      " class B[T, E, U](a: Int, b: String, c: Any) extends A[T,E](a,b) {} }", stub)
  }

  def testOverrideScala() {
    val stub = "  /**\n    * \n    * @param i 777\n    * @tparam T lkjh\n    */"
    checkStubFromText("class A {\n /**\n  * @tparam T lkjh\n * @param i 777\n */\ndef f[T](i: Int) {}\n}\n\n class B extends A {\n ",
      "override def f[T](i: Int) {} }", stub)
  }

//  def testOverrideJava() {
//    val stub = "   /**\n    * \n    * @return a string representation of this collection\n    */"
//    checkStubFromText(" abstract class A[E] extends java.util.AbstractCollection[E] {\n",
//      " override def toString: Boolean = { null }\n} ", stub)
//  }
}


object DocStubGenerationTest {
  val standartHeader = "class A {\n"
  val standartFooter = " {} }"
  val standartDocCommentStart = "    /**\n"
}