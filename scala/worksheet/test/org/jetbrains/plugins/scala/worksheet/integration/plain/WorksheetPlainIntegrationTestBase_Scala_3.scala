package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.junit.Test

trait WorksheetPlainIntegrationTestBase_Scala_3_AllInOne { self: PlainWorksheetTestBase =>
  // TODO: it flickers in WorksheetPlainCompileOnServerRunLocallyIntegrationTest, but works fine in prod
  @Test
  def testScala3_AllInOne(): Unit = {
    val before =
      """import java.io.PrintStream
        |import scala.concurrent.duration._;
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |val q1 = new DurationInt(3)
        |var q2 = new DurationInt(4)
        |
        |def f = 11
        |var _ = 5
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B
        |
        |enum ListEnum[+A] {
        |  case Cons(h: A, t: ListEnum[A])
        |  case Empty
        |}
        |
        |println(ListEnum.Empty)
        |println(ListEnum.Cons(42, ListEnum.Empty))""".stripMargin
    val after =
      s"""
         |
         |
         |
         |${foldStart}List(1, 2, 3)
         |val res0: Unit = ()$foldEnd
         |${foldStart}1
         |val res1: Unit = ()${foldEnd}
         |
         |val res2: Unit = ()
         |val res3: Int = 23
         |val res4: String = str
         |
         |def foo: String
         |def foo0: Int
         |def foo1(): Int
         |def foo2: Int
         |def foo3(): Int
         |def foo4(p: String): Int
         |def foo5(p: String): Int
         |def foo6(p: String, q: Short): Int
         |def foo7[T]: Int
         |def foo8[T](): Int
         |def foo9[T]: Int
         |def foo10[T](): Int
         |def foo11[T](p: String): Int
         |def foo12[T](p: String): Int
         |def foo13[T](p: String, q: Short): Int
         |
         |
         |val x: Int = 2
         |val y: String = 21231
         |val x2: java.io.PrintStream = null
         |val q1: scala.concurrent.duration.package.DurationInt = scala.concurrent.duration.package$$DurationInt@3
         |var q2: scala.concurrent.duration.package.DurationInt = scala.concurrent.duration.package$$DurationInt@4
         |
         |def f: Int
         |
         |var v1: Int = 6
         |var v2: Int = 17
         |v2: Int = 6
         |
         |// defined class A
         |// defined trait B
         |// defined object B
         |
         |// defined enum ListEnum
         |
         |
         |
         |
         |${foldStart}Empty
         |val res5: Unit = ()${foldEnd}
         |${foldStart}Cons(42,Empty)
         |val res6: Unit = ()${foldEnd}""".stripMargin
    doRenderTest(before, after)
  }
}

trait WorksheetPlainIntegrationTestBase_Scala_3_4_to_3_7_AllInOne { self: PlainWorksheetTestBase =>
  @Test
  def testScala3_AllInOne(): Unit = {
    val before =
      """import java.io.PrintStream
        |import scala.concurrent.duration._;
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |val q1 = new DurationInt(3)
        |var q2 = new DurationInt(4)
        |
        |def f = 11
        |var _ = 5
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B
        |
        |enum ListEnum[+A] {
        |  case Cons(h: A, t: ListEnum[A])
        |  case Empty
        |}
        |
        |println(ListEnum.Empty)
        |println(ListEnum.Cons(42, ListEnum.Empty))""".stripMargin
    val after =
      s"""
         |
         |
         |
         |${foldStart}List(1, 2, 3)
         |val res0: Unit = ()$foldEnd
         |${foldStart}1
         |val res1: Unit = ()${foldEnd}
         |
         |val res2: Unit = ()
         |val res3: Int = 23
         |val res4: String = str
         |
         |def foo: String
         |def foo0: Int
         |def foo1(): Int
         |def foo2: Int
         |def foo3(): Int
         |def foo4(p: String): Int
         |def foo5(p: String): Int
         |def foo6(p: String, q: Short): Int
         |def foo7[T]: Int
         |def foo8[T](): Int
         |def foo9[T]: Int
         |def foo10[T](): Int
         |def foo11[T](p: String): Int
         |def foo12[T](p: String): Int
         |def foo13[T](p: String, q: Short): Int
         |
         |
         |val x: Int = 2
         |val y: String = 21231
         |val x2: java.io.PrintStream = null
         |val q1: scala.concurrent.duration.DurationInt = scala.concurrent.duration.package$$DurationInt@3
         |var q2: scala.concurrent.duration.DurationInt = scala.concurrent.duration.package$$DurationInt@4
         |
         |def f: Int
         |
         |var v1: Int = 6
         |var v2: Int = 17
         |v2: Int = 6
         |
         |// defined class A
         |// defined trait B
         |// defined object B
         |
         |// defined enum ListEnum
         |
         |
         |
         |
         |${foldStart}Empty
         |val res5: Unit = ()${foldEnd}
         |${foldStart}Cons(42,Empty)
         |val res6: Unit = ()${foldEnd}""".stripMargin
    doRenderTest(before, after)
  }
}

