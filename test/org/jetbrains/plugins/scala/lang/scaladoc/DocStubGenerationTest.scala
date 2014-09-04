package org.jetbrains.plugins.scala
package lang.scaladoc

/**
 * User: Dmitry Naydanov
 * Date: 12/29/11
 */

class DocStubGenerationTest extends ScalaDocEnterActionTestBase {

  import org.jetbrains.plugins.scala.lang.scaladoc.DocStubGenerationTest._

  private def transformateGeneratedText(text: String): String = {
    text.substring(standartHeader.length(), text.length())
  }

  private def genericTestStub(stub: String, testText: String) {
    checkGeneratedTextFromString(headerAndDocCommentStart, testText, stub, transformateGeneratedText)
  }

  def testSimpleMethodParamStub() {
    genericTestStub("  /**\n   * \n   * @param i\n   * @param j\n   */\n" + " def f(i: Int, j: Int) " + standartFooter,
      " def f(i: Int, j: Int) " + standartFooter)
  }

  def testMethodSpecificStub() {
    genericTestStub("  /**\n   * \n   * @throws java.io.IOException\n   * @return\n   */\n" +
            " @throws(classOf[java.io.IOException])\n def f(): Int = {1}}",
      " @throws(classOf[java.io.IOException])\n def f(): Int = {1}}")
  }

  def testMixedMethodStub() {
    genericTestStub("  /**\n   * \n   * @param a\n   * @param b\n   * @tparam C\n   * @return\n   */\n" +
            " def f[C](a: String, b: String): Int = 1 }", " def f[C](a: String, b: String): Int = 1 }")
  }

  def testClassStub() {
    checkGeneratedTextFromString(standartDocCommentStart, " class A[E, T](i: Int, j: String) {}",
      "/**\n * \n * @param i\n * @param j\n * @tparam E\n * @tparam T\n */\n" + " class A[E, T](i: Int, j: String) {}")
  }

  def testTraitStub() {
    checkGeneratedTextFromString(standartDocCommentStart, " trait F[A, B, C] {}",
      "/**\n * \n * @tparam A\n * @tparam B\n * @tparam C\n */\n" + " trait F[A, B, C] {}")
  }

  def testTypeAliasStub() {
    genericTestStub("  /**\n   * \n   * @tparam A\n   * @tparam B\n   */\n" + " type AA[A, B] = String",
      " type AA[A, B] = String")
  }

  def testInhFromScala() {
    val testText = " class B[T, E, U](a: Int, b: String, c: Any) extends A[T,E](a,b) {} }"
    val stub = "  /**\n   * \n   * @param a ytrewq\n   * @param b 54321\n   * @param c\n   * @tparam T qwerty\n   * " +
            "@tparam E 12345\n   * @tparam U\n   */\n" + testText
    val header = "trait GGJKGH {\n  /**\n * @tparam T qwerty\n * @param a ytrewq\n * @tparam E 12345\n *" +
            " @param b 54321\n  */\n class A[T, E](a: Int, b: String){}\n\n"
    checkGeneratedTextFromString(header  + standartDocCommentStart, testText, stub,
      s => s.substring(header.length()))
  }

  def testOverrideScala() {
    val testText = "override def f[T](i: Int) {} }"
    val stub = "  /**\n    * \n    * @param i 777\n    * @tparam T lkjh\n    */\n" + testText
    val header = "class A {\n /**\n  * @tparam T lkjh\n * @param i 777\n */\ndef f[T](i: Int) {}\n}\n\n class B extends A {\n "
    checkGeneratedTextFromString(header + standartDocCommentStart, testText, stub, s => s.substring(header.length))
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
  val headerAndDocCommentStart = standartHeader + standartDocCommentStart
}