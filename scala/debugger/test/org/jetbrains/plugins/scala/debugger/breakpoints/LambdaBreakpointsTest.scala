package org.jetbrains.plugins.scala
package debugger
package breakpoints

class LambdaBreakpointsTest_2_11 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class LambdaBreakpointsTest_2_12 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  override def testLambdaInClassConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "$anonfun$new$1"), (4, "$anonfun$new$1"), (4, "$anonfun$new$1"))
  }

  override def testLambdaInObjectConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "$anonfun$new$1"), (4, "$anonfun$new$1"), (4, "$anonfun$new$1"))
  }

  override def testLambdaInNestedObjectStatic(): Unit = {
    breakpointsTest()((11, "main"), (5, "$anonfun$method$1"), (5, "$anonfun$method$1"), (5, "$anonfun$method$1"))
  }

  override def testLambdaInNestedClassStatic(): Unit = {
    breakpointsTest()((11, "main"), (5, "$anonfun$method$1"), (5, "$anonfun$method$1"), (5, "$anonfun$method$1"))
  }

  override def testLambdaInNestedObject(): Unit = {
    breakpointsTest()((15, "main"), (8, "$anonfun$method$1"), (8, "$anonfun$method$1"), (8, "$anonfun$method$1"))
  }

  override def testLambdaInNestedClass(): Unit = {
    breakpointsTest()((15, "main"), (8, "$anonfun$method$1"), (8, "$anonfun$method$1"), (8, "$anonfun$method$1"))
  }

  override def testLambdaInLocalMethod(): Unit = {
    breakpointsTest()(
      (21, "main"),
      (8, "$anonfun$create$1"), (9, "$anonfun$create$1"), (10, "$anonfun$create$1"), (11, "$anonfun$create$1"),
      (8, "$anonfun$create$1"), (9, "$anonfun$create$1"), (10, "$anonfun$create$1"), (11, "$anonfun$create$1"),
      (8, "$anonfun$create$1"), (9, "$anonfun$create$1"), (10, "$anonfun$create$1"), (11, "$anonfun$create$1"),
    )
  }

  override def testLambdaInGuard(): Unit = {
    breakpointsTest()(
      (5, "main"),
      (5, "$anonfun$main$1"), (5, "$anonfun$main$2"),
      (5, "$anonfun$main$1"), (5, "$anonfun$main$2"), (6, "$anonfun$main$3"),
      (5, "$anonfun$main$1"), (5, "$anonfun$main$2"), (6, "$anonfun$main$3")
    )
  }

  override def testVariousLambdas(): Unit = {
    breakpointsTest()(
      (3, "<init>"),
      (7, "main"), (3, "$anonfun$plusTwo$1"),
      (8, "main"), (8, "$anonfun$main$1"),
      (9, "main"), (9, "$anonfun$main$2"),
      (10, "main"), (10, "$anonfun$main$3"),
      (11, "main"), (11, "$anonfun$main$4"),
      (12, "main"), (12, "$anonfun$main$5"),
      (14, "main"), (14, "$anonfun$main$6"),
      (16, "main"), (16, "$anonfun$main$7"),
      (18, "main"), (18, "$anonfun$main$8"),
      (20, "$anonfun$main$9")
    )
  }

  override def testByNameInExtensionMethod(): Unit = {
    breakpointsTest()(
      (4, "doubleIt$extension"),
      (4, "$anonfun$doubleIt$extension$1")
    )
  }

  override def testLambdaBreakpointsInsideTrait(): Unit = {
    breakpointsTest()(
      (22, "$anonfun$fooFromTrait$1"), (23, "$anonfun$fooFromTrait$1"), (22, "$anonfun$fooFromTrait$1"), (23, "$anonfun$fooFromTrait$1"), (25, "fooFromTrait"),
      (33, "$anonfun$fooFromAbstractClass$1"), (34, "$anonfun$fooFromAbstractClass$1"), (33, "$anonfun$fooFromAbstractClass$1"), (34, "$anonfun$fooFromAbstractClass$1"), (36, "fooFromAbstractClass"),
      (11, "$anonfun$fooFromMain$1"), (12, "$anonfun$fooFromMain$1"), (11, "$anonfun$fooFromMain$1"), (12, "$anonfun$fooFromMain$1"), (14, "fooFromMain"),
    )
  }

  override def testOneLineLambdaInTrait(): Unit = {
    breakpointsTest()(
      (4, "$anonfun$fooInTrait$1"), (4, "$anonfun$fooInTrait$1"), (5, "fooInTrait")
    )
  }

  override def testLineInLambdaWithLambdaOnLineInTrait(): Unit = {
    breakpointsTest()(
      (5, "$anonfun$fooInTrait$1"), (6, "$anonfun$fooInTrait$1"), (6, "$anonfun$fooInTrait$2"),
      (5, "$anonfun$fooInTrait$1"), (6, "$anonfun$fooInTrait$1"), (6, "$anonfun$fooInTrait$2"),
      (8, "fooInTrait")
    )
  }
}

class LambdaBreakpointsTest_2_13 extends LambdaBreakpointsTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override def testVariousLambdas(): Unit = {
    breakpointsTest()(
      (3, "<clinit>"),
      (7, "main"), (3, "$anonfun$plusTwo$1"),
      (8, "main"), (8, "$anonfun$main$1"),
      (9, "main"), (9, "$anonfun$main$2"),
      (10, "main"), (10, "$anonfun$main$3"),
      (11, "main"), (11, "$anonfun$main$4"),
      (12, "main"), (12, "$anonfun$main$5"),
      (14, "main"), (14, "$anonfun$main$6"),
      (16, "main"), (16, "$anonfun$main$7"),
      (18, "main"), (18, "$anonfun$main$8"),
      (20, "$anonfun$main$9")
    )
  }
}

class LambdaBreakpointsTest_3_3 extends LambdaBreakpointsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_3

  override def testLambdaInClassConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"))
  }

  override def testLambdaInObjectConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"))
  }

  override def testLambdaInNestedObjectStatic(): Unit = {
    breakpointsTest()(
      (11, "main"),
      (5, "LambdaInNestedObjectStatic$Outer$Inner$$$_$method$$anonfun$1"),
      (5, "LambdaInNestedObjectStatic$Outer$Inner$$$_$method$$anonfun$1"),
      (5, "LambdaInNestedObjectStatic$Outer$Inner$$$_$method$$anonfun$1")
    )
  }

  override def testLambdaInNestedClassStatic(): Unit = {
    breakpointsTest()(
      (11, "main"),
      (5, "LambdaInNestedClassStatic$Outer$Inner$$_$method$$anonfun$1"),
      (5, "LambdaInNestedClassStatic$Outer$Inner$$_$method$$anonfun$1"),
      (5, "LambdaInNestedClassStatic$Outer$Inner$$_$method$$anonfun$1")
    )
  }

  override def testLambdaInNestedObject(): Unit = {
    breakpointsTest()(
      (15, "main"),
      (8, "LambdaInNestedObject$Outer$Inner$$$_$method$$anonfun$1"),
      (8, "LambdaInNestedObject$Outer$Inner$$$_$method$$anonfun$1"),
      (8, "LambdaInNestedObject$Outer$Inner$$$_$method$$anonfun$1")
    )
  }

  override def testLambdaInNestedClass(): Unit = {
    breakpointsTest()(
      (15, "main"),
      (8, "LambdaInNestedClass$Outer$Inner$$_$method$$anonfun$1"),
      (8, "LambdaInNestedClass$Outer$Inner$$_$method$$anonfun$1"),
      (8, "LambdaInNestedClass$Outer$Inner$$_$method$$anonfun$1")
    )
  }

  addSourceFile("LambdaInExtension.scala",
    s"""
       |object LambdaInExtension:
       |  extension (n: Int) def blah(): Unit =
       |    (0 until n).foreach { x =>
       |      println(s"blah $$x") $breakpoint
       |    }
       |
       |  def main(args: Array[String]): Unit =
       |    3.blah() $breakpoint
       |""".stripMargin)

  def testLambdaInExtension(): Unit = {
    breakpointsTest()((8, "main"), (4, "blah$$anonfun$1"), (4, "blah$$anonfun$1"), (4, "blah$$anonfun$1"))
  }

  addSourceFile("MainAnnotation.scala",
    s"""
       |@main def multipleBreakpoints(): Unit = {
       |  def foo(o: Any): Any = {
       |    o match {
       |      case s: String if s.nonEmpty => "string" $breakpoint
       |      case _ => "not string"
       |    }
       |  }
       |
       |  foo("abc")
       |}
       |""".stripMargin)

  def testMainAnnotation(): Unit = {
    breakpointsTest("multipleBreakpoints")((4, "foo$1"))
  }

  override def testLambdaInLocalMethod(): Unit = {
    breakpointsTest()(
      (21, "main"),
      (8, "func$1$$anonfun$1"), (9, "func$1$$anonfun$1"), (10, "func$1$$anonfun$1"), (11, "func$1$$anonfun$1"),
      (8, "func$1$$anonfun$1"), (9, "func$1$$anonfun$1"), (10, "func$1$$anonfun$1"), (11, "func$1$$anonfun$1"),
      (8, "func$1$$anonfun$1"), (9, "func$1$$anonfun$1"), (10, "func$1$$anonfun$1"), (11, "func$1$$anonfun$1"),
    )
  }

  override def testLambdaInGuard(): Unit = {
    breakpointsTest()(
      (5, "main"),
      (5, "$anonfun$1"), (5, "$anonfun$1$$anonfun$1"),
      (5, "$anonfun$1"), (5, "$anonfun$1$$anonfun$1"), (6, "$anonfun$2"),
      (5, "$anonfun$1"), (5, "$anonfun$1$$anonfun$1"), (6, "$anonfun$2")
    )
  }

  addSourceFile("LambdaInToplevelMain.scala",
    s"""
       |@main
       |def lambdaInToplevelMain(): Unit =
       |  for (i <- 1 to 5) do
       |    println(i) $breakpoint
       |    println(i)
       |""".stripMargin)

  def testLambdaInToplevelMain(): Unit = {
    breakpointsTest("lambdaInToplevelMain")(
      (4, "lambdaInToplevelMain$$anonfun$1"),
      (4, "lambdaInToplevelMain$$anonfun$1"),
      (4, "lambdaInToplevelMain$$anonfun$1"),
      (4, "lambdaInToplevelMain$$anonfun$1"),
      (4, "lambdaInToplevelMain$$anonfun$1")
    )
  }

  override def testVariousLambdas(): Unit = {
    breakpointsTest()(
      (3, "<clinit>"),
      (7, "main"), (3, "$init$$$anonfun$1"),
      (8, "main"), (8, "main$$anonfun$1"),
      (9, "main"), (9, "main$$anonfun$2"),
      (10, "main"), (10, "main$$anonfun$3"),
      (11, "main"), (11, "main$$anonfun$4"),
      (12, "main"), (12, "main$$anonfun$5"),
      (14, "main"), (14, "main$$anonfun$6"),
      (16, "main"), (16, "main$$anonfun$7"),
      (18, "main"), (18, "main$$anonfun$8"),
      (20, "main$$anonfun$9")
    )
  }

  override def testByNameInExtensionMethod(): Unit = {
    breakpointsTest()(
      (4, "doubleIt$extension"),
      (4, "ByNameInExtensionMethod$IntOpt$$$_$doubleIt$extension$$anonfun$1")
    )
  }

  override def testLambdaBreakpointsInsideTrait(): Unit = {
    breakpointsTest()(
      (22, "$anonfun$2"), (23, "$anonfun$2"), (22, "$anonfun$2"), (23, "$anonfun$2"), (25, "fooFromTrait"),
      (33, "$anonfun$3"), (34, "$anonfun$3"), (33, "$anonfun$3"), (34, "$anonfun$3"), (36, "fooFromAbstractClass"),
      (11, "$anonfun$1"), (12, "$anonfun$1"), (11, "$anonfun$1"), (12, "$anonfun$1"), (14, "fooFromMain"),
    )
  }

  override def testOneLineLambdaInTrait(): Unit = {
    breakpointsTest()(
      (4, "$anonfun$1"), (4, "$anonfun$1"), (5, "fooInTrait")
    )
  }

  override def testLineInLambdaWithLambdaOnLineInTrait(): Unit = {
    breakpointsTest()(
      (5, "$anonfun$1"), (6, "$anonfun$1"), (6, "$anonfun$1$$anonfun$1"),
      (5, "$anonfun$1"), (6, "$anonfun$1"), (6, "$anonfun$1$$anonfun$1"),
      (8, "fooInTrait")
    )
  }
}

