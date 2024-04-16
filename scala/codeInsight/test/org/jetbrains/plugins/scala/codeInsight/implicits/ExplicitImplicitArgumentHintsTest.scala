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
}