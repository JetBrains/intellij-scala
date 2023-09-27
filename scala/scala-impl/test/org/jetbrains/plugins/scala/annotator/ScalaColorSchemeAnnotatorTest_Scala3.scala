package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertEquals

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaColorSchemeAnnotatorTest_Scala3 extends ScalaColorSchemeAnnotatorTestBase[TextAttributesKey] {

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

  def testContextParameter(): Unit = {
    testAllAnnotations("def foo(using p: Int): Unit = ()",
      """Info((4,7),foo,Scala Method declaration)
        |Info((8,13),using,Scala Keyword)
        |Info((14,15),p,Scala Parameter)
        |Info((17,20),Int,Scala Predefined types)
        |Info((23,27),Unit,Scala Predefined types)
        |""".stripMargin
    )
  }

  def testContextParameterAnonymous(): Unit = {
    testAllAnnotations("def foo(using Int): Unit = ()",
      """Info((4,7),foo,Scala Method declaration)
        |Info((8,13),using,Scala Keyword)
        |Info((14,17),Int,Scala Predefined types)
        |Info((20,24),Unit,Scala Predefined types)
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

  private def assertNoMultipleHighlightsForSageRange(annotations: Seq[Message2]): Unit = {
    val annotationsDuplicates = annotations.groupBy(_.range).filter(_._2.size > 1)
    assertEquals(
      "Found multiple highlighting for the same text range",
      "",
      annotationsDuplicates.mkString("\n")
    )
  }

  private def doPartialHighlightTest(
    fileText: String,
    codePartToHighlight: String,
    expectedAnnotationsText: String
  ): Unit = {
    val annotations = annotateWithColorSchemeAnnotator(fileText).filter(_.code == codePartToHighlight)

    assertNoMultipleHighlightsForSageRange(annotations)
    assertEquals(
      s"Wrong color scheme highlighting",
      expectedAnnotationsText.trim,
      buildAnnotationsTestText(annotations)
    )
  }

  def testEnum(): Unit = {
    val code =
      """enum MyEnum {
        |  case MyCase1
        |  case MyCase2(x: Int)
        |}
        |
        |//noinspection ScalaUnusedExpression
        |object usage {
        |  //type annotation
        |  def foo(x: MyEnum, y: MyEnum.type): MyEnum = ???
        |
        |  //referencing enum case
        |  MyEnum.MyCase1
        |
        |  //using in imports
        |  import MyEnum._
        |  import MyEnum.MyCase1
        |}
        |""".stripMargin

    doPartialHighlightTest(code, "MyEnum",
      """Info((5,11),MyEnum,Scala Enum)
        |Info((140,146),MyEnum,Scala Enum)
        |Info((151,157),MyEnum,Scala Enum)
        |Info((165,171),MyEnum,Scala Enum)
        |Info((207,213),MyEnum,Scala Enum)
        |Info((253,259),MyEnum,Scala Enum)
        |Info((271,277),MyEnum,Scala Enum)
        |""".stripMargin
    )
  }

  private val CodeWithEnumCases =
    """enum MyEnum {
      |  case MyCase1_WithoutParameters
      |  case MyCase2_WithEmptyParameters()
      |  case MyCase3_WithParameters(x: Int)
      |  case MyCase4_WithTypeParameters[T](x: T)
      |  case MyCase5_WithExtendsList extends MyEnum
      |  case MyCase6_WithParametersAndExtendsList(y: Int) extends MyEnum
      |}
      |
      |val x1: MyEnum = MyEnum.MyCase1_WithoutParameters
      |val x2: MyEnum = MyEnum.MyCase2_WithEmptyParameters()
      |val x3: MyEnum = MyEnum.MyCase3_WithParameters(42)
      |val x4: MyEnum = MyEnum.MyCase4_WithTypeParameters[String]("42")
      |val x5: MyEnum = MyEnum.MyCase5_WithExtendsList
      |val x6: MyEnum = MyEnum.MyCase6_WithParametersAndExtendsList(23)
      |""".stripMargin

  def testEnumCase_WithoutParameters(): Unit = {
    doPartialHighlightTest(CodeWithEnumCases, "MyCase1_WithoutParameters",
      """Info((21,46),MyCase1_WithoutParameters,Scala Enum Singleton Case)
        |Info((305,330),MyCase1_WithoutParameters,Scala Enum Singleton Case)
        |""".stripMargin
    )
  }

  def testEnumCase_WithEmptyParameters(): Unit = {
    doPartialHighlightTest(CodeWithEnumCases, "MyCase2_WithEmptyParameters",
      """Info((54,81),MyCase2_WithEmptyParameters,Scala Enum Class Case)
        |Info((355,382),MyCase2_WithEmptyParameters,Scala Enum Class Case)
        |""".stripMargin
    )
  }

  def testEnumCase_WithParameters(): Unit = {
    doPartialHighlightTest(CodeWithEnumCases, "MyCase3_WithParameters",
      """Info((91,113),MyCase3_WithParameters,Scala Enum Class Case)
        |Info((409,431),MyCase3_WithParameters,Scala Enum Class Case)
        |""".stripMargin
    )
  }

  def testEnumCase_WithTypeParameters(): Unit = {
    doPartialHighlightTest(CodeWithEnumCases, "MyCase4_WithTypeParameters",
      """Info((129,155),MyCase4_WithTypeParameters,Scala Enum Class Case)
        |Info((460,486),MyCase4_WithTypeParameters,Scala Enum Class Case)
        |""".stripMargin
    )
  }

  def testEnumCase_WithExtendsList(): Unit = {
    doPartialHighlightTest(CodeWithEnumCases, "MyCase5_WithExtendsList",
      """Info((172,195),MyCase5_WithExtendsList,Scala Enum Singleton Case)
        |Info((525,548),MyCase5_WithExtendsList,Scala Enum Singleton Case)
        |""".stripMargin
    )
  }

  def testEnumCase_WithParametersAndExtendsList(): Unit = {
    doPartialHighlightTest(CodeWithEnumCases, "MyCase6_WithParametersAndExtendsList",
      """Info((218,254),MyCase6_WithParametersAndExtendsList,Scala Enum Class Case)
        |Info((573,609),MyCase6_WithParametersAndExtendsList,Scala Enum Class Case)
        |""".stripMargin
    )
  }
}
