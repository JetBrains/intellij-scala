package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ContextFunctionHighlightingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testContextFunctionResolve(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  type Executable[T] = ExecutionContext ?=> T
      |  type Executable2[T] = ContextFunction1[ExecutionContext, T]
      |}
      |""".stripMargin
  )

  def testSimple(): Unit = checkTextHasNoErrors(
    """
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

  def testBuilder(): Unit = checkTextHasNoErrors(
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

  def testPostconditions(): Unit = checkTextHasNoErrors(
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
}
