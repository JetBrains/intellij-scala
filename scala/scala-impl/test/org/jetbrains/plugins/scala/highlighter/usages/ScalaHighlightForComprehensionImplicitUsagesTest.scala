package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaVersion

abstract class ScalaHighlightForComprehensionImplicitUsagesTestBase extends ScalaHighlightUsagesHandlerTestBase {
  override def createHandler: HighlightUsagesHandlerBase[PsiElement] =
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]

  protected def check(methods: String, expression: String, expectedUsages: String*): Unit = {
    val code =
      s"""object Test {
         |  class Context
         |  class Container[A] {
         |    $methods
         |  }
         |  implicit val ${CARET}context: Context = new Context
         |  val values = new Container[Int]
         |  val options = new Container[Option[Int]]
         |  def source(implicit context: Context): Container[Int] = ???
         |  val result = $expression
         |}
         |""".stripMargin

    doTest(code, Seq("context") ++ expectedUsages)
  }

  def testMap(): Unit = check(
    "def map[B](f: A => B)(implicit context: Context): Container[B] = ???",
    "for (a <- values) yield a + 1",
    "<-"
  )

  def testFlatMap(): Unit = check(
    "def flatMap[B](f: A => Option[B])(implicit context: Context): Option[B] = ???",
    "for (a <- values; b <- Option(a)) yield b + 1",
    "<-"
  )

  def testMapForBinding(): Unit = check(
    "def map[B](f: A => B)(implicit context: Context): List[B] = ???",
    "for (a <- values; b = a + 1; if b > 0) yield b + 1",
    "="
  )

  def testForeach(): Unit = check(
    "def foreach(f: A => Unit)(implicit context: Context): Unit = ???",
    "for (a <- values) println(a)",
    "<-"
  )

  def testWithFilterGuard(): Unit = check(
    """def withFilter(f: A => Boolean)(implicit context: Context): Container[A] = ???
      |def map[B](f: A => B): Container[B] = ???
      |""".stripMargin,
    "for (a <- values if a > 0) yield a + 1",
    "if"
  )

  protected def pattern: String = if (version.isScala3) "case Some(a)" else "Some(a)"

  def testWithFilterPattern(): Unit = check(
    """def withFilter(f: A => Boolean)(implicit context: Context): Container[A] = ???
      |def map[B](f: A => B): Container[B] = ???
      |""".stripMargin,
    s"for ($pattern <- options) yield a + 1",
    "<-"
  )

  def testWithFilterPatternAndGuard(): Unit = check(
    """def withFilter(f: A => Boolean)(implicit context: Context): Container[A] = ???
      |def map[B](f: A => B): Container[B] = ???
      |""".stripMargin,
    s"for ($pattern <- options if a > 0) yield a + 1",
    "<-", "if"
  )

  def testImplicitArgumentInSourceExpression(): Unit = check(
    "def map[B](f: A => B): Container[B] = ???",
    "for (a <- source) yield a + 1",
    "source"
  )
}

class ScalaHighlightForComprehensionImplicitUsagesTest_Scala211 extends ScalaHighlightForComprehensionImplicitUsagesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

  def testFilterGuard(): Unit = check(
    """def filter(f: A => Boolean)(implicit context: Context): Container[A] = ???
      |def map[B](f: A => B): Container[B] = ???
      |""".stripMargin,
    "for (a <- values if a > 0) yield a + 1",
    "if"
  )

  def testFilterPattern(): Unit = check(
    """def filter(f: A => Boolean)(implicit context: Context): Container[A] = ???
      |def map[B](f: A => B): Container[B] = ???
      |""".stripMargin,
    "for (Some(a) <- options) yield a + 1",
    "<-"
  )

  def testWithFilterTakesPrecedenceOverFilter(): Unit = check(
    """def filter(f: A => Boolean)(implicit context: Context): Container[A] = ???
      |def withFilter(f: A => Boolean): Container[A] = ???
      |def map[B](f: A => B): Container[B] = ???
      |""".stripMargin,
    "for (a <- values if a > 0) yield a + 1"
  )
}

class ScalaHighlightForComprehensionImplicitUsagesTest_Scala212 extends ScalaHighlightForComprehensionImplicitUsagesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class ScalaHighlightForComprehensionImplicitUsagesTest_Scala213 extends ScalaHighlightForComprehensionImplicitUsagesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ScalaHighlightForComprehensionImplicitUsagesTest_Scala3 extends ScalaHighlightForComprehensionImplicitUsagesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}
