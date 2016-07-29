package org.jetbrains.plugins.scala.codeInsight.intentions.imports

import org.jetbrains.plugins.scala.annotator.quickfix.implicits.SearchForImplicitConversionAction
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * Created by Svyatoslav Ilinskiy on 29.07.16.
  */
class SearchForImplicitConversionActionTest extends ScalaIntentionTestBase {
  override def familyName: String = SearchForImplicitConversionAction.Name

  def testSimple(): Unit = {
    val before =
      """
        |import foo.{Bar, Foo}
        |package foo {
        |
        |  package object foo {
        |    implicit def foo2Bar(f: Foo): Bar = new Bar
        |  }
        |
        |  class Foo
        |
        |  class Bar
        |
        |}
        |
        |object Moo {
        |  val f: Foo = ???
        |  val b: Bar = <caret>f
        |}
      """.stripMargin
    val after =
      """
        |import foo.foo.foo2Bar
        |import foo.{Bar, Foo}
        |package foo {
        |
        |  package object foo {
        |    implicit def foo2Bar(f: Foo): Bar = new Bar
        |  }
        |
        |  class Foo
        |
        |  class Bar
        |
        |}
        |
        |object Moo {
        |  val f: Foo = ???
        |  val b: Bar = f
        |}
      """.stripMargin
    doTest(before, after)
  }
}
