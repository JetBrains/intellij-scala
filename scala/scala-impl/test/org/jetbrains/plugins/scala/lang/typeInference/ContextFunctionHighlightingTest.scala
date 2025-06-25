package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ContextFunctionHighlightingTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private def doTest(code: String): Unit = checkTextHasNoErrors(code)

  def testContextFunctionResolve(): Unit = doTest(
    """import scala.concurrent.ExecutionContext
      |
      |object A {
      |  type Executable[T] = ExecutionContext ?=> T
      |  type Executable2[T] = ContextFunction1[ExecutionContext, T]
      |}
      |""".stripMargin
  )

  def testSimple(): Unit = doTest(
    """import scala.concurrent.ExecutionContext
      |
      |object A {
      |  type Executable[T] = ExecutionContext ?=> T
      |  def g(arg: Executable[Int]) = ???
      |  def f(x: Int): ExecutionContext ?=> Int = ???
      |  g(22)
      |  g(f(2))
      |  g((ctx: ExecutionContext) ?=> 123)
      |}
      |""".stripMargin
  )

  def testBuilder(): Unit = doTest(
    """
      |import scala.collection.mutable.ArrayBuffer
      |
      |class Table:
      |  val rows = new ArrayBuffer[Row]
      |
      |  def add(r: Row): Unit = rows += r
      |
      |  override def toString = rows.mkString("Table(", ", ", ")")
      |
      |class Row:
      |  val cells = new ArrayBuffer[Cell]
      |
      |  def add(c: Cell): Unit = cells += c
      |
      |  override def toString = cells.mkString("Row(", ", ", ")")
      |
      |case class Cell(elem: String)
      |
      |object A {
      |  def table(init: Table ?=> Unit): Table = {
      |    given t: Table = Table()
      |
      |    init
      |    t
      |  }
      |
      |  def row(init: Row ?=> Unit)(using t: Table) = {
      |    given r: Row = Row()
      |
      |    init
      |    t.add(r)
      |  }
      |
      |  def cell(str: String)(using r: Row) = {
      |    r.add(new Cell(str))
      |  }
      |
      |  table {
      |    row {
      |      cell("top left")
      |      cell("top right")
      |    }
      |    row {
      |      cell("bottom left")
      |      cell("bottom right")
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def testPostconditions(): Unit = doTest(
    """
      |object PostConditions:
      |  opaque type WrappedResult[T] = T
      |
      |  def result[T](using r: WrappedResult[T]): T = r
      |
      |  extension [T](x: T)
      |    def ensuring(condition: WrappedResult[T] ?=> Boolean): T =
      |      assert(condition(using x))
      |      x
      |end PostConditions
      |import PostConditions.{ensuring, result}
      |
      |val s = List(1, 2, 3).sum.ensuring(result == 6)
      |""".stripMargin
  )

  def testSCL20797(): Unit = doTest(
    """
      |object A {
      |  val foo: Ordering[Int] ?=> List[Int] = ???
      |  foo.sorted // valid syntax, highlighting fails
      |}""".stripMargin
  )

  def testSCL23387(): Unit = doTest(
    """
      |object A {
      |  trait Context
      |
      |  extension [From, To](from: From) {
      |    def focus(lambda: (Context ?=> From => To)): To = ???
      |  }
      |
      |  object Test {
      |    def xxxx: Int = ???
      |  }
      |
      |  val z: Int = Test.focus(test => test.xxxx) // does not work
      |  val zzz = Test.focus(_.xxxx) // does not work
      |  val zz = Test.focus(_ ?=> _.xxxx) // works
      |}
      |""".stripMargin
  )

  def testContextFunctionSynthesisNested(): Unit = doTest(
    """
      |object A {
      |  def blub(x: Int ?=> Double ?=> String => Boolean) = ???
      |  blub(s2 => {
      |    implicitly[Int]
      |    implicitly[Double]
      |    false
      |  })
      |
      |  trait Foo
      |  val xxx: Foo ?=> String => Unit = s => {
      |    implicitly[Foo]
      |    ()
      |  }
      |}
      |""".stripMargin
  )

  def testSCL23347(): Unit = doTest(
    """
      |case class Bar(value: Int):
      |  def foo(): Unit = ???
      |  def foo(lambda: (String ?=> Bar => Int)): Unit = ???
      |
      |extension (target: Bar)
      |  def ext(): Unit = ???
      |  def ext(lambda: (String ?=> Bar => Int)): Unit = ???
      |
      |@main def myMain(): Unit =
      |  val bar = Bar(1)
      |
      |  bar.foo(_.value)
      |  bar.foo(b => b.value)
      |
      |  bar.ext(_.value)
      |  bar.ext(b => b.value)
      |""".stripMargin
  )

}
