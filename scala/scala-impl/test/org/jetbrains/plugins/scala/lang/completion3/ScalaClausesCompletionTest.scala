package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.lang.completion.clauses.ExhaustiveMatchCompletionContributor

class ScalaClausesCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}
  import ScalaCodeInsightTestBase._

  override implicit val version: ScalaVersion = Scala_2_12

  def testSyntheticUnapply(): Unit = doCompletionTest(
    fileText =
      s"""case class Foo(foo: Int = 42)(bar: Int = 42)
         |
         |Foo()() match {
         |  case $CARET
         |}
       """.stripMargin,
    resultText =
      s"""case class Foo(foo: Int = 42)(bar: Int = 42)
         |
         |Foo()() match {
         |  case Foo(foo)$CARET
         |}
       """.stripMargin,
    item = "Foo(foo)"
  )

  def testSyntheticUnapplyVararg(): Unit = doCompletionTest(
    fileText =
      s"""case class Foo(foos: Int*)
         |
         |Foo() match {
         |  case $CARET
         |}
       """.stripMargin,
    resultText =
      s"""case class Foo(foos: Int*)
         |
         |Foo() match {
         |  case Foo(foos@_*)$CARET
         |}
       """.stripMargin,
    item = "Foo(foos@_*)"
  )

  def testUnapply(): Unit = doMultipleCompletionTest(
    fileText =
      s"""class Foo(val foo: Int = 42, val bar: Int = 42)
         |
         |object Foo {
         |  def unapply(foo: Foo): Option[(Int, Int)] = Some(foo.foo, foo.bar)
         |}
         |
         |new Foo() match {
         |  case $CARET
         |}
       """.stripMargin,
    items = "Foo(i, i1)"
  )

  def testTraitUnapply(): Unit = doMultipleCompletionTest(
    fileText =
      s"""trait Foo
         |
         |object Foo {
         |  def unapply(foo: Foo): Option[Foo] = None
         |}
         |
         |(_: Foo) match {
         |  case $CARET
         |}
       """.stripMargin,
    items = "Foo(foo)"
  )

  def testBeforeCase(): Unit = checkNoCompletion(
    fileText =
      s"""case class Foo()
         |
         |Foo() match {
         |  $CARET
         |}
      """.stripMargin,
    item = "Foo()"
  )

  def testAfterArrow(): Unit = checkNoCompletion(
    fileText =
      s"""case class Foo()
         |
         |Foo() match {
         |  case _ => $CARET
         |}
      """.stripMargin,
    item = "Foo()"
  )

  def testNestedPatternCompletion(): Unit = doMultipleCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class FooImpl(foo: Int = 42) extends Foo
         |
         |case object Bar extends Foo
         |
         |case class Baz(foo: Foo = FooImpl())
         |
         |Baz() match {
         |  case Baz(null | $CARET)
         |}
       """.stripMargin,
    items = "FooImpl(foo)", "Bar"
  )

  def testSealedTraitInheritors(): Unit = doMultipleCompletionTest(
    fileText =
      s"""sealed trait Foo {
         |  def foo: Int = 42
         |}
         |
         |class FooImpl() extends Foo
         |
         |object FooImpl {
         |  def unapply(foo: FooImpl): Option[Int] = Some(foo.foo)
         |}
         |
         |case class Bar(override val foo: Int) extends Foo
         |
         |trait Baz extends Foo
         |
         |(_: Foo) match {
         |  case $CARET
         |}
       """.stripMargin,
    items = "FooImpl(i)", "Bar(foo)"
  )

  def testCollectPatternCompletion(): Unit = doCompletionTest(
    fileText =
      s"""case class Foo(foo: Int = 42)(bar: Int = 42)
         |
         |Some(Foo()()).collect {
         |  case $CARET
         |}
       """.stripMargin,
    resultText =
      s"""case class Foo(foo: Int = 42)(bar: Int = 42)
         |
         |Some(Foo()()).collect {
         |  case Foo(foo)$CARET
         |}
       """.stripMargin,
    item = "Foo(foo)"
  )

  def testNamedPatternCompletion(): Unit = doCompletionTest(
    fileText =
      s"""case class Foo()
         |
         |Foo() match {
         |  case foo@$CARET
         |}
       """.stripMargin,
    resultText =
      s"""case class Foo()
         |
         |Foo() match {
         |  case foo@Foo()$CARET
         |}
       """.stripMargin,
    item = "Foo()"
  )

  def testAnonymousInheritorCompletion(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  val Impl = new Foo {}
         |}
         |
         |(_: Foo) match {
         |  case $CARET
         |}
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  val Impl = new Foo {}
         |}
         |
         |(_: Foo) match {
         |  case Foo.Impl$CARET
         |}
       """.stripMargin,
    item = "Foo.Impl"
  )

  def testJavaInheritorCompletion(): Unit = {
    configureJavaFile(
      fileText = "public class Bar extends Foo {}",
      className = "Bar"
    )

    doCompletionTest(
      fileText =
        s"""sealed trait Foo
           |
           |(_: Foo) match {
           |  case $CARET
           |}
         """.stripMargin,
      resultText =
        s"""sealed trait Foo
           |
           |(_: Foo) match {
           |  case bar: Bar$CARET
           |}
         """.stripMargin,
      item = "bar: Bar"
    )
  }

  def testSealedTrait(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object FooImpl extends Foo
         |
         |case class Bar() extends Foo
         |
         |class Baz extends Foo
         |
         |object Baz {
         |  def unapply(baz: Baz) = Option(baz)
         |}
         |
         |(_: Foo) $CARET
         """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object FooImpl extends Foo
         |
         |case class Bar() extends Foo
         |
         |class Baz extends Foo
         |
         |object Baz {
         |  def unapply(baz: Baz) = Option(baz)
         |}
         |
         |(_: Foo) match {
         |  case FooImpl => $CARET
         |  case Bar() =>
         |  case Baz(baz) =>
         |  case baz: Baz =>
         |}
         """.stripMargin
  )

  def testJavaEnum(): Unit = doMatchCompletionTest(
    fileText =
      s"""import java.nio.file.FileVisitResult
         |
         |(_: FileVisitResult) m$CARET
         """.stripMargin,
    resultText =
      s"""import java.nio.file.FileVisitResult
         |
         |(_: FileVisitResult) match {
         |  case FileVisitResult.CONTINUE => $CARET
         |  case FileVisitResult.TERMINATE =>
         |  case FileVisitResult.SKIP_SUBTREE =>
         |  case FileVisitResult.SKIP_SIBLINGS =>
         |}
         """.stripMargin
  )

  def testScalaEnum(): Unit = doMatchCompletionTest(
    fileText =
      s"""object Margin extends Enumeration {
         |  type Margin = Value
         |
         |  val TOP, BOTTOM = Value
         |  val LEFT, RIGHT = Value
         |
         |  private val NULL = Value
         |}
         |
         |(_: Margin.Margin) m$CARET
       """.stripMargin,
    resultText =
      s"""object Margin extends Enumeration {
         |  type Margin = Value
         |
         |  val TOP, BOTTOM = Value
         |  val LEFT, RIGHT = Value
         |
         |  private val NULL = Value
         |}
         |
         |(_: Margin.Margin) match {
         |  case Margin.TOP => $CARET
         |  case Margin.BOTTOM =>
         |  case Margin.LEFT =>
         |  case Margin.RIGHT =>
         |}
       """.stripMargin
  )

  def testScalaEnum2(): Unit = doMatchCompletionTest(
    fileText =
      s"""object Margin extends Enumeration {
         |
         |  protected case class Val() extends super.Val
         |
         |  val Top, Bottom = Val()
         |  val Left, Right = Val()
         |}
         |
         |(_: Margin.Value) m$CARET
       """.stripMargin,
    resultText =
      s"""object Margin extends Enumeration {
         |
         |  protected case class Val() extends super.Val
         |
         |  val Top, Bottom = Val()
         |  val Left, Right = Val()
         |}
         |
         |(_: Margin.Value) match {
         |  case Margin.Top => $CARET
         |  case Margin.Bottom =>
         |  case Margin.Left =>
         |  case Margin.Right =>
         |}
       """.stripMargin
  )

  def testFromScalaPackage(): Unit = doMatchCompletionTest(
    fileText =
      s"""(_: List[String]) m$CARET
         """.stripMargin,
    resultText =
      s"""(_: List[String]) match {
         |  case Nil => $CARET
         |  case ::(head, tl) =>
         |}
        """.stripMargin
  )

  def testVarargs(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar(foos: Foo*) extends Foo
         |
         |(_: Foo) m$CARET
         """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar(foos: Foo*) extends Foo
         |
         |(_: Foo) match {
         |  case Bar(foos@_*) => $CARET
         |}
         """.stripMargin
  )

  def testNonSealedClass(): Unit = checkNoCompletion(
    fileText =
      s"""trait Foo
         |
         |class FooImpl extends Foo
         |
         |object FooImpl {
         |  def unapply(impl: FooImpl) = Some(impl)
         |}
         |
         |(_: Foo) m$CARET
       """.stripMargin,
    time = DEFAULT_TIME,
    completionType = DEFAULT_COMPLETION_TYPE
  )(isExhaustiveMatch)

  def testMaybe(): Unit = doMatchCompletionTest(
    fileText =
      s"""val maybeFoo = Option("foo")
         |
         |maybeFoo m$CARET
       """.stripMargin,
    resultText =
      s"""val maybeFoo = Option("foo")
         |
         |maybeFoo match {
         |  case Some(value) => $CARET
         |  case None =>
         |}
       """.stripMargin
  )

  def testAnonymousInheritor(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |val impl = new Foo() {}
         |
         |(_: Foo) m$CARET
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |val impl = new Foo() {}
         |
         |(_: Foo) match {
         |  case Bar() => $CARET
         |  case _ =>
         |}
       """.stripMargin
  )

  def testStableAnonymousInheritor(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  val Impl = new Foo() {}
         |}
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) m$CARET
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  val Impl = new Foo() {}
         |}
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case Foo.Impl => $CARET
         |  case Bar() =>
         |}
       """.stripMargin
  )

  def testLowerCaseExtractor(): Unit = doCompletionTest(
    fileText =
      s"""trait Foo {
         |
         |  object foo {
         |    def unapply(i: Int) = Some(i)
         |  }
         |}
         |
         |object Bar extends Foo {
         |
         |  (_: Int) match {
         |    case f$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""trait Foo {
         |
         |  object foo {
         |    def unapply(i: Int) = Some(i)
         |  }
         |}
         |
         |object Bar extends Foo {
         |
         |  (_: Int) match {
         |    case foo$CARET
         |  }
         |}
      """.stripMargin,
    item = "foo"
  )

  def testJavaInheritor(): Unit = {
    configureJavaFile(
      fileText = "public class Baz extends Foo {}",
      className = "Baz"
    )

    doMatchCompletionTest(
      fileText =
        s"""sealed class Foo
           |class Bar extends Foo
           |
           |(_: Foo) m$CARET
         """.stripMargin,
      resultText =
        s"""sealed class Foo
           |class Bar extends Foo
           |
           |(_: Foo) match {
           |  case bar: Bar => $CARET
           |  case baz: Baz =>
           |}
         """.stripMargin
    )
  }

  def testTypesAdjustment(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  val Foo = new Foo {}
         |
         |  object Bar extends Foo
         |
         |  class Baz[+T, -U] extends Foo
         |
         |  object Baz {
         |    def unapply[T, U](baz: Baz[T, U]) = Option(baz)
         |  }
         |}
         |
         |(_: Foo) m$CARET
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  val Foo = new Foo {}
         |
         |  object Bar extends Foo
         |
         |  class Baz[+T, -U] extends Foo
         |
         |  object Baz {
         |    def unapply[T, U](baz: Baz[T, U]) = Option(baz)
         |  }
         |}
         |
         |(_: Foo) match {
         |  case Foo.Foo => $CARET
         |  case Foo.Bar =>
         |  case Foo.Baz(value) =>
         |  case baz: Foo.Baz[_, _] =>
         |}
       """.stripMargin
  )

  private def doMultipleCompletionTest(fileText: String,
                                       items: String*): Unit =
    super.doMultipleCompletionTest(fileText, items.size, DEFAULT_CHAR, DEFAULT_TIME, DEFAULT_COMPLETION_TYPE) { lookup =>
      items.contains(lookup.getLookupString)
    }

  private def doMatchCompletionTest(fileText: String, resultText: String): Unit =
    super.doCompletionTest(fileText, resultText, DEFAULT_CHAR, DEFAULT_TIME, DEFAULT_COMPLETION_TYPE)(isExhaustiveMatch)

  private def isExhaustiveMatch(lookup: LookupElement) = {
    import ExhaustiveMatchCompletionContributor.{ItemText, RendererTailText}
    hasItemText(lookup, ItemText, ItemText, RendererTailText)
  }
}
