package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.LatestScalaVersions.Scala_2_13
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class UndoingEtaExpansionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  def testSimple(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: String => Int): Unit = ()
      |def foo(f: Int => Unit): Int    = 42
      |def bar(x: Int): Unit           = ???
      |
      |foo(x => bar(x))
      |foo(bar _)
      |foo(bar(_))
      |""".stripMargin
  )

  def testWithBlock(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: String => Int): Unit = ()
      |def foo(f: Int => Unit): Int    = 42
      |def bar(x: Int): Unit           = ???
      |
      |foo(x => { bar(x) })
      |""".stripMargin
  )

  def testHigherArity(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: (Int, String) => String): Int       = 123
      |def foo(g: (Double, Double) => Double): Double = 2d
      |def bar(i: Int, s: String): String             = "456"
      |
      |foo(bar(_, _))
      |foo(bar _)
      |foo((x, y) => bar(x, y))
      |foo((x, y) => { bar(x, y) })
      |""".stripMargin
  )

  def testSCL17899(): Unit = checkTextHasNoErrors(
    """
      |def foo(f: Int => Int): Int = 1
      |def foo(f: String => String): String = "2"
      |def bar(s: String, s2: Int): String = "3"
      |def bar2(s: String): String = "3"
      |
      |foo(a => bar(a, a))
      |foo(a => bar2(a))
      |foo(bar2(_))
      |""".stripMargin
  )

  def testSCL17899_2(): Unit = checkTextHasNoErrors(
    """
      |trait Command
      |trait User
      |case class CreateUser(user: User, value: ARef[User]) extends Command
      |
      |trait ARef[A] {
      |  def ask[Res](replyTo: ARef[Res] => A): Res = ???
      |}
      |
      |def createUser(user: User): User = {
      |  val ref: ARef[Command] = ???
      |  ref.ask(a => CreateUser(user, a))
      |  ref.ask(CreateUser(user, _))
      |  ???
      |}""".stripMargin
  )
}
