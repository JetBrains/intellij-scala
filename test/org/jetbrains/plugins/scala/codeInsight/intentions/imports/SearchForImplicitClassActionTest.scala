package org.jetbrains.plugins.scala.codeInsight.intentions.imports

import org.jetbrains.plugins.scala.annotator.quickfix.implicits.SearchForImplicitClassAction
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * Created by Svyatoslav Ilinskiy on 26.07.16.
  */
class SearchForImplicitClassActionTest extends ScalaIntentionTestBase {
  override def familyName: String = SearchForImplicitClassAction.Name

  def testBasicImplicitClass(): Unit = {
    val before =
      """
        |import boo.Bar
        |package boo {
        |
        |  object AAA {
        |
        |    implicit class MooStringExt(val s: Bar) extends AnyVal {
        |      def foo(): Unit = println(s)
        |    }
        |
        |  }
        |
        |  class Bar
        |
        |}
        |
        |object Moo {
        |  def foo(b: Bar): Unit = {
        |    b.f<caret>oo()
        |  }
        |}
      """.stripMargin
    val after =
      """
        |import boo.AAA.MooStringExt
        |import boo.Bar
        |package boo {
        |
        |  object AAA {
        |
        |    implicit class MooStringExt(val s: Bar) extends AnyVal {
        |      def foo(): Unit = println(s)
        |    }
        |
        |  }
        |
        |  class Bar
        |
        |}
        |
        |object Moo {
        |  def foo(b: Bar): Unit = {
        |    b.f<caret>oo()
        |  }
        |}
      """.stripMargin
    doTest(before, after)
  }

  def testImportFromTrait(): Unit = {
    val before =
      """
        |import boo.Bar
        |
        |package boo {
        |
        |  object AAA {
        |
        |    trait ExtTrait {
        |      protected def repr: String
        |
        |      def foo(): Unit = println(repr)
        |    }
        |
        |    implicit class MooStringExt(val repr: Bar) extends AnyVal with ExtTrait {
        |      def bar(): Unit = {
        |
        |      }
        |    }
        |
        |  }
        |
        |  class Bar
        |
        |}
        |
        |object Moo {
        |  val b = new Bar
        |
        |  b.f<caret>oo()
        |}
      """.stripMargin
    val after =
      """
        |import boo.AAA.MooStringExt
        |import boo.Bar
        |
        |package boo {
        |
        |  object AAA {
        |
        |    trait ExtTrait {
        |      protected def repr: String
        |
        |      def foo(): Unit = println(repr)
        |    }
        |
        |    implicit class MooStringExt(val repr: Bar) extends AnyVal with ExtTrait {
        |      def bar(): Unit = {
        |
        |      }
        |    }
        |
        |  }
        |
        |  class Bar
        |
        |}
        |
        |object Moo {
        |  val b = new Bar
        |
        |  b.foo()
        |}
      """.stripMargin
    doTest(before, after)
  }
}
