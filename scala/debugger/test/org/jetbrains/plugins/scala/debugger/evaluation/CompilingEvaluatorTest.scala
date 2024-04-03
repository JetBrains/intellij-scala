package org.jetbrains.plugins.scala
package debugger
package evaluation

class CompilingEvaluatorTest_2_11 extends CompilingEvaluatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class CompilingEvaluatorTest_2_12 extends CompilingEvaluatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  // from scala 2.12.12 (or maybe from 2.12.11) ++ calls `List.:::`, not `TraversableLike.++`
  override def testFromLibrary(): Unit = {
    addBreakpointInLibrary("scala.collection.immutable.List", ":::")
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("prefix", "List(1, 2, 3, 4, 5)")
      evalEquals("prefix.exists(_ => true)", "true")
      evalEquals("prefix.exists(_ => false)", "false")
      evalEquals("prefix.exists(_ => false)", "false")
      evalEquals("None.getOrElse(1)", "1")
    }
  }
}

class CompilingEvaluatorTest_2_13 extends CompilingEvaluatorTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class CompilingEvaluatorTest_3 extends CompilingEvaluatorTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  // TODO: Current known limitation, we do not use the expression evaluator when evaluating code in a Scala 2 source file.
  override def testFromLibrary(): Unit = ()

  // TODO: Upstream bug with the expression compiler when evaluating a new instance of a newly defined class. Will report.
  override def testSimplePlace(): Unit = ()

  // TODO: A bug that should be addressed.
  override def testInLambda(): Unit = ()
}

class CompilingEvaluatorTest_3_RC extends CompilingEvaluatorTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class CompilingEvaluatorTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("SimplePlace.scala",
    s"""
       |object SimplePlace {
       |  val f = "f"
       |
       |  def foo(i: Int): Unit = {
       |    val x = 1
       |    println() $breakpoint
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    foo(3)
       |  }
       |}
    """.stripMargin.trim)

  def testSimplePlace(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("Seq(i, x).map(z => z * z).mkString(\", \")", "9, 1")

      evalEquals(
        """val result = for (z <- Seq(3, 4)) yield z * z
          |result.mkString""".stripMargin, "916")

      evalEquals(
        """def sqr(x: Int) = x * x
          |val a = sqr(12)
          |val b = sqr(1)
          |a + b""".stripMargin, "145")

      evalEquals(
        """Option(Seq(x)) match {
          |  case None => 1
          |  case Some(Seq(2)) => 2
          |  case Some(Seq(_)) => 0
          |}""".stripMargin, "0")

      evalEquals(
        """case class AAA(s: String, i: Int)
          |AAA("a", 1).toString""".stripMargin, "AAA(a,1)")
    }
  }

  addSourceFile("InForStmt.scala",
    s"""
       |object InForStmt {
       |  def main(args: Array[String]): Unit = {
       |    for {
       |      x <- Seq(1, 2)
       |      if x == 2
       |    } {
       |      println() $breakpoint
       |    }
       |  }
       |}
    """.stripMargin.trim
  )

  def testInForStmt(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("Seq(x, 2).map(z => z * z).mkString(\", \")", "4, 4")

      evalEquals(
        """def sqr(x: Int) = x * x
          |val a = sqr(12)
          |val b = sqr(1)
          |a + b
         """.stripMargin, "145")
    }
  }

  addSourceFile("InConstructor.scala",
    s"""
       |object InConstructor {
       |  def main(args: Array[String]): Unit = {
       |    new Sample().foo()
       |  }
       |
       |  class Sample {
       |    val a = "a"
       |    val b = "b" $breakpoint
       |
       |    def foo() = "foo"
       |  }
       |}
    """.stripMargin.trim
  )

  def testInConstructor(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("None.getOrElse(a)", "a")
      evalEquals("foo().map(_.toUpper)", "FOO")
    }
  }

  addSourceFile("AddBraces.scala",
    s"""package test
       |
       |object AddBraces {
       |  def main(args: Array[String]): Unit = {
       |    foo()
       |  }
       |
       |  def foo(): String = "foo" $breakpoint
       |}
      """.stripMargin.trim
  )

  def testAddBraces(): Unit = {
    expressionEvaluationTest("test.AddBraces") { implicit ctx =>
      evalEquals("None.getOrElse(foo())", "foo")

      evalEquals(
        """def bar = "bar"
          |foo() + bar""".stripMargin.trim, "foobar"
      )
    }
  }

  addSourceFile("FromPattern.scala",
    s"""
       |object FromPattern {
       |  def main(args: Array[String]): Unit = {
       |    Some("ab").map(x => (x, x + x)) match {
       |      case Some((a, b)) =>
       |        println(a)
       |        println() $breakpoint
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim
  )

  def testFromPattern(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "ab")
      evalEquals("b", "abab")
    }
  }

  addSourceFile("FromLibrary.scala",
    s"""object FromLibrary {
       |  def main(args: Array[String]): Unit = {
       |    val `this` = Seq(1, 2, 3, 4, 5)
       |    val that = Seq(6, 7, 8, 9, 10)
       |    `this` ++ that
       |  }
       |}
     """.stripMargin.trim
  )

  def testFromLibrary(): Unit = {
    addBreakpointInLibrary("scala.collection.TraversableLike", "++")
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("that", "List(6, 7, 8, 9, 10)")
      evalEquals("that.exists(_ => true)", "true")
      evalEquals("that.exists(_ => false)", "false")
      evalEquals("that.exists(_ => false)", "false")
      evalEquals("None.getOrElse(1)", "1")
    }
  }

  addSourceFile("InLambda.scala",
    s"""object InLambda {
       |  def main(args: Array[String]): Unit = {
       |    val list: List[Int] = List(1, 2, 3)
       |    list.map {x => println(x)}.toList $breakpoint ${lambdaOrdinal(0)}
       |    System.out.println()
       |  }
       |}
      """.stripMargin.trim
  )

  def testInLambda(): Unit = {
    expressionEvaluationTest()(
      implicit ctx => {
        evalEquals("x", "1")
        evalEquals("None.getOrElse(x)", "1")
      },
      implicit ctx => {
        evalEquals("x", "2")
        evalEquals("None.getOrElse(x)", "2")
      },
      implicit ctx => {
        evalEquals("x", "3")
        evalEquals("None.getOrElse(x)", "3")
      }
    )
  }
}