trait WorksheetPlainIntegrationTestBase_Scala_3_RC_AllInOne { self: PlainWorksheetTestBase =>
  @Test
  def testScala3_Next_RC_AllInOne(): Unit = {
    val before =
      """import java.io.PrintStream
        |import scala.concurrent.duration._;
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |val q1 = new DurationInt(3)
        |var q2 = new DurationInt(4)
        |
        |def f = 11
        |var _ = 5
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B
        |
        |enum ListEnum[+A] {
        |  case Cons(h: A, t: ListEnum[A])
        |  case Empty
        |}
        |
        |println(ListEnum.Empty)
        |println(ListEnum.Cons(42, ListEnum.Empty))""".stripMargin
    val after =
      s"""
         |
         |
         |
         |${foldStart}List(1, 2, 3)
         |val res0: Unit = ()$foldEnd
         |${foldStart}1
         |val res1: Unit = ()${foldEnd}
         |
         |val res2: Unit = ()
         |val res3: Int = 23
         |val res4: String = str
         |
         |def foo: String
         |def foo0: Int
         |def foo1(): Int
         |def foo2: Int
         |def foo3(): Int
         |def foo4(p: String): Int
         |def foo5(p: String): Int
         |def foo6(p: String, q: Short): Int
         |def foo7[T]: Int
         |def foo8[T](): Int
         |def foo9[T]: Int
         |def foo10[T](): Int
         |def foo11[T](p: String): Int
         |def foo12[T](p: String): Int
         |def foo13[T](p: String, q: Short): Int
         |
         |
         |val x: Int = 2
         |val y: String = 21231
         |val x2: PrintStream = null
         |val q1: DurationInt = scala.concurrent.duration.package$$DurationInt@3
         |var q2: DurationInt = scala.concurrent.duration.package$$DurationInt@4
         |
         |def f: Int
         |
         |var v1: Int = 6
         |var v2: Int = 17
         |v2: Int = 6
         |
         |// defined class A
         |// defined trait B
         |// defined object B
         |
         |// defined enum ListEnum
         |
         |
         |
         |
         |${foldStart}Empty
         |val res5: Unit = ()${foldEnd}
         |${foldStart}Cons(42,Empty)
         |val res6: Unit = ()${foldEnd}""".stripMargin
    doRenderTest(before, after)
  }
}

trait WorksheetPlainIntegrationTestBase_Scala_3_BracelessSyntax { self: PlainWorksheetTestBase =>
  @Test
  def testScala3_WithBracelessSyntax(): Unit = {
    val before =
      """def foo42(x: Int) =
        |  val y = x + 1
        |  y + 1
        |
        |class A(x: Int):
        |  val a = x + 2
        |  def method =
        |    val b = a + 2
        |    b
        |
        |foo42(1)
        |
        |A(1).method
        |""".stripMargin
    val after =
      s"""def foo42(x: Int): Int
         |
         |
         |
         |// defined class A
         |
         |
         |
         |
         |
         |val res0: Int = 3
         |
         |val res1: Int = 5""".stripMargin
    doRenderTest(before, after)
  }
}
