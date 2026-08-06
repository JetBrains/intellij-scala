package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.idea.TestFor
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaClausesCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3ClausesCompletionTest extends ScalaClausesCompletionTestBase {
  override protected def setUp(): Unit = {
    super.setUp()
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  @Test
  def testScala3Enum(): Unit = doMatchCompletionTest(
    fileText =
      s"""enum Direction:
         |  case North, South
         |  case West, East
         |end Direction
         |
         |object O:
         |  (_: Direction) m$CARET
       """.stripMargin,
    resultText =
      s"""enum Direction:
         |  case North, South
         |  case West, East
         |end Direction
         |
         |object O:
         |  (_: Direction) match
         |    case Direction.North => $START$CARET???$END
         |    case Direction.South => ???
         |    case Direction.West => ???
         |    case Direction.East => ???
       """.stripMargin
  )

  @Test
  def testScala3Enum2(): Unit = doMatchCompletionTest(
    fileText =
      s"""enum Json:
         |  case JsString(value: String)
         |  case JsNumber(value: Double)
         |  case JsNull
         |end Json
         |
         |object O:
         |  (_: Json) m$CARET
         |""".stripMargin,
    resultText =
      s"""enum Json:
         |  case JsString(value: String)
         |  case JsNumber(value: Double)
         |  case JsNull
         |end Json
         |
         |object O:
         |  (_: Json) match
         |    case Json.JsString(value) => $START$CARET???$END
         |    case Json.JsNumber(value) => ???
         |    case Json.JsNull => ???
         |""".stripMargin
  )

  @Test
  def testScala3Enum3(): Unit = doMatchCompletionTest(
    fileText =
      s"""trait A
         |trait B
         |
         |enum MyEnum extends A with B:
         |  case Foo
         |  case Bar(value: Int)
         |end MyEnum
         |
         |object O:
         |  (_: MyEnum) m$CARET
         |""".stripMargin,
    resultText =
      s"""trait A
         |trait B
         |
         |enum MyEnum extends A with B:
         |  case Foo
         |  case Bar(value: Int)
         |end MyEnum
         |
         |object O:
         |  (_: MyEnum) match
         |    case MyEnum.Foo => $START$CARET???$END
         |    case MyEnum.Bar(value) => ???
         |""".stripMargin
  )

  @Test
  def testInnerScala3Enum(): Unit = doMatchCompletionTest(
    fileText =
      s"""object Scope:
         |  enum Direction:
         |    case North, South
         |    case West, East
         |end Scope
         |
         |object O:
         |  (_: Scope.Direction) m$CARET
       """.stripMargin,
    resultText =
      s"""import Scope.Direction
         |
         |object Scope:
         |  enum Direction:
         |    case North, South
         |    case West, East
         |end Scope
         |
         |object O:
         |  (_: Scope.Direction) match
         |    case Direction.North => $START$CARET???$END
         |    case Direction.South => ???
         |    case Direction.West => ???
         |    case Direction.East => ???
       """.stripMargin
  )

  @Test
  def testScala3EnumBetweenMethods(): Unit = doMatchCompletionTest(
    fileText =
      s"""
        |enum Tree[+A]:
        |  case Leaf(value: A)
        |  case Branch(left: Tree[A], right: Tree[A])
        |
        |  def size: Int = this match
        |    case Leaf(_) => 1
        |    case Branch(l, r) => 1 + l.size + r.size
        |
        |  def depth: Int = ???
        |
        |  this ma$CARET
        |
        |  def map[B](f: A => B): Tree[B] = ???
        |
        |  def fold[B](f: A => B, g: (B, B) => B): B = ???
        |""".stripMargin,
    resultText =
      s"""
        |enum Tree[+A]:
        |  case Leaf(value: A)
        |  case Branch(left: Tree[A], right: Tree[A])
        |
        |  def size: Int = this match
        |    case Leaf(_) => 1
        |    case Branch(l, r) => 1 + l.size + r.size
        |
        |  def depth: Int = ???
        |
        |  this match
        |    case Tree.Leaf(value) => $START$CARET???$END
        |    case Tree.Branch(left, right) => ???
        |
        |  def map[B](f: A => B): Tree[B] = ???
        |
        |  def fold[B](f: A => B, g: (B, B) => B): B = ???
        |""".stripMargin,
  )

  @Test
  def testScala3EnumInsideMethodBodyBetweenMethods(): Unit = doMatchCompletionTest(
    fileText =
      s"""
         |enum Tree[+A]:
         |  case Leaf(value: A)
         |  case Branch(left: Tree[A], right: Tree[A])
         |
         |  def size: Int = this match
         |    case Leaf(_) => 1
         |    case Branch(l, r) => 1 + l.size + r.size
         |
         |  def depth: Int = ???
         |
         |  def foo = this ma$CARET
         |
         |  def map[B](f: A => B): Tree[B] = ???
         |
         |  def fold[B](f: A => B, g: (B, B) => B): B = ???
         |""".stripMargin,
    resultText =
      s"""
         |enum Tree[+A]:
         |  case Leaf(value: A)
         |  case Branch(left: Tree[A], right: Tree[A])
         |
         |  def size: Int = this match
         |    case Leaf(_) => 1
         |    case Branch(l, r) => 1 + l.size + r.size
         |
         |  def depth: Int = ???
         |
         |  def foo = this match
         |    case Tree.Leaf(value) => $START$CARET???$END
         |    case Tree.Branch(left, right) => ???
         |
         |  def map[B](f: A => B): Tree[B] = ???
         |
         |  def fold[B](f: A => B, g: (B, B) => B): B = ???
         |""".stripMargin,
  )

  @Test
  @TestFor(issues = Array("SCL-24607"))
  def testCompleteClauseOperatorNameWithUsingClause(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |final case class @@(using value: String, tag: Int) extends Foo
         |
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |final case class @@(using value: String, tag: Int) extends Foo
         |
         |Option.empty[Foo].map {
         |  case @@() => $CARET
         |}""".stripMargin,
    itemText = "@@()"
  )

  @Test
  @TestFor(issues = Array("SCL-24607"))
  def testCompleteClauseOperatorNameWithUsingClause2(): Unit = doClauseCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |final case class @@(flag: Boolean, count: Long)(using value: String, tag: Int) extends Foo
         |
         |Option.empty[Foo].map {
         |  c$CARET
         |}""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |final case class @@(flag: Boolean, count: Long)(using value: String, tag: Int) extends Foo
         |
         |Option.empty[Foo].map {
         |  case @@(flag, count) => $CARET
         |}""".stripMargin,
    itemText = "@@(flag, count)"
  )

  @Test
  @TestFor(issues = Array("SCL-24607", "SCL-24609"))
  def testSealedTraitWithOperatorNames(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait MyMarker
         |
         |case class Node(left: Int, right: Node) extends MyMarker
         |infix case class NodeInfix(left: Int, right: Node) extends MyMarker
         |case class :=[K, V](key: K, value: V) extends MyMarker
         |case class -->(from: Int, to: Int) extends MyMarker
         |case class @@[A, T](value: A, tag: T) extends MyMarker
         |case class +:[A](head: A, tail: List[A]) extends MyMarker
         |case class @:@[A, T]() extends MyMarker
         |
         |object Foo:
         |  def test(x: MyMarker): Unit =
         |    x $CARET
         """.stripMargin,
    resultText =
      s"""sealed trait MyMarker
         |
         |case class Node(left: Int, right: Node) extends MyMarker
         |infix case class NodeInfix(left: Int, right: Node) extends MyMarker
         |case class :=[K, V](key: K, value: V) extends MyMarker
         |case class -->(from: Int, to: Int) extends MyMarker
         |case class @@[A, T](value: A, tag: T) extends MyMarker
         |case class +:[A](head: A, tail: List[A]) extends MyMarker
         |case class @:@[A, T]() extends MyMarker
         |
         |object Foo:
         |  def test(x: MyMarker): Unit =
         |    x match
         |      case Node(left, right) => $START$CARET???$END
         |      case left NodeInfix right => ???
         |      case key := value => ???
         |      case from --> to => ???
         |      case value @@ tag => ???
         |      case head +: tail => ???
         |      case @:@() => ???
         """.stripMargin
  )
}
