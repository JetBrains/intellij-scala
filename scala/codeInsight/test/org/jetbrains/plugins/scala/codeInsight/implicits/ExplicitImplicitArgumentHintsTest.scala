package org.jetbrains.plugins.scala.codeInsight.implicits
import org.jetbrains.plugins.scala.ScalaVersion

class ExplicitImplicitArgumentHintsTest extends ImplicitHintsTestBase {
  import Hint.{End => E, Start => S}

  def testSimpleImplicitArgument(): Unit = doTest(
    s"""
       |def fun()(implicit a: Int): Unit = ???
       |
       |fun()(${S}using${E}1)
       |fun(){${S}using$E 2 }
     """.stripMargin
  )
}

class ExplcitImplcitArgumentHintsTest_Scala3 extends ImplicitHintsTestBase {
  import Hint.{End => E, Start => S}

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def testSimpleImplicitArgument(): Unit = doTest(
    s"""
       |def fun()(implicit a: Int): Unit = ???
       |
       |fun()(using 2)
       |fun()(${S}using${E}1)
       |fun(){${S}using$E 2 }
       |fun():${S}using$E
       |  1
     """.stripMargin
  )

  def testExplicitImplicitClauseAfterInterleavedTypeClauses(): Unit = doTest(
    s"""
       |def fun[A](a: A)[B](b: B)(implicit c: String): Unit = ()
       |
       |fun(1)(true)(${S}using${E}"context")
       |""".stripMargin
  )

  def testConstructorExplicitImplicitClause(): Unit = doTest(
    s"""
       |class Example(a: Int)(implicit b: String)
       |
       |new Example(1)(${S}using${E}"context")
       |""".stripMargin
  )
}