class LambdaBreakpointsTest_3_4 extends LambdaBreakpointsTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4

  override def testLambdaInNestedClass(): Unit = {
    breakpointsTest()(
      (15, "main"),
      (8, "method"),
      (8, "LambdaInNestedClass$Outer$Inner$$_$method$$anonfun$1"),
      (8, "LambdaInNestedClass$Outer$Inner$$_$method$$anonfun$1"),
      (8, "LambdaInNestedClass$Outer$Inner$$_$method$$anonfun$1")
    )
  }

  override def testLambdaInExtension(): Unit = {
    breakpointsTest()((8, "main"), (4, "blah"), (4, "blah$$anonfun$1"), (4, "blah$$anonfun$1"), (4, "blah$$anonfun$1"))
  }

  override def testLambdaInClassConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "<init>"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"))
  }

  override def testLambdaInGuard(): Unit = {
    breakpointsTest()(
      (5, "main"), (6, "main"),
      (5, "$anonfun$1"), (5, "$anonfun$1$$anonfun$1"),
      (5, "$anonfun$1"), (5, "$anonfun$1$$anonfun$1"), (6, "$anonfun$2"),
      (5, "$anonfun$1"), (5, "$anonfun$1$$anonfun$1"), (6, "$anonfun$2")
    )
  }

  override def testLambdaInNestedObject(): Unit = {
    breakpointsTest()(
      (15, "main"),
      (8, "method"),
      (8, "LambdaInNestedObject$Outer$Inner$$$_$method$$anonfun$1"),
      (8, "LambdaInNestedObject$Outer$Inner$$$_$method$$anonfun$1"),
      (8, "LambdaInNestedObject$Outer$Inner$$$_$method$$anonfun$1")
    )
  }

  override def testLambdaInLocalMethod(): Unit = {
    breakpointsTest()(
      (21, "main"), (11, "func$1"),
      (8, "func$1$$anonfun$1"), (9, "func$1$$anonfun$1"), (10, "func$1$$anonfun$1"), (11, "func$1$$anonfun$1"),
      (8, "func$1$$anonfun$1"), (9, "func$1$$anonfun$1"), (10, "func$1$$anonfun$1"), (11, "func$1$$anonfun$1"),
      (8, "func$1$$anonfun$1"), (9, "func$1$$anonfun$1"), (10, "func$1$$anonfun$1"), (11, "func$1$$anonfun$1"),
    )
  }

  override def testLambdaInObjectConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "<clinit>"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"), (4, "$init$$$anonfun$1"))
  }

  override def testVariousLambdas(): Unit = {
    breakpointsTest()(
      (3, "<clinit>"),
      (7, "main"), (3, "$init$$$anonfun$1"),
      (8, "main"), (8, "main$$anonfun$1"),
      (9, "main"), (9, "main$$anonfun$2"),
      (10, "main"), (10, "main$$anonfun$3"),
      (11, "main"), (11, "main$$anonfun$4"),
      (12, "main"), (12, "main$$anonfun$5"),
      (14, "main"), (14, "main$$anonfun$6"),
      (16, "main"), (16, "main$$anonfun$7"),
      (18, "main"), (18, "main$$anonfun$8"),
      (20, "main"), (20, "main$$anonfun$9")
    )
  }

  override def testLambdaBreakpointsInsideTrait(): Unit = {
    breakpointsTest()(
      (23, "fooFromTrait"),
      (22, "$anonfun$2"), (23, "$anonfun$2"), (22, "$anonfun$2"), (23, "$anonfun$2"), (25, "fooFromTrait"),
      (34, "fooFromAbstractClass"),
      (33, "$anonfun$3"), (34, "$anonfun$3"), (33, "$anonfun$3"), (34, "$anonfun$3"), (36, "fooFromAbstractClass"),
      (12, "fooFromMain"),
      (11, "$anonfun$1"), (12, "$anonfun$1"), (11, "$anonfun$1"), (12, "$anonfun$1"), (14, "fooFromMain"),
    )
  }

  override def testLineInLambdaWithLambdaOnLineInTrait(): Unit = {
    breakpointsTest()(
      (6, "fooInTrait"),
      (5, "$anonfun$1"), (6, "$anonfun$1"), (6, "$anonfun$1$$anonfun$1"),
      (5, "$anonfun$1"), (6, "$anonfun$1"), (6, "$anonfun$1$$anonfun$1"),
      (8, "fooInTrait")
    )
  }
}

