package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.{Lookup, LookupElement}
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.lang.completion.clauses.ExhaustiveMatchCompletionContributor

class ScalaClausesCompletionTest extends ScalaCodeInsightTestBase {

  import CompletionType.BASIC
  import EditorTestUtil.{CARET_TAG => CARET}
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCodeInsightTestBase._

  override implicit val version: ScalaVersion = Scala_2_12

  def testSyntheticUnapply(): Unit = doPatternCompletionTest(
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
       """.stripMargin
  )

  def testSyntheticUnapplyVararg(): Unit = doPatternCompletionTest(
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
       """.stripMargin
  )

  def testUnapply(): Unit = doPatternCompletionTest(
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
    resultText =
      s"""trait Foo
         |
         |object Foo {
         |  def unapply(foo: Foo): Option[Foo] = None
         |}
         |
         |(_: Foo) match {
         |  case Foo(_)$CARET
         |}
         """.stripMargin
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

  def testNestedPatternCompletion(): Unit = doPatternCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  case object Bar extends Foo
         |
         |  case class Baz(foo: Foo = Bar) extends Foo
         |}
         |
         |import Foo.Baz
         |Baz() match {
         |  case Baz(null | $CARET)
         |}
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  case object Bar extends Foo
         |
         |  case class Baz(foo: Foo = Bar) extends Foo
         |}
         |
         |import Foo.Baz
         |Baz() match {
         |  case Baz(null | Baz(foo)$CARET)
         |}
       """.stripMargin,
    itemText = "Baz(_)"
  )

  def testCollectPatternCompletion(): Unit = doPatternCompletionTest(
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
       """.stripMargin
  )

  def testNamedPatternCompletion(): Unit = doPatternCompletionTest(
    fileText =
      s"""case class Foo(foo: Int = 42)
         |
         |Foo() match {
         |  case foo@$CARET
         |}
       """.stripMargin,
    resultText =
      s"""case class Foo(foo: Int = 42)
         |
         |Foo() match {
         |  case foo@Foo(foo)$CARET
         |}
       """.stripMargin
  )

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
    completionType = BASIC
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

  def testList(): Unit = doMatchCompletionTest(
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

  def testTry(): Unit = doMatchCompletionTest(
    fileText =
      s"""import scala.util.Try
         |
         |(_: Try[Any]) ma$CARET
       """.stripMargin,
    resultText =
      s"""import scala.util.{Failure, Success, Try}
         |
         |(_: Try[Any]) match {
         |  case Failure(exception) => $CARET
         |  case Success(value) =>
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

  def testExplicitCompanion(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |object Bar
         |
         |(_: Foo) ma$CARET
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |object Bar
         |
         |(_: Foo) match {
         |  case Bar() => $CARET
         |}
       """.stripMargin
  )

  def testInfixExpression(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) ma$CARET
         |???
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case Bar() => $CARET
         |}
         |???
       """.stripMargin
  )

  //  def testPathDependent(): Unit = doMatchCompletionTest(
  //    fileText =
  //      s"""class Foo {
  //         |  object Bar {
  //         |    sealed trait Baz
  //         |
  //         |    val Impl = new Baz {}
  //         |
  //         |    object BazImpl extends Baz
  //         |
  //         |    case class CaseBaz() extends Baz
  //         |
  //         |    class BazBaz[+T, -U] extends Baz
  //         |
  //         |    object BazBaz {
  //         |      def unapply[T, U](baz: BazBaz[T, U]) = Option(baz)
  //         |    }
  //         |
  //         |    (_: Baz) m$CARET
  //         |  }
  //         |}
  //       """.stripMargin,
  //    resultText =
  //      s"""class Foo {
  //         |  object Bar {
  //         |    sealed trait Baz
  //         |
  //         |    val Impl = new Baz {}
  //         |
  //         |    object BazImpl extends Baz
  //         |
  //         |    case class CaseBaz() extends Baz
  //         |
  //         |    class BazBaz[+T, -U] extends Baz
  //         |
  //         |    object BazBaz {
  //         |      def unapply[T, U](baz: BazBaz[T, U]) = Option(baz)
  //         |    }
  //         |
  //         |    (_: Baz) match {
  //         |      case Bar.BazImpl => $CARET
  //         |      case Bar.CaseBaz() =>
  //         |      case baz: Bar.BazBaz[_, _] =>
  //         |      case _ =>
  //         |    }
  //         |  }
  //         |}
  //       """.stripMargin
  //  )

  def testConcreteClass(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed class Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) ma$CARET
       """.stripMargin,
    resultText =
      s"""sealed class Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case Bar() => $CARET
         |  case _ =>
         |}
       """.stripMargin
  )

  def testAbstractClass(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed abstract class Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) ma$CARET
       """.stripMargin,
    resultText =
      s"""sealed abstract class Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case Bar() => $CARET
         |}
       """.stripMargin
  )

  private def doPatternCompletionTest(fileText: String, resultText: String,
                                      itemText: String = "Foo(_)"): Unit =
    super.doCompletionTest(fileText, resultText, REPLACE_SELECT_CHAR, DEFAULT_TIME, BASIC) {
      hasItemText(_, itemText, itemText, itemTextItalic = true)
    }

  //  private def doMultipleCompletionTest(fileText: String,
  //                                       items: String*): Unit =
  //    super.doMultipleCompletionTest(fileText, BASIC, DEFAULT_TIME, items.size) { lookup =>
  //      items.contains(lookup.getLookupString)
  //    }

  private def doMatchCompletionTest(fileText: String, resultText: String): Unit =
    super.doCompletionTest(fileText, resultText, REPLACE_SELECT_CHAR, DEFAULT_TIME, BASIC)(isExhaustiveMatch)

  private def isExhaustiveMatch(lookup: LookupElement) = {
    import ExhaustiveMatchCompletionContributor.{itemText, rendererTailText}
    hasItemText(lookup, itemText, itemText, tailText = rendererTailText)
  }
}
