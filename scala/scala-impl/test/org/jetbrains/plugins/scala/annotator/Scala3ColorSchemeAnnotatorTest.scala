package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3ColorSchemeAnnotatorTest extends ScalaColorSchemeAnnotatorTestBase[TextAttributesKey] {
  import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter._

  override protected def buildAnnotationsTestText(annotations: Seq[Message2]): String =
    annotations.map(_.textWithRangeAndCodeAttribute).mkString("\n")

  protected def needToAnnotateElement(element: PsiElement): Boolean = true

  override protected def getFilterByField(annotation: Message2): TextAttributesKey = annotation.textAttributesKey

  def testSoftKeywords_As(): Unit = {
    val text =
      """
        |import java.lang.StringBuilder as JBuilder
        |val jb: JBuilder = ???
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((32,34),as,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Derives(): Unit = {
    val text =
      """
        |case class Point(x: Int, y: Int) derives Ordering
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((34,41),derives,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_End(): Unit = {
    val text =
      """
        |def foo(): Unit =
        |  ???
        |end foo
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((25,28),end,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Extension(): Unit = {
    val text =
      """
        |extension (n: Int)
        |  def isOdd: Boolean = n % 2 == 0
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((1,10),extension,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Opaque(): Unit = {
    val text =
      """
        |opaque type CustomerId = Int
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((1,7),opaque,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Inline(): Unit = {
    val text =
      """
        |inline def foo(): Unit = ???
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((1,7),inline,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Transparent(): Unit = {
    val text =
      """
        |transparent trait S
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((1,12),transparent,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Using(): Unit = {
    val text =
      """
        |def foreach(f: T => Unit)(using ec: ExecutionContext): Unit = ???
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((27,32),using,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Open(): Unit = {
    val text =
      """
        |open class Foo
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((1,5),open,Scala Keyword)
        |""".stripMargin
    )
  }

  def testSoftKeywords_Infix(): Unit = {
    val text =
      """
        |infix def combine(other: T): T
        |""".stripMargin

    testAnnotations(text, KEYWORD,
      """
        |Info((1,6),infix,Scala Keyword)
        |""".stripMargin
    )
  }
}
