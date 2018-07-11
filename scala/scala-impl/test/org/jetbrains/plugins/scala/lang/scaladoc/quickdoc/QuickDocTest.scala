package org.jetbrains.plugins.scala
package lang
package scaladoc
package quickdoc

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.junit.Assert

/**
 * User: Dmitry Naydanov
 * Date: 12/17/11
 */

class QuickDocTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private def generateByElement(docElement: PsiElement, assumedText: String) {
    val generatedText = QuickDocTest.quickDocGenerator.generateDoc(docElement, docElement)
    Assert.assertEquals(assumedText, generatedText.substring(generatedText.indexOf("_MARKER") + 7, generatedText.lastIndexOf("_MARKER")))
  }
  
  private def generateSimpleByText(initialText: String, assumedText: String) {
    configureFromFileTextAdapter("dummy.scala", initialText.stripMargin('|').replaceAll("\r", "").trim())
    val element = getFileAdapter.getLastChild

    generateByElement(element, assumedText.replaceAll("\r", ""))
  }

  private def generateNested(fileText: String, className: String, elementName: String, assumedTest: String) {
    configureFromFileTextAdapter("dummy.scala", fileText.stripMargin('|').replaceAll("\r", "").trim())
    val td = getFileAdapter.asInstanceOf[ScalaFile].getClasses collectFirst {
      case a: ScTemplateDefinition if a.name == className => a
    }
    val member = td flatMap (c => c.members.find(_.getName == elementName))
    if (member.isEmpty) Assert.fail()
    else member foreach {
      case m: ScFunctionWrapper => generateByElement(m.delegate, assumedTest)
      case member: ScMember => generateByElement(member, assumedTest)
    }
  }

  def testSimpleSyntax() {
    val fileText =
      """
      | /**
      |   * _MARKER__xxx^yyy''zzz''yyy^xxx___MARKER
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
      |   * _MARKERaa
      |   * @see someting
      |   * @author me
      |   * @example aaa
      |   * @note qwerty_MARKER
      |   */
      | class A {}
      """
    val testText = 
      """aa
        |     <br/><br/><b>See also:</b><br/> <dd>someting</dd>
        |     <br/><br/><b>Example:</b><br/> aaa
        |     <br/><br/><b>Note:</b><br/> qwerty""".stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testTagsWithParamsForMethod() {
    val fileText =
      """
      | /**
      |   * _MARKERaa
      |   * @param i aaa
      |   * @param j bbb
      |   * @param k ccc
      |   * @tparam T qqq
      |   * @tparam E aaa
      |   * bbb_MARKER
      |   */
      | def f[T, E](i: Int, j: Int, k: String) {}
      """
    val testText = 
      """aa
        |     <br/>
        |<p></div><table class='sections'><p><tr><td valign='top' class='section'><p>Params:</td><td valign='top'>i &ndash; aaa  <p>j &ndash; bbb  <p>k &ndash; ccc  </td><tr><td valign='top' class='section'><p>Type parameters:</td><td valign='top'>&lt;T&gt; &ndash;  qqq  <p>&lt;E&gt; &ndash;  aaa  bbb""".stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testThrowsTagForMethod() {
    val fileText =
      """
      | /**
      |   * _MARKER
      |   * @throws Exception aaaaaaaaaaaaaaa_MARKER
      |   */
      | def g() {}
      """
    val testText = 
      """
        |     <br/>
        |<p></div><table class='sections'><p><tr><td valign='top' class='section'><p>Throws:</td><td valign='top'><p><a href="psi_element://java.lang.Exception"><code>Exception</code></a> &ndash; aaaaaaaaaaaaaaa""".stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testTagsWithParamsForClass() {
    val fileText =
      """
      | /**
      |   * _MARKER
      |   * @param o
      |   * @tparam E
      |   * @param f jhdkfhkl
      |   * @tparam K dsuhf_MARKER
      |   */
      | class A[E, K](o: Any, f: AnyVal) {}
      """
    val testText = 
      """
        |     <br/>
        |<p></div><table class='sections'><p><tr><td valign='top' class='section'><p>Params:</td><td valign='top'>o &ndash;  <p>f &ndash; jhdkfhkl  </td><tr><td valign='top' class='section'><p>Type parameters:</td><td valign='top'>&lt;E&gt; &ndash;  <p>&lt;K&gt; &ndash;  dsuhf""".stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testTagsWithParamsForType() {
    val fileText =
      """
      | /** _MARKER
      |   * @tparam A
      |   * @tparam B dgjsdaf
      |   * @tparam C _MARKER
      |   */
      | type myType[A, B, C] = HashMap[A, HashMap[B, C]]
      """
    val testText = 
      """
        |     <br/>
        |</div><table class='sections'><p><tr><td valign='top' class='section'><p>Type parameters:</td><td valign='top'>&lt;A&gt; &ndash;  <p>&lt;B&gt; &ndash;  dgjsdaf  <p>&lt;C&gt; &ndash;  """.stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testSyntaxInTags() {
    val fileText =
      """
      | /**
      |   * aa_MARKER
      |   * @note '''__THIS IS SPARTAAA!!!11__'''
      |   * @see ,,111__anything__111,,_MARKER
      |   */
      | def f() {}
      """
    val testText = 
      """
        |     <br/><br/><b>Note:</b><br/> <b><u>THIS IS SPARTAAA!!!11</u></b>
        |     <br/><br/><b>See also:</b><br/> <sub>111<u>anything</u>111</sub><dd>""".stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testCodeLinks() {
    val fileText =
      """
      | /** _MARKER
      |   * [[http://yandex.ru     ]]
      |   * [[http://jetbrains.com/idea/scala   Scala Plugin        ]]
      |   * [[http://google.com This is google]]
      |   * [[http://scala-lang.org]]
      |   * ,,__[[http://jetbrains.com]]__,,
      |   * [[java.lang.String]] ^[[java.lang.Integer]]^ _MARKER
      |   */
      | val a = 1
      """
    val testText = "\n     <a href=\"http://yandex.ru     \">http://yandex.ru     </a>\n     " +
            "<a href=\"http://jetbrains.com/idea/scala\">  Scala Plugin</a>\n     " +
            "<a href=\"http://google.com\">This is google</a>" +
            "\n     <a href=\"http://scala-lang.org\">http://scala-lang.org</a>\n     " +
            "<sub><u><a href=\"http://jetbrains.com\">http://jetbrains.com</a></u></sub>\n     " +
            "<a href=\"psi_element://java.lang.String\"><code>String</code></a> " +
            "<sup><a href=\"psi_element://java.lang.Integer\"><code>Integer</code></a></sup> "

    generateSimpleByText(fileText, testText)
  }

  def testMalformedSyntax() {
    val fileText =
      """
      | /** _MARKER
      |   * ^blah-blah
      |   *
      |   * __aaaaaaa,,bbbbbbb
      |   * @see something _MARKER
      |   */
      | val a = 1
      """
    val testText = 
      """
        |     <sup>blah-blah
        |    </sup>
        |     <u>aaaaaaa<sub>bbbbbbb
        |     </sub></u><br/><br/><b>See also:</b><br/> <dd>something """.stripMargin

    generateSimpleByText(fileText, testText)
  }

  def testMalformedTags() {
    val fileText =
      """
      | /**
      |   * _MARKER
      |   * @gmm
      |   * @
      |   @see
      |   * @param
      |   * @note aaaaa _MARKER
      |   */
      | val a = 1
      """
    val testText = 
      """
        |     <br/><br/><b>See also:</b><br/>
        |     <br/><br/><b>Note:</b><br/> aaaaa """.stripMargin

    generateSimpleByText(fileText, testText)
  }
  
  def testInheritdoc() {
    val fileText =
      """
        |/**
        | *
        | * @define THIS A
        | */
        |class A {
        | /**
        |  * The function f defined in $THIS returns some integer without no special property. (previously defined in $PARENT)
        |  * @param i An ignored parameter.
        |  * @return The value $RESULT.
        |  */
        | def f(i: Int) = 3
        |}
        |
        |/**
        | *
        | * @define THIS B
        | * @define PARENT A
        | */
        |class B extends A {
        | /**
        |  * _MARKER
        |  * @inheritdoc
        |  * Some notes on implementation performance, the function runs in O(1).
        |  * @param i An important parameter_MARKER
        |  */
        | override def f(i: Int) = i + 3
        |}
      """.stripMargin
    
    val test =
      """
        |    
        |    The function f defined in B returns some integer without no special property. (previously defined in A)
        |    
        |    Some notes on implementation performance, the function runs in O(1).
        |    <br/>
        |<p></div><table class='sections'><p><tr><td valign='top' class='section'><p>Params:</td><td valign='top'>i &ndash; An important parameter""".stripMargin.replaceAll("\r", "")


    generateNested(fileText, "B", "f", test)
  }

  def testMacroSimple() {
    val fileText =
      """
        |/**
        | * @define THIS A
        | */
        |trait A {
        |
        |  /**
        |   *_MARKER Function defined in $THIS _MARKER
        |   */
        |  def boo()
        |}
      """.stripMargin
    
    val test =
      " Function defined in A "

    generateNested(fileText, "A", "boo", test)
  }

  def testMacroComplicated() {
    val fileText =
      """
        |trait A {
        |  /**
        |   * @define THAT A
        |   */
        |  def boo() = 1
        |}
        |
        |/**
        | * @define THIS B
        | */
        |trait B {
        |}
        |
        |class C extends A with B {
        |
        |  /**
        |   *_MARKER aa $THIS bb _MARKER
        |   */
        |  override def boo() = 2
        |}
        |
      """.stripMargin

    val test = " aa B bb "

    generateNested(fileText, "C", "boo", test)
  }
  
  def testMacroWiki(): Unit = {
    val fileText =
      """
        |/**
        | * @define none `None`
        | */
        | class A {
        |   /**
        |    * _MARKER $none _MARKER
        |    */
        |    def foo() = {}
        | }
      """.stripMargin
    val test = " <tt>None</tt> "
    
    generateNested(fileText, "A", "foo", test)
  }
  
  def testAnnotationArgs() {
    val fileText =
      """
        | @deprecated("use 'foo' instead", "1.2.3")
        | @throws(classOf[Exception])
        | def boo() {} 
      """
    
    val expected =
      """<html><body><div class="definition"><pre>@<a href="psi_element://scala.deprecated"><code>deprecated</code></a>("use 'foo' instead", "1.2.3")
        |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://scala"><code>scala</code></a>.Exception](classOf[Exception])
        |def <b>boo</b>(): Unit</pre></div></body></html>""".stripMargin.replaceAll("\r", "")

    configureFromFileTextAdapter("dummy.scala", fileText.stripMargin('|').replaceAll("\r", "").trim())
    val element = getFileAdapter.getLastChild
    val generated = QuickDocTest.quickDocGenerator.generateDoc(element, element)
    Assert.assertEquals(expected, generated)
  }
}


object QuickDocTest {
  val quickDocGenerator = new ScalaDocumentationProvider
}
