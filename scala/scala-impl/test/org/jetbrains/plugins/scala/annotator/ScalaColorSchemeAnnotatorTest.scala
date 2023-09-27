package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

class ScalaColorSchemeAnnotatorTest extends ScalaColorSchemeAnnotatorTestBase[TextAttributesKey] {
  import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter._

  override protected def buildAnnotationsTestText(annotations: Seq[Message2]): String =
    annotations.map(_.textWithRangeAndCodeAttribute).mkString("\n")

  protected def needToAnnotateElement(element: PsiElement): Boolean = true

  override protected def getFilterByField(annotation: Message2): TextAttributesKey = annotation.textAttributesKey

  def testAnnotateGeneratorAndEnumerator(): Unit = {
    val text =
      s"""for {
         |  case (a, b) <- Seq()
         |  (c, d) <- Seq()
         |} yield {
         |  println((a, b))
         |  println((c, d))
         |}
         |""".stripMargin

    testAnnotations(text, GENERATOR,
      """Info((14,15),a,Scala For statement value)
        |Info((17,18),b,Scala For statement value)
        |Info((32,33),c,Scala For statement value)
        |Info((35,36),d,Scala For statement value)
        |Info((68,69),a,Scala For statement value)
        |Info((71,72),b,Scala For statement value)
        |Info((86,87),c,Scala For statement value)
        |Info((89,90),d,Scala For statement value)
        |""".stripMargin)
  }

  def testForYield(): Unit = {
    val text =
      s"""for {
         |  c <- Seq()
         |} yield {
         |}
         |""".stripMargin

    testAnnotations(text, GENERATOR,
      """
        |Info((8,9),c,Scala For statement value)
        |""".stripMargin)
  }

  def testAnnotatePattern_1(): Unit = {
    val text =
      s"""??? match {
         |  case (a, b) =>
         |    (a, b)
         |}""".stripMargin

    testAnnotations(text, PATTERN,
      """Info((20,21),a,Scala Pattern value)
        |Info((23,24),b,Scala Pattern value)
        |Info((34,35),a,Scala Pattern value)
        |Info((37,38),b,Scala Pattern value)
        |""".stripMargin)
  }

  def testAnnotatePattern_2(): Unit = {
    val text =
      s"""val sourceRoots = Seq()
         |val translatedTemplatePath = ""
         |
         |lazy val xxx: Option[String] = {
         |  sourceRoots collectFirst {
         |    case root if  root == null =>
         |      println(root)
         |      ???
         |  }
         |}
         |
         |lazy val yyy: Option[String] = {
         |  sourceRoots collectFirst {
         |    case root if root == null =>
         |      println(root)
         |      42 // type mismatch error
         |  }
         |}""".stripMargin

    testAnnotations(text, PATTERN,
      """Info((128,132),root,Scala Pattern value)
        |Info((137,141),root,Scala Pattern value)
        |Info((167,171),root,Scala Pattern value)
        |Info((261,265),root,Scala Pattern value)
        |Info((269,273),root,Scala Pattern value)
        |Info((299,303),root,Scala Pattern value)
        |""".stripMargin)
  }

  def testAnnotatePattern_3(): Unit = {
    val text =
      s"""??? match {
         |  case a =>
         |}""".stripMargin

    testAnnotations(text, PATTERN,
      """Info((19,20),a,Scala Pattern value)
        |""".stripMargin)
  }

  def testBooleans(): Unit = {
    val text =
      """
        |val t: Boolean = true
        |val f: Boolean = false
        |""".stripMargin

    testAnnotations(text, PREDEF,
      """Info((8,15),Boolean,Scala Predefined types)
        |Info((30,37),Boolean,Scala Predefined types)
        |""".stripMargin)
  }

  def testStringInterpolation(): Unit = {
    testAllAnnotations(
      """raw"Hi ${System.currentTimeMillis()}"
        |""".stripMargin,
      """Info((9,15),System,Scala Object)
        |Info((16,33),currentTimeMillis,Scala Object method call)
        |""".stripMargin
    )
  }

  def testStringInterpolation_2(): Unit = {
    getFixture.addFileToProject("defs.scala",
      """case class Bar()
        |def foo(b: Bar): Unit = ???
        |val bar = Bar()
        |""".stripMargin)

    testAllAnnotations(
      """s"one two ${foo(bar)} three"
        |""".stripMargin,
      """Info((12,15),foo,Scala Local method call)
        |Info((16,19),bar,Scala Local value)
        |""".stripMargin
    )
  }

  def testLanguageInjection(): Unit = {
    val text =
      """
        |//language=Scala
        |val scalaText = "val a = 1"
        |""".stripMargin
    testAnnotations(text, LOCAL_VALUES,
      """Info((22,31),scalaText,Scala Local value)
        |""".stripMargin)
  }

  def testSymbol(): Unit = {
    val text =
      """
        |val symbol = 'Symbol
        |""".stripMargin
    testAnnotations(text, LOCAL_VALUES,
      """Info((5,11),symbol,Scala Local value)
        |""".stripMargin)
  }

  def testTypeAlias(): Unit = {
    val text =
      """
        |type A = String
        |""".stripMargin
    testAnnotations(text, TYPE_ALIAS,
      """Info((6,7),A,Scala Type Alias)
        |""".stripMargin)
  }

  def testAbstractClass(): Unit = {
    val text =
      """
        |abstract class AbstractClass
        |""".stripMargin

    testAnnotations(text, ABSTRACT_CLASS,
      "Info((16,29),AbstractClass,Scala Abstract class)"
    )
  }

  def testAnnotation(): Unit = {
    val text =
      """
        |@Source(url = "https://foo.com/")
        |trait Foo
        |""".stripMargin

    testAnnotations(text, ANNOTATION,
      """
        |Info((1,2),@,Scala Annotation name)
        |Info((2,8),Source,Scala Annotation name)
        |""".stripMargin
    )
  }

  def testAnonymousParameter(): Unit = {
    val text =
      """
        |(x: Int) => x
        |{ x: Int => x }
        |""".stripMargin

    testAnnotations(text, PARAMETER_OF_ANONIMOUS_FUNCTION,
      """Info((2,3),x,Scala Anonymous Parameter)
        |Info((13,14),x,Scala Anonymous Parameter)
        |Info((17,18),x,Scala Anonymous Parameter)
        |Info((27,28),x,Scala Anonymous Parameter)
        |""".stripMargin
    )
  }

  def testMethodVsValueVsVariable(): Unit = {
    val text =
      """
        |def a = 0
        |val b = 1
        |var c = 2
        |""".stripMargin

    testAnnotations(text, METHOD_DECLARATION,
      """
        |Info((5,6),a,Scala Method declaration)
        |""".stripMargin
    )

    testAnnotations(text, LOCAL_VALUES,
      """
        |Info((15,16),b,Scala Local value)
        |""".stripMargin
    )

    testAnnotations(text, LOCAL_VARIABLES,
      """
        |Info((25,26),c,Scala Local variable)
        |""".stripMargin
    )
  }
}