class LambdaBreakpointsTest_3_5 extends LambdaBreakpointsTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class LambdaBreakpointsTest_3_6 extends LambdaBreakpointsTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class LambdaBreakpointsTest_3_7 extends LambdaBreakpointsTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_7
}

class LambdaBreakpointsTest_3_RC extends LambdaBreakpointsTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class LambdaBreakpointsTest_3_Next_RC extends LambdaBreakpointsTest_3_RC {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}

abstract class LambdaBreakpointsTestBase extends BreakpointsTestBase {

  addSourceFile("LambdaInClassConstructor.scala",
    s"""
       |object LambdaInClassConstructor {
       |  class C {
       |    (0 until 3).foreach { x =>
       |      println(x) $breakpoint
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(new C()) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInClassConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "apply$mcVI$sp"), (4, "apply$mcVI$sp"), (4, "apply$mcVI$sp"))
  }

  addSourceFile("LambdaInObjectConstructor.scala",
    s"""
       |object LambdaInObjectConstructor {
       |  object O {
       |    (0 until 3).foreach { x =>
       |      println(x) $breakpoint
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(O) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInObjectConstructor(): Unit = {
    breakpointsTest()((9, "main"), (4, "apply$mcVI$sp"), (4, "apply$mcVI$sp"), (4, "apply$mcVI$sp"))
  }

  addSourceFile("LambdaInNestedObjectStatic.scala",
    s"""
       |object LambdaInNestedObjectStatic {
       |  class Outer {
       |    object Inner {
       |      def method(n: Int): Unit = {
       |        (0 until n).foreach { x => println(x) } $breakpoint ${lambdaOrdinal(0)}
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer().Inner.method(3) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedObjectStatic(): Unit = {
    breakpointsTest()((11, "main"), (5, "apply$mcVI$sp"), (5, "apply$mcVI$sp"), (5, "apply$mcVI$sp"))
  }

  addSourceFile("LambdaInNestedClassStatic.scala",
    s"""
       |object LambdaInNestedClassStatic {
       |  object Outer {
       |    class Inner {
       |      def method(n: Int): Unit = {
       |        (0 until n).foreach(println) $breakpoint ${lambdaOrdinal(0)}
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer.Inner().method(3) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedClassStatic(): Unit = {
    breakpointsTest()((11, "main"), (5, "apply"), (5, "apply"), (5, "apply"))
  }

  addSourceFile("LambdaInNestedObject.scala",
    s"""
       |object LambdaInNestedObject {
       |  class Outer {
       |    val field: Int = 3
       |
       |    object Inner {
       |      def method(): Unit = {
       |        (0 until field).foreach { x =>
       |          println(x) $breakpoint
       |        }
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer().Inner.method() $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedObject(): Unit = {
    breakpointsTest()((15, "main"), (8, "apply$mcVI$sp"), (8, "apply$mcVI$sp"), (8, "apply$mcVI$sp"))
  }

  addSourceFile("LambdaInNestedClass.scala",
    s"""
       |object LambdaInNestedClass {
       |  object Outer {
       |    val field: Int = 3
       |
       |    class Inner {
       |      def method(): Unit = {
       |        (0 until field).foreach { x =>
       |          println(x) $breakpoint
       |        }
       |      }
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    new Outer.Inner().method() $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInNestedClass(): Unit = {
    breakpointsTest()(
      (15, "main"), (8, "apply$mcVI$sp"), (8, "apply$mcVI$sp"), (8, "apply$mcVI$sp")
    )
  }

  addSourceFile("LambdaInLocalMethod.scala",
    s"""
       |object LambdaInLocalMethod {
       |  case class A(s: String = "s", i: Int = 1)
       |
       |  object Inside {
       |    def create(a: A) = {
       |      def func(a: A, count: Int) = {
       |        (0 until count).map { i =>
       |          val number = i + 1 $breakpoint
       |          val string = i.toString $breakpoint
       |          val insideA = A(string, number) $breakpoint
       |          insideA.s * number $breakpoint
       |        }
       |
       |      }
       |      func(a, 3)
       |      a
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    Inside.create(A()) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaInLocalMethod(): Unit = {
    breakpointsTest()(
      (21, "main"),
      (8, "apply"), (9, "apply"), (10, "apply"), (11, "apply"),
      (8, "apply"), (9, "apply"), (10, "apply"), (11, "apply"),
      (8, "apply"), (9, "apply"), (10, "apply"), (11, "apply")
    )
  }

  addSourceFile(s"LambdaInGuard.scala",
    s"""
       |object LambdaInGuard {
       |  def contains(f: Int => Boolean, x: Int): Boolean = f(x)
       |
       |  def main(args: Array[String]): Unit = {
       |    val xs = for { i <- (0 to 2) if contains(_ > 0, i) } $breakpoint
       |      yield i $breakpoint
       |    println(xs)
       |  }
       |}
       |""".stripMargin)

  def testLambdaInGuard(): Unit = {
    breakpointsTest()(
      (5, "main"),
      (5, "apply$mcZI$sp"), (5, "apply$mcZI$sp"),
      (5, "apply$mcZI$sp"), (5, "apply$mcZI$sp"), (6, "apply$mcII$sp"),
      (5, "apply$mcZI$sp"), (5, "apply$mcZI$sp"), (6, "apply$mcII$sp")
    )
  }

  addSourceFile(s"VariousLambdas.scala",
    s"""
       |object VariousLambdas {
       |  private val plusTwo: Int => Int =
       |    x => x + 2 $breakpoint
       |
       |  def main(args: Array[String]): Unit = {
       |    Seq(1)
       |      .map(plusTwo) $breakpoint
       |      .map(x => x + 2) $breakpoint
       |      .map(_ + 2) $breakpoint
       |      .map { x => x + 2 } $breakpoint
       |      .map({ x => x + 2 }) $breakpoint
       |      .map({ _ + 2 }) $breakpoint
       |      .map {
       |        _ + 2 $breakpoint
       |      }.map {
       |        x => x + 2 $breakpoint
       |      }.map {
       |        (_ + 2) $breakpoint
       |      }.map { x =>
       |        x + 2 $breakpoint
       |      }
       |  }
       |}
       |""".stripMargin
  )

  def testVariousLambdas(): Unit = {
    breakpointsTest()(
      (3, "<init>"),
      (7, "main"), (3, "apply$mcII$sp"),
      (8, "main"), (8, "apply$mcII$sp"),
      (9, "main"), (9, "apply$mcII$sp"),
      (10, "main"), (10, "apply$mcII$sp"),
      (11, "main"), (11, "apply$mcII$sp"),
      (12, "main"), (12, "apply$mcII$sp"),
      (14, "main"), (14, "apply$mcII$sp"),
      (16, "main"), (16, "apply$mcII$sp"),
      (18, "main"), (18, "apply$mcII$sp"),
      (20, "apply$mcII$sp")
    )
  }

  addSourceFile("ByNameInExtensionMethod.scala",
    s"""
       |object ByNameInExtensionMethod {
       |  implicit class IntOpt(private val n: Int) extends AnyVal {
       |    def doubleIt: Int = None.getOrElse(
       |      n * 2 $breakpoint
       |    )
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(2.doubleIt)
       |  }
       |}
       |""".stripMargin)

  def testByNameInExtensionMethod(): Unit = {
    breakpointsTest()(
      (4, "doubleIt$extension"),
      (4, "apply$mcI$sp")
    )
  }

  addSourceFile("LambdaBreakpointsInsideTrait.scala",
    s"""
       |object LambdaBreakpointsInsideTrait extends MyAbstractClass with MyTrait {
       |  def main(args: Array[String]): Unit = {
       |    fooFromTrait()
       |    fooFromAbstractClass()
       |    fooFromMain()
       |  }
       |
       |  def fooFromMain(): Unit = {
       |    val seq = 1 to 2
       |    val seqNew = seq.map { item =>
       |      val x = 1 + 1 $breakpoint
       |      item + x $breakpoint
       |    }
       |    println(seqNew) $breakpoint
       |  }
       |}
       |
       |trait MyTrait {
       |  def fooFromTrait(): Unit = {
       |    val seq = 1 to 2
       |    val seqNew = seq.map { item =>
       |      val x = 1 + 1 $breakpoint
       |      item + x $breakpoint
       |    }
       |    println(seqNew) $breakpoint
       |  }
       |}
       |
       |abstract class MyAbstractClass {
       |  def fooFromAbstractClass(): Unit = {
       |    val seq = 1 to 2
       |    val seqNew = seq.map { item =>
       |      val x = 1 + 1 $breakpoint
       |      item + x $breakpoint
       |    }
       |    println(seqNew) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testLambdaBreakpointsInsideTrait(): Unit = {
    breakpointsTest()(
      (22, "apply$mcII$sp"), (23, "apply$mcII$sp"), (22, "apply$mcII$sp"), (23, "apply$mcII$sp"), (25, "fooFromTrait"),
      (33, "apply$mcII$sp"), (34, "apply$mcII$sp"), (33, "apply$mcII$sp"), (34, "apply$mcII$sp"), (36, "fooFromAbstractClass"),
      (11, "apply$mcII$sp"), (12, "apply$mcII$sp"), (11, "apply$mcII$sp"), (12, "apply$mcII$sp"), (14, "fooFromMain"),
    )
  }

  addSourceFile("OneLineLambdaInTrait.scala",
    s"""
       |trait MyTraitWithOneLineLambda {
       |  def fooInTrait(): Unit = {
       |    val seq = 1 to 2
       |    val seqNew = seq.map(_ + 2) $breakpoint ${lambdaOrdinal(0)}
       |    println(seqNew) $breakpoint
       |  }
       |}
       |
       |object OneLineLambdaInTrait extends MyTraitWithOneLineLambda {
       |  def main(args: Array[String]): Unit = {
       |    fooInTrait()
       |  }
       |}
       |""".stripMargin)

  def testOneLineLambdaInTrait(): Unit = {
    breakpointsTest()(
      (4, "apply$mcII$sp"), (4, "apply$mcII$sp"), (5, "fooInTrait")
    )
  }

  addSourceFile("LineInLambdaWithLambdaOnLineInTrait.scala",
    s"""
       |trait MyTraitWithLambdaWithLambdaOnLine {
       |  def fooInTrait(): Unit = {
       |    val seq = 1 to 2
       |    val seqNew = seq.flatMap { item =>
       |      val x = 1 + 1 $breakpoint
       |      Seq(item).map(_ + x) $breakpoint
       |    }
       |    println(seqNew) $breakpoint
       |  }
       |}
       |
       |object LineInLambdaWithLambdaOnLineInTrait extends MyTraitWithLambdaWithLambdaOnLine {
       |  def main(args: Array[String]): Unit = {
       |    fooInTrait()
       |  }
       |}
       |""".stripMargin)

  def testLineInLambdaWithLambdaOnLineInTrait(): Unit = {
    breakpointsTest()(
      (5, "apply"), (6, "apply"), (6, "apply$mcII$sp"),
      (5, "apply"), (6, "apply"), (6, "apply$mcII$sp"),
      (8, "fooInTrait")
    )
  }
}

