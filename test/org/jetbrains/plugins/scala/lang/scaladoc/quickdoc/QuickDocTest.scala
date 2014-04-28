package org.jetbrains.plugins.scala
package lang
package scaladoc
package quickdoc

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.junit.Assert
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper

/**
 * User: Dmitry Naydanov
 * Date: 12/17/11
 */

class QuickDocTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private def generateByElement(docElement: PsiElement, assumedText: String) {
    val generatedText = QuickDocTest.quickDocGenerator.generateDoc(docElement, docElement)
    Assert.assertEquals(assumedText, generatedText.substring(generatedText.indexOf("&&") + 2, generatedText.lastIndexOf("&&")))
  }
  
  private def generateSimpleByText(initialText: String, assumedText: String) {
    configureFromFileTextAdapter("dummy.scala", initialText.stripMargin('|').replaceAll("\r", "").trim())
    val element = getFileAdapter.getLastChild

    generateByElement(element, assumedText)
  }

  def testSimpleSyntax() {
    val fileText =
      """
      | /**
      |   * &&__xxx^yyy''zzz''yyy^xxx__&&
      |   */
      | val a = 1
      """
    val testText = "<u>xxx<sup>yyy<i>zzz</i>yyy</sup>xxx</u>"

    generateSimpleByText(fileText, testText)
  }

   def testSimpleTags() {
    val fileText =
      """
      | /**
      |   * &&aa
      |   * @see someting
      |   * @author me
      |   * @example aaa
      |   * @note qwerty&&
      |   */
      | class A {}
      """
    val testText = "aa\n     <dl><dt><b>See Also:</b></dt> <dd>someting</dd>\n    " +
            " </dl><br><br><b>Example:</b><br> aaa\n     <br><br><b>Note:</b><br> qwerty"

    generateSimpleByText(fileText, testText)
  }

  def testTagsWithParamsForMethod() {
    val fileText =
      """
      | /**
      |   * &&aa
      |   * @param i aaa
      |   * @param j bbb
      |   * @param k ccc
      |   * @tparam T qqq
      |   * @tparam E aaa
      |   * bbb&&
      |   */
      | def f[T, E](i: Int, j: Int, k: String) {}
      """
    val testText = "aa\n     <br>\n<DD><DL><DT><b>Parameters:</b><DD><code>i</code> - aaa  " +
            "<DD><code>j</code> - bbb  <DD><code>k</code> - ccc  </DD></DL></DD><DD><DL><DT>" +
            "<b>Type parameters:</b><DD><code>&lt;T&gt;</code> -  qqq  <DD><code>&lt;E&gt;</code> -  aaa  bbb"

    generateSimpleByText(fileText, testText)
  }

  def testThrowsTagForMethod() {
    val fileText =
      """
      | /**
      |   * &&
      |   * @throws Exception aaaaaaaaaaaaaaa&&
      |   */
      | def g() {}
      """
    val testText = "\n     <br>\n<DD><DL><DT><b>Throws:</b><DD><a href=\"psi_element://java.lang.Exce" +
            "ption\"><code>Exception</code></a> - aaaaaaaaaaaaaaa"

    generateSimpleByText(fileText, testText)
  }

  def testTagsWithParamsForClass() {
    val fileText =
      """
      | /**
      |   * &&
      |   * @param o
      |   * @tparam E
      |   * @param f jhdkfhkl
      |   * @tparam K dsuhf&&
      |   */
      | class A[E, K](o: Any, f: AnyVal) {}
      """
    val testText = "\n     <br>\n<DD><DL><DT><b>Parameters:</b><DD><code>o</code> -  <DD><code>f</code> - jhdkfhkl  " +
            "</DD></DL></DD><DD><DL><DT><b>Type parameters:</b><DD><code>&lt;E&gt;" +
            "</code> -  <DD><code>&lt;K&gt;</code> -  dsuhf"

    generateSimpleByText(fileText, testText)
  }

  def testTagsWithParamsForType() {
    val fileText =
      """
      | /** &&
      |   * @tparam A
      |   * @tparam B dgjsdaf
      |   * @tparam C &&
      |   */
      | type myType[A, B, C] = HashMap[A, HashMap[B, C]]
      """
    val testText = "\n     <br>\n<DD><DL><DT><b>Type parameters:</b><DD><code>&lt;A&gt;" +
            "</code> -  <DD><code>&lt;B&gt;</code> -  dgjsdaf  <DD><code>&lt;C&gt;</code> -  "

    generateSimpleByText(fileText, testText)
  }

  /*def testCodeDocGeneration() {
    val fileText =
      """
      | /** &&
      |   * {{{
      |   * class Clazzz {
      |       def f[U](u: U) {}
      |
      |       val a = 1
      |    } }}} &&
      |   */
      |  val a = 1
      """
    val testText = "\n      <pre> <code> \n    class Clazzz {\n       def f[U](u: U) {}\n\n       val a = 1\n    }  " +
            "</code> </pre>  "

    generateSimpleByText(fileText, testText)
  }*/

  def testSyntaxInTags() {
    val fileText =
      """
      | /**
      |   * aa&&
      |   * @note '''__THIS IS SPARTAAA!!!11__'''
      |   * @see ,,111__anything__111,,&&
      |   */
      | def f() {}
      """
    val testText = "\n     <br><br><b>Note:</b><br> <b><u>THIS IS SPARTAAA!!!11</u></b>\n     " +
            "<br><br><dl><dt><b>See Also:</b></dt> <sub>111<u>anything</u>111</sub><dd>"

    generateSimpleByText(fileText, testText)
  }

  def testCodeLinks() {
    val fileText =
      """
      | /** &&
      |   * [[http://yandex.ru     ]]
      |   * [[http://jetbrains.com/idea/scala   Scala Plugin        ]]
      |   * [[http://google.com This is google]]
      |   * [[http://scala-lang.org]]
      |   * ,,__[[http://jetbrains.com]]__,,
      |   * [[java.lang.String]] ^[[java.lang.Integer]]^ &&
      |   */
      | val a = 1
      """
    val testText = "\n     <a href=\"http://yandex.ru     \">http://yandex.ru     </a>\n     " +
            "<a href=\"http://jetbrains.com/idea/scala\">  Scala Plugin</a>\n     " +
            "<a href=\"http://google.com\">This is google</a>" +
            "\n     <a href=\"http://scala-lang.org\">http://scala-lang.org</a>\n     " +
            "<sub><u><a href=\"http://jetbrains.com}</u></sub>\n     " +
            "<a href=\"psi_element://java.lang.String\"><code>String</code></a> " +
            "<sup><a href=\"psi_element://java.lang.Integer\"><code>Integer</code></a></sup> "

    generateSimpleByText(fileText, testText)
  }

  def testMalformedSyntax() {
    val fileText =
      """
      | /** &&
      |   * ^blah-blah
      |   *
      |   * __aaaaaaa,,bbbbbbb
      |   * @see something &&
      |   */
      | val a = 1
      """
    val testText = "\n     <sup>blah-blah\n    </sup>\n     <u>aaaaaaa<sub>bbbbbbb\n     " +
            "</sub></u><dl><dt><b>See Also:</b></dt> <dd>something "

    generateSimpleByText(fileText, testText)
  }

  def testMalformedTags() {
    val fileText =
      """
      | /**
      |   * &&
      |   * @gmm
      |   * @
      |   @see
      |   * @param
      |   * @note aaaaa &&
      |   */
      | val a = 1
      """
    val testText = "\n     <dl><dt><b>See Also:</b></dt>\n     </dl><br><br><b>Note:</b><br> aaaaa "

    generateSimpleByText(fileText, testText)
  }
  
  def testInheritdoc() {
    val fileText =
      """
        |class A {
        | /**
        |  * The function f defined in $THIS returns some integer without no special property. (previously defined in $PARENT)
        |  * @param i An ignored parameter.
        |  * @return The value $RESULT.
        |  */
        | def f(i: Int) = 3
        |}
        |class B extends A {
        | /**
        |  * &&
        |  * @inheritdoc
        |  * Some notes on implementation performance, the function runs in O(1).
        |  * @param i An important parameter&&
        |  */
        | override def f(i: Int) = i + 3
        |}
      """.stripMargin
    val test =
      """
        |    
        |    The function f defined in $THIS returns some integer without no special property. (previously defined in $PARENT)
        |    
        |    Some notes on implementation performance, the function runs in O(1).
        |    <br>
        |<DD><DL><DT><b>Parameters:</b><DD><code>i</code> - An important parameter""".stripMargin.replaceAll("\r", "")

    configureFromFileTextAdapter("dummy.scala", fileText.stripMargin('|').replaceAll("\r", "").trim())
    getFileAdapter.asInstanceOf[ScalaFile].getClasses find {
      case a => a.getName == "B"
    } flatMap  (_.findMethodsByName("f", false).headOption) map {
      case m: ScFunctionWrapper => generateByElement(m.function, test)
    } getOrElse Assert.assertTrue(false)
  }
}


object QuickDocTest {
  val quickDocGenerator = new ScalaDocumentationProvider
}
