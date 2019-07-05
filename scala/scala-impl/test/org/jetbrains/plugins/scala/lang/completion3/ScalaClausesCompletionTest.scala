package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.{Lookup, LookupElement}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}

class ScalaClausesCompletionTest extends ScalaCodeInsightTestBase {

  import CompletionType.BASIC
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCodeInsightTestBase._
  import completion.ScalaKeyword.{CASE, MATCH}

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
       """.stripMargin,
    itemText = "Foo(foo)"
  )

  def testInnerSyntheticUnapply(): Unit = doPatternCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  case class Bar(foo: Int = 42) extends Foo
         |}
         |
         |(_: Foo) match {
         |  case Foo.B$CARET
         |}
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  case class Bar(foo: Int = 42) extends Foo
         |}
         |
         |(_: Foo) match {
         |  case Foo.Bar(foo)$CARET
         |}
       """.stripMargin,
    itemText = "Bar(foo)"
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
       """.stripMargin,
    itemText = "Foo(foos@_*)"
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
         |  case Foo(foo)$CARET
         |}
         """.stripMargin,
    itemText = "Foo(foo)"
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

  def testNestedPattern(): Unit = doPatternCompletionTest(
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
    itemText = "Baz(foo)"
  )

  def testCollectPattern(): Unit = doPatternCompletionTest(
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
    itemText = "Foo(foo)"
  )

