package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaClausesCompletionTestBase
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class ScalaClausesCompletionTest extends ScalaClausesCompletionTestBase {

  import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase._
  import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword.{CASE, MATCH}
  import org.jetbrains.plugins.scala.lang.completion.clauses.DirectInheritors.FqnBlockList

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

  def testBeforeCase(): Unit = checkNoBasicCompletion(
    fileText =
      s"""case class Foo()
         |
         |Foo() match {
         |  $CARET
         |}
        """.stripMargin,
    item = "Foo()"
  )

  def testAfterArrow(): Unit = checkNoBasicCompletion(
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

  def testCompleteFirstClauseInPartialFunction(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |case class Bar() extends Foo
         |
         |val collector: PartialFunction[Foo, Unit] = {
         |  ca$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |case class Bar() extends Foo
         |
         |val collector: PartialFunction[Foo, Unit] = {
         |  case Bar() => $CARET
         |}""".stripMargin,
    itemText = "Bar()"
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

  def testCompleteJavaTypeClause(): Unit = {
    configureJavaFile(
      "public interface Foo",
      "Foo"
    )

    doClauseCompletionTest(
      fileText =
        s"""class Bar extends Foo
           |
           |(_: Foo) match {
           |  ca$CARET
           |}""".stripMargin,
      resultText =
        s"""class Bar extends Foo
           |
           |(_: Foo) match {
           |  case bar: Bar => $CARET
           |}""".stripMargin,
      itemText = "_: Bar"
    )
  }

  def testCompleteWithImportsClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""import javax.swing.JComponent
         |
         |(_: JComponent) match {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""import javax.swing.{JComponent, JTree}
         |
         |(_: JComponent) match {
         |  case tree: JTree => $CARET
         |}""".stripMargin,
    itemText = "_: JTree"
  )

  def testCompleteInaccessibleClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private case object Bar extends Foo
         |}
         |
         |(_: Foo) match {
         |  ca$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private case object Bar extends Foo
         |}
         |
         |(_: Foo) match {
         |  case Foo.Bar => $CARET
         |}""".stripMargin,
    itemText = "Bar",
    invocationCount = 2
  )

  def testNoCompleteInaccessibleClause(): Unit = checkNoCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private case object Bar extends Foo
         |}
         |
         |(_: Foo) match {
         |  ca$CARET
         |}""".stripMargin
  )(isCaseClause(_, "Bar"))

  def testNoCompleteClause(): Unit = checkNoCompletion(
    fileText =
      s"""List.empty[String]
         |.zipWithIndex
         |.foreach {
         |  case _ | $CARET
         |}""".stripMargin
  ) {
    case LookupString(string) => string.startsWith(CASE)
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
         |  case FooImpl => $START$CARET???$END
         |  case Bar() => ???
         |  case baz: Baz => ???
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
         |  case FileVisitResult.CONTINUE => $START$CARET???$END
         |  case FileVisitResult.TERMINATE => ???
         |  case FileVisitResult.SKIP_SUBTREE => ???
         |  case FileVisitResult.SKIP_SIBLINGS => ???
         |}
         """.stripMargin
  )

  def testInnerJavaEnum(): Unit = {
    configureJavaFile(
      """
        |public class Scope {
        |  public enum Color {
        |    Red, Green, Blue
        |  }
        |}
        |""".stripMargin,
      "Scope"
    )

    doMatchCompletionTest(
      fileText =
        s"""(_: Scope.Color) m$CARET
         """.stripMargin,
      resultText =
        s"""(_: Scope.Color) match {
           |  case Scope.Color.Red => $START$CARET???$END
           |  case Scope.Color.Green => ???
           |  case Scope.Color.Blue => ???
           |}
         """.stripMargin
    )
  }

  def testInnerJavaEnum2(): Unit = {
    configureJavaFile(
      """public class Scope {
        |  public enum Color {
        |    Red, Green, Blue
        |  }
        |}
        |""".stripMargin,
      "Scope"
    )

    doMatchCompletionTest(
      fileText =
        s"""import Scope.Color
           |
           |(_: Color) m$CARET
           |""".stripMargin,
      resultText =
        s"""import Scope.Color
           |
           |(_: Color) match {
           |  case Scope.Color.Red => $START$CARET???$END
           |  case Scope.Color.Green => ???
           |  case Scope.Color.Blue => ???
           |}
         """.stripMargin
    )
  }

  def testEmptyJavaEnum(): Unit = {
    configureJavaFile(
      "public enum EmptyEnum {}",
      "EmptyEnum"
    )

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
         |  case Margin.TOP => $START$CARET???$END
         |  case Margin.BOTTOM => ???
         |  case Margin.LEFT => ???
         |  case Margin.RIGHT => ???
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
         |  case Margin.Top => $START$CARET???$END
         |  case Margin.Bottom => ???
         |  case Margin.Left => ???
         |  case Margin.Right => ???
         |}
       """.stripMargin
  )

  def testInnerScalaEnumeration(): Unit = doMatchCompletionTest(
    fileText =
      s"""object Scope {
         |  object Margin extends Enumeration {
         |    type Margin = Value
         |
         |    val TOP, BOTTOM = Value
         |    val LEFT, RIGHT = Value
         |
         |    private val NULL = Value
         |  }
         |}
         |
         |(_: Scope.Margin.Margin) m$CARET
       """.stripMargin,
    resultText =
      s"""object Scope {
         |  object Margin extends Enumeration {
         |    type Margin = Value
         |
         |    val TOP, BOTTOM = Value
         |    val LEFT, RIGHT = Value
         |
         |    private val NULL = Value
         |  }
         |}
         |
         |(_: Scope.Margin.Margin) match {
         |  case Scope.Margin.TOP => $START$CARET???$END
         |  case Scope.Margin.BOTTOM => ???
         |  case Scope.Margin.LEFT => ???
         |  case Scope.Margin.RIGHT => ???
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

  def testEmptyScalaEnum2(): Unit = doMatchCompletionTest(
    fileText =
      s"""object Margin extends Enumeration {
         |  type Margin = Value
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
         |  private val NULL = Value
         |}
         |
         |(_: Margin.Margin) match {
         |  case Margin.NULL => $START$CARET???$END
         |}
       """.stripMargin,
    invocationCount = 2
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
         |  case Bar(foos@_*) => $START$CARET???$END
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
         |  case impl: FooImpl => $START$CARET???$END
         |  case _ => ???
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
           |  case Some(value) => $START$CARET???$END
           |  case None        => ???
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
         |  case ::(head, next) => $START$CARET???$END
         |  case Nil => ???
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
         |  case Failure(exception) => $START$CARET???$END
         |  case Success(value) => ???
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
         |  case Bar() => $START$CARET???$END
         |  case _ => ???
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
         |  case Bar() => $START$CARET???$END
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
         |  case Bar() => $START$CARET???$END
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
         |  case Bar() => $START$CARET???$END
         |  case _ => ???
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
         |  case Bar() => $START$CARET???$END
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
           |    case Some(value) => $START$CARET???$END
           |    case None        => ???
           |  }
           |}""".stripMargin
    )
  }

  def testJavaType(): Unit = {
    configureJavaFile(
      fileText =
        """public interface Foo {
          |    public static Foo createFoo() {
          |        return new Foo() {};
          |    }
          |}""".stripMargin,
      className = "Foo"
    )

    doMatchCompletionTest(
      fileText =
        s"""class Bar() extends Foo
           |
           |(_: Foo) ma$CARET""".stripMargin,
      resultText =
        s"""class Bar() extends Foo
           |
           |(_: Foo) match {
           |  case bar: Bar => $START$CARET???$END
           |  case _ => ???
           |}""".stripMargin
    )
  }

  def testCompoundType(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  final case class Baz() extends Foo
         |  trait FooExt extends Foo
         |}
         |
         |import Foo._
         |(if (true) Left(""): Either[String, Bar] else Left(""): Either[String, Baz]) match {
         |  case Left(value) =>
         |  case Right(value) =>
         |    value m$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  final case class Baz() extends Foo
         |  trait FooExt extends Foo
         |}
         |
         |import Foo._
         |(if (true) Left(""): Either[String, Bar] else Left(""): Either[String, Baz]) match {
         |  case Left(value) =>
         |  case Right(value) =>
         |    value match {
         |      case Bar() => $START$CARET???$END
         |      case Baz() => ???
         |    }
         |}""".stripMargin
  )

  def testInaccessibleInheritors(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  private case object Baz extends Foo
         |}
         |
         |(_: Foo) m$CARET""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  private case object Baz extends Foo
         |}
         |
         |(_: Foo) match {
         |  case Foo.Bar() => $START$CARET???$END
         |  case _ => ???
         |}""".stripMargin
  )

  def testInaccessibleInheritors2(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  private case object Baz extends Foo
         |}
         |
         |(_: Foo) m$CARET""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  final case class Bar() extends Foo
         |  private case object Baz extends Foo
         |}
         |
         |(_: Foo) match {
         |  case Foo.Bar() => $START$CARET???$END
         |  case Foo.Baz => ???
         |}""".stripMargin,
    invocationCount = 2
  )

  def testNonSealedInheritorsThreshold(): Unit = checkNoCompletion(
    fileText =
      s"""trait Foo
         |class Bar1 extends Foo
         |class Bar2 extends Foo
         |class Bar3 extends Foo
         |class Bar4 extends Foo
         |class Bar5 extends Foo
         |
         |(_: Foo) ma$CARET""".stripMargin,
  )(isExhaustiveMatch)

  def testCaseInFunction(): Unit = doCaseCompletionTest(
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
         |  case Bar() => $START$CARET???$END
         |}
       """.stripMargin
  )

  def testCaseInPartialFunction(): Unit = doCaseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]).collect {
         |  ca$CARET
         |}
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]).collect {
         |  case Bar() => $START$CARET???$END
         |}
       """.stripMargin
  )

  def testCaseInMatch(): Unit = doCaseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  ca$CARET
         |}
       """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case Bar() => $START$CARET???$END
         |}
       """.stripMargin
  )

  def testNoCaseInFunction(): Unit = checkNoCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]).map {
         |  case _ =>
         |  ca$CARET
         |}
       """.stripMargin,
  )(isExhaustiveCase)

  def testNoCaseInPartialFunction(): Unit = checkNoCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Option[Foo]).collect {
         |  case _ =>
         |  ca$CARET
         |}
       """.stripMargin,
  )(isExhaustiveCase)

  def testNoCaseInMatch(): Unit = checkNoCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case _ =>
         |  ca$CARET
         |}
       """.stripMargin,
  )(isExhaustiveCase)

  def testFqnBlockList(): Unit = for {
    fqn <- FqnBlockList
  } checkNoCompletion(s"(_: $fqn) m$CARET")(isExhaustiveMatch)

  def testQualifiedReference(): Unit = checkNoCompletion(
    fileText =
      s"""sealed trait Foo
         |final case class Bar() extends Foo
         |
         |(_: Foo) match {
         |  case bar: Bar =>
         |    bar.$CARET
         |}""".stripMargin
  ) {
    case LookupString(string) =>
      string.startsWith(MATCH) || string.startsWith(CASE)
  }

  private def withCaseAlignment(doTest: => Unit): Unit = {
    val settings = CodeStyle.getSettings(getProject)
      .getCustomSettings(classOf[org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings])
    val oldValue = settings.ALIGN_IN_COLUMNS_CASE_BRANCH

    try {
      settings.ALIGN_IN_COLUMNS_CASE_BRANCH = true
      doTest
    } finally {
      settings.ALIGN_IN_COLUMNS_CASE_BRANCH = oldValue
    }
  }

  //  private def doMultipleCompletionTest(fileText: String,
  //                                       items: String*): Unit =
  //    super.doMultipleCompletionTest(fileText, BASIC, DEFAULT_TIME, items.size) { lookup =>
  //      items.contains(lookup.getLookupString)
  //    }
}
