package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import junit.framework.TestCase.{assertEquals, assertNotNull}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFor, ScIf, ScMatch, ScTry, ScWhile}
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import scala.reflect.ClassTag

class RewritersTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private def check[E <: ScalaPsiElement : ClassTag](text: String, expectedText: String)
                                                    (operation: E => (ProjectContext, ScalaFeatures) => ScalaPsiElement): Unit = {
    val file = configureFromFileText(text)
    val result = file.children.collectFirst {
      case expr: E =>
        operation(expr)(expr.projectContext, expr)
    }.orNull

    assertNotNull(result)
    assertEquals(expectedText, result.getText)
    myFixture.checkResult(expectedText)
  }

  def checkNewSyntax[E <: ScExpression : ClassTag](text: String, expectedText: String): Unit =
    check[E](text, expectedText)(e => e.toNewSyntax(_, _))

  def checkIndentationBasedSyntax[E <: ScExpression : ClassTag](text: String, expectedText: String): Unit =
    check[E](text, expectedText)(e => e.toIndentationBasedSyntax(_, _))

  def testIf_newSyntax(): Unit = checkNewSyntax[ScIf](
    """if (a && b) {
      |  println("a && b")
      |}""".stripMargin,
    """if a && b then {
      |  println("a && b")
      |}""".stripMargin
  )

  def testIf_indentationBasedSyntax(): Unit = checkIndentationBasedSyntax[ScIf](
    """if (a && b) {
      |  println("a && b")
      |}""".stripMargin,
    """if a && b then
      |  println("a && b")""".stripMargin
  )

  def testIfElse_newSyntax(): Unit = checkNewSyntax[ScIf](
    """if (a && b) {
      |  println("a && b")
      |} else {
      |  println("something else")
      |}""".stripMargin,
    """if a && b then {
      |  println("a && b")
      |} else {
      |  println("something else")
      |}""".stripMargin
  )

  def testIfElse_indentationBasedSyntax(): Unit = checkIndentationBasedSyntax[ScIf](
    """if (a && b) {
      |  println("a && b")
      |} else {
      |  println("something else")
      |}""".stripMargin,
    """if a && b then
      |  println("a && b")
      |else
      |  println("something else")""".stripMargin
  )

  def testForDo_newSyntax(): Unit = checkNewSyntax[ScFor](
    """for (x <- xs) {
      |  println("a && b")
      |}""".stripMargin,
    """for x <- xs do {
      |  println("a && b")
      |}""".stripMargin
  )

  def testForDo_indentationBasedSyntax(): Unit = checkIndentationBasedSyntax[ScFor](
    """for (x <- xs) {
      |  println("a && b")
      |}""".stripMargin,
    """for x <- xs do
      |  println("a && b")""".stripMargin
  )

  def testForDo_newSyntax2(): Unit = checkNewSyntax[ScFor](
    """for (x <- xs) { println("a && b") }""".stripMargin,
    """for x <- xs do { println("a && b") }""".stripMargin
  )

  def testForDo_indentationBasedSyntax2(): Unit = checkIndentationBasedSyntax[ScFor](
    """for (x <- xs) { println("a && b") }""".stripMargin,
    """for x <- xs do
      |  println("a && b")""".stripMargin
  )

  def testForDo_newSyntax3(): Unit = checkNewSyntax[ScFor](
    """for { x <- xs } { println("a && b") }""".stripMargin,
    """for x <- xs do { println("a && b") }""".stripMargin
  )

  def testForDo_indentationBasedSyntax3(): Unit = checkIndentationBasedSyntax[ScFor](
    """for { x <- xs } { println("a && b") }""".stripMargin,
    """for x <- xs do
      |  println("a && b")""".stripMargin
  )

  def testForDo_newSyntax4(): Unit = checkNewSyntax[ScFor](
    """for {
      |  x <- xs
      |} { println("a && b") }""".stripMargin,
    """for
      |  x <- xs
      |do { println("a && b") }""".stripMargin
  )

  def testForDo_indentationBasedSyntax4(): Unit = checkIndentationBasedSyntax[ScFor](
    """for {
      |  x <- xs
      |} { println("a && b") }""".stripMargin,
    """for
      |  x <- xs
      |do
      |  println("a && b")""".stripMargin
  )

  def testForDo_newSyntax5(): Unit = checkNewSyntax[ScFor](
    """for {
      |  x <- xs
      |} yield { 2 }""".stripMargin,
    """for
      |  x <- xs
      |yield { 2 }""".stripMargin
  )

  def testForDo_indentationBasedSyntax5(): Unit = checkIndentationBasedSyntax[ScFor](
    """for {
      |  x <- xs
      |} yield { 2 }""".stripMargin,
    """for
      |  x <- xs
      |yield
      |  2""".stripMargin
  )

  def testForDo_newSyntax6(): Unit = checkNewSyntax[ScFor](
    """for { x <- xs; y <- ys } { println("a && b") }""".stripMargin,
    """for x <- xs; y <- ys do { println("a && b") }""".stripMargin
  )

  def testForDo_indentationBasedSyntax6(): Unit = checkIndentationBasedSyntax[ScFor](
    """for { x <- xs; y <- ys } { println("a && b") }""".stripMargin,
    """for x <- xs; y <- ys do
      |  println("a && b")""".stripMargin
  )

  def testForDo_newSyntax7(): Unit = checkNewSyntax[ScFor](
    """for { i <-  List(1,2,3)}
      |      {
      |        val a = 2
      |        println(s"$a")
      |      }""".stripMargin,
    """for i <- List(1, 2, 3) do {
      |        val a = 2
      |        println(s"$a")
      |      }""".stripMargin
  )

  def testForDo_indentationBasedSyntax7(): Unit = checkIndentationBasedSyntax[ScFor](
    """for { i <-  List(1,2,3)}
      |      {
      |        val a = 2
      |        println(s"$a")
      |      }""".stripMargin,
    """for i <- List(1, 2, 3) do
      |  val a = 2
      |  println(s"$a")""".stripMargin
  )

  def testForYield_newSyntax(): Unit = checkNewSyntax[ScFor](
    """for { x <- xs } yield { 2 }""".stripMargin,
    """for x <- xs yield { 2 }""".stripMargin
  )

  def testForYield_indentationBasedSyntax(): Unit = checkIndentationBasedSyntax[ScFor](
    """for { x <- xs } yield { 2 }""".stripMargin,
    """for x <- xs yield
      |  2""".stripMargin
  )

  def testForYield_newSyntax2(): Unit = checkNewSyntax[ScFor](
    """for { x <- xs } yield 2""".stripMargin,
    """for x <- xs yield 2""".stripMargin
  )

  def testForYield_indentationBasedSyntax2(): Unit = checkIndentationBasedSyntax[ScFor](
    """for { x <- xs } yield 2""".stripMargin,
    """for x <- xs yield 2""".stripMargin
  )

  def testMatch_newSyntax(): Unit = checkNewSyntax[ScMatch](
    """2 match {
      |  case 2 => ???
      |  case _ => ???
      |}""".stripMargin,
    """2 match {
      |  case 2 => ???
      |  case _ => ???
      |}""".stripMargin
  )

  def testMatch(): Unit = checkIndentationBasedSyntax[ScMatch](
    """2 match {
      |  case 2 => ???
      |  case _ => ???
      |}""".stripMargin,
    """2 match
      |  case 2 => ???
      |  case _ => ???""".stripMargin
  )

  def testWhile_newSyntax(): Unit = checkNewSyntax[ScWhile](
    """while (true) {
      |  println(1)
      |  println(2)
      |}""".stripMargin,
    """while true do {
      |  println(1)
      |  println(2)
      |}""".stripMargin
  )

  def testWhile(): Unit = checkIndentationBasedSyntax[ScWhile](
    """while (true) {
      |  println(1)
      |  println(2)
      |}""".stripMargin,
    """while true do
      |  println(1)
      |  println(2)""".stripMargin
  )

  def testTry_newSyntax(): Unit = checkNewSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |}""".stripMargin,
    """try {
      |  println("foo")
      |  println("bar")
      |}""".stripMargin
  )

  def testTry(): Unit = checkIndentationBasedSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |}""".stripMargin,
    """try
      |  println("foo")
      |  println("bar")""".stripMargin
  )

  def testTryCatch_newSyntax(): Unit = checkNewSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |} catch {
      |  case _ => ???
      |}""".stripMargin,
    """try {
      |  println("foo")
      |  println("bar")
      |} catch {
      |  case _ => ???
      |}""".stripMargin
  )

  def testTryCatch(): Unit = checkIndentationBasedSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |} catch {
      |  case _ => ???
      |}""".stripMargin,
    """try
      |  println("foo")
      |  println("bar")
      |catch
      |  case _ => ???""".stripMargin
  )

  def testTryFinally_newSyntax(): Unit = checkNewSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |}
      |finally {
      |  println("baz")
      |}""".stripMargin,
    """try {
      |  println("foo")
      |  println("bar")
      |}
      |finally {
      |  println("baz")
      |}""".stripMargin
  )

  def testTryFinally(): Unit = checkIndentationBasedSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |}
      |finally {
      |  println("baz")
      |}""".stripMargin,
    """try
      |  println("foo")
      |  println("bar")
      |finally
      |  println("baz")""".stripMargin
  )

  def testTryCatchFinally_newSyntax(): Unit = checkNewSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |} catch {
      |  case _ => ???
      |}
      |finally {
      |  println("baz")
      |}""".stripMargin,
    """try {
      |  println("foo")
      |  println("bar")
      |} catch {
      |  case _ => ???
      |}
      |finally {
      |  println("baz")
      |}""".stripMargin
  )

  def testTryCatchFinally(): Unit = checkIndentationBasedSyntax[ScTry](
    """try {
      |  println("foo")
      |  println("bar")
      |} catch {
      |  case _ => ???
      |}
      |finally {
      |  println("baz")
      |}""".stripMargin,
    """try
      |  println("foo")
      |  println("bar")
      |catch
      |  case _ => ???
      |finally
      |  println("baz")""".stripMargin
  )
}