  def testNamedPattern(): Unit = doPatternCompletionTest(
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
       """.stripMargin,
    itemText = "Foo(foo)"
  )

  def testTuplePattern(): Unit = doPatternCompletionTest(
    fileText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case tuple@$CARET
         |}
       """.stripMargin,
    resultText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case tuple@(str, i)$CARET
         |}
       """.stripMargin,
    itemText = "(str, i)"
  )

  def testCompleteClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |final case class Baz() extends Foo
         |final case class Bar() extends Foo
         |
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |final case class Baz() extends Foo
         |final case class Bar() extends Foo
         |
         |Option.empty[Foo].map {
         |  case Bar() => $CARET
         |}""".stripMargin,
    itemText = "Bar()"
  )

  def testCompleteClauseFormatting(): Unit = withCaseAlignment {
    doClauseCompletionTest(
      fileText =
        s"""sealed trait Foo
           |
           |final case class Bar() extends Foo
           |final case class BarBaz() extends Foo
           |
           |Option.empty[Foo].map {
           |  c$CARET
           |  case Bar() => println( 42 ) // rhs should not to be formatted
           |}""".stripMargin,
      resultText =
        s"""sealed trait Foo
           |
           |final case class Bar() extends Foo
           |final case class BarBaz() extends Foo
           |
           |Option.empty[Foo].map {
           |  case BarBaz() => $CARET
           |  case Bar()    => println( 42 ) // rhs should not to be formatted
           |}""".stripMargin,
      itemText = "BarBaz()"
    )
  }

  def testCompleteObjectClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |final case object Baz extends Foo
         |
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |final case object Baz extends Foo
         |
         |Option.empty[Foo].map {
         |  case Baz => $CARET
         |}""".stripMargin,
    itemText = "Baz"
  )

  def testCompleteNamedClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |final class FooImpl extends Foo
         |
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |final class FooImpl extends Foo
         |
         |Option.empty[Foo].map {
         |  case impl: FooImpl => $CARET
         |}""".stripMargin,
    itemText = "_: FooImpl"
  )

  def testCompleteClauseAdjustment(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  final case class Baz() extends Foo
         |}
         |
         |import Foo.Baz
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  final case class Baz() extends Foo
         |}
         |
         |import Foo.Baz
         |Option.empty[Foo].map {
         |  case Foo.Bar() => $CARET
         |}""".stripMargin,
    itemText = "Bar()"
  )

  def testCompleteClauseAdjustmentWithImport(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  final case class Baz() extends Foo
         |}
         |
         |import Foo.Baz
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  final case class Baz() extends Foo
         |}
         |
         |import Foo.Baz
         |Option.empty[Foo].map {
         |  case Baz() => $CARET
         |}""".stripMargin,
    itemText = "Baz()"
  )

  def testCompleteParameterizedClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |trait FooExt[T] extends Foo
         |
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |trait FooExt[T] extends Foo
         |
         |Option.empty[Foo].map {
         |  case ext: FooExt[_] => $CARET
         |}""".stripMargin,
    itemText = "_: FooExt[_]"
  )

  def testCompleteTupleClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case (str, i) => $CARET
         |}""".stripMargin,
    itemText = "(str, i)"
  )

  def testCompleteClauseBeforeAnother(): Unit = doClauseCompletionTest(
    fileText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach { c$CARET
         |  case _ =>
         |}""".stripMargin,
    resultText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach { case (str, i) => $CARET
         |case _ =>
         |}""".stripMargin,
    itemText = "(str, i)"
  )

  def testCompleteClauseAfterAnother(): Unit = doClauseCompletionTest(
    fileText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case _ => c$CARET
         |}""".stripMargin,
    resultText =
      s"""List
         |.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case _ =>
         |  case (str, i) => $CARET
         |}""".stripMargin,
    itemText = "(str, i)"
  )

  def testCompleteFirstClauseInInfix(): Unit = doClauseCompletionTest(
    fileText =
      s"""Option.empty[(String, String)] foreach {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""Option.empty[(String, String)] foreach {
         |  case (str, str1) => $CARET
         |}""".stripMargin,
    itemText = "(str, str1)"
  )

  def testCompleteSecondClauseInInfix(): Unit = doClauseCompletionTest(
    fileText =
      s"""Option.empty[(String, String)] foreach {
         |  case _ =>
         |    c$CARET
         |}""".stripMargin,
    resultText =
      s"""Option.empty[(String, String)] foreach {
         |  case _ =>
         |  case (str, str1) => $CARET
         |}""".stripMargin,
    itemText = "(str, str1)"
  )

  def testCompleteFirstClauseInMatch(): Unit = doClauseCompletionTest(
    fileText =
      s"""("", "") match {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""("", "") match {
         |  case (str, str1) => $CARET
         |}""".stripMargin,
    itemText = "(str, str1)"
  )

  def testCompleteSecondClauseInMatch(): Unit = doClauseCompletionTest(
    fileText =
      s"""("", "") match {
         |  case _ =>
         |    c$CARET
         |}""".stripMargin,
    resultText =
      s"""("", "") match {
         |  case _ =>
         |  case (str, str1) => $CARET
         |}""".stripMargin,
    itemText = "(str, str1)"
  )

  def testCompleteSingleLineClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""Option.empty[(String, String)].map{c$CARET}""".stripMargin,
    resultText =
      s"""Option.empty[(String, String)].map{ case (str, str1) => $CARET}""".stripMargin,
    itemText = "(str, str1)"
  )

  def testNoCompleteClause(): Unit = checkNoCompletion(
    fileText =
      s"""List.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case _ | $CARET
         |}""".stripMargin
  )(_.getLookupString.startsWith(CASE))

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

  def testEmptyJavaEnum(): Unit = {
    this.configureJavaFile("public enum EmptyEnum {}", "EmptyEnum")
    checkNoCompletion(
      fileText = s"(_: EmptyEnum) m$CARET"
    )(isExhaustiveMatch)
  }

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

  def testEmptyScalaEnum(): Unit = checkNoCompletion(
    fileText =
      s"""object Margin extends Enumeration {
         |  type Margin = Value
         |
         |  private val NULL = Value
         |}
         |
         |(_: Margin.Margin) m$CARET
       """.stripMargin
  )(isExhaustiveMatch)

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

  def testNonSealedClass(): Unit = doMatchCompletionTest(
    fileText =
      s"""trait Foo
         |
         |class FooImpl extends Foo
         |
         |object FooImpl {
         |  def unapply(impl: FooImpl) = Some(impl)
         |}
         |
         |(_: Foo) m$CARET""".stripMargin,
    resultText =
      s"""trait Foo
         |
         |class FooImpl extends Foo
         |
         |object FooImpl {
         |  def unapply(impl: FooImpl) = Some(impl)
         |}
         |
         |(_: Foo) match {
         |  case impl: FooImpl => $CARET
         |  case _ =>
         |}""".stripMargin
  )

  def testMaybe(): Unit = withCaseAlignment {
    doMatchCompletionTest(
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
           |  case None        =>
           |}
       """.stripMargin
    )
  }

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

  def testMatchFormatting(): Unit = withCaseAlignment {
    doMatchCompletionTest(
      fileText =
        s"""def foo(maybeString: Option[String]: Unit = {
           |  maybeString ma$CARET
           |}""".stripMargin,
      resultText =
        s"""def foo(maybeString: Option[String]: Unit = {
           |  maybeString match {
           |    case Some(value) => $CARET
           |    case None        =>
           |  }
           |}""".stripMargin
    )
  }

  def testCase(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]).map {
         |  ca$CARET
         |}
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]).map {
         |  case Bar() => $CARET
         |}
       """.stripMargin
  )(isExhaustiveCase)

  def testMatchCase(): Unit = checkNoCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]) match {
         |  ca$CARET
         |}
       """.stripMargin
  )(isExhaustiveCase)

  private def withCaseAlignment(doTest: => Unit): Unit = {
    val settings = CodeStyle.getSettings(getProject)
      .getCustomSettings(classOf[formatting.settings.ScalaCodeStyleSettings])
    val oldValue = settings.ALIGN_IN_COLUMNS_CASE_BRANCH

    try {
      settings.ALIGN_IN_COLUMNS_CASE_BRANCH = true
      doTest
    } finally {
      settings.ALIGN_IN_COLUMNS_CASE_BRANCH = oldValue
    }
  }

  private def doPatternCompletionTest(fileText: String, resultText: String, itemText: String): Unit =
    doCompletionTest(fileText, resultText) {
      hasItemText(_, itemText)(itemTextItalic = true)
    }

  private def doClauseCompletionTest(fileText: String, resultText: String, itemText: String): Unit =
    doCompletionTest(fileText, resultText) {
      hasItemText(_, CASE + itemText)(
        itemText = CASE,
        itemTextBold = true,
        tailText = " " + itemText
      )
    }

  //  private def doMultipleCompletionTest(fileText: String,
  //                                       items: String*): Unit =
  //    super.doMultipleCompletionTest(fileText, BASIC, DEFAULT_TIME, items.size) { lookup =>
  //      items.contains(lookup.getLookupString)
  //    }

  private def doMatchCompletionTest(fileText: String, resultText: String): Unit =
    doCompletionTest(fileText, resultText)(isExhaustiveMatch)

  private def isExhaustiveMatch(lookup: LookupElement) =
    isExhaustive(lookup, MATCH)

  private def isExhaustive(lookup: LookupElement, lookupString: String) =
    hasItemText(lookup, lookupString)(
      itemTextBold = true,
      tailText = " " + completion.clauses.ExhaustiveMatchCompletionContributor.rendererTailText,
      grayed = true
    )

  private def isExhaustiveCase(lookup: LookupElement) =
    isExhaustive(lookup, CASE)

  private def doCompletionTest(fileText: String, resultText: String)
                              (predicate: LookupElement => Boolean): Unit =
    super.doCompletionTest(fileText, resultText, REPLACE_SELECT_CHAR, DEFAULT_TIME, BASIC)(predicate)

  private def checkNoCompletion(fileText: String)
                               (predicate: LookupElement => Boolean): Unit =
    checkNoCompletion(fileText, BASIC, DEFAULT_TIME)(predicate)
}
