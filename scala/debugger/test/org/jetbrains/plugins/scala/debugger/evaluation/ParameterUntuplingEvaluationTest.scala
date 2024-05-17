package org.jetbrains.plugins.scala
package debugger
package evaluation

class ParameterUntuplingEvaluationTest_3 extends ParameterUntuplingEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class ParameterUntuplingEvaluationTest_3_RC extends ParameterUntuplingEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5_RC
}

abstract class ParameterUntuplingEvaluationTestBase extends ExpressionEvaluationTestBase {

  addSourceFile("MapPairsUntuping.scala",
    s"""object MapPairsUntupling:
       |  def main(args: Array[String]): Unit =
       |    Map("one" -> 1, "two" -> 2, "three" -> 3).foreach: (k, v) =>
       |      println(k) $breakpoint
       |      println(v)
       |""".stripMargin)

  def testMapPairsUntupling(): Unit = expressionEvaluationTest()(
    implicit ctx => {
      evalEquals("k", "one")
      evalEquals("v", "1")
    },
    implicit ctx => {
      evalEquals("k", "two")
      evalEquals("v", "2")
    },
    implicit ctx => {
      evalEquals("k", "three")
      evalEquals("v", "3")
    }
  )

  addSourceFile("MapPairsWithCapture.scala",
    s"""object MapPairsWithCapture:
       |  def main(args: Array[String]): Unit =
       |    val x = 123
       |    Map("one" -> 1, "two" -> 2, "three" -> 3).foreach: (k, v) =>
       |      println(x)
       |      println(k) $breakpoint
       |      println(v)
       |""".stripMargin)

  def testMapPairsWithCapture(): Unit = expressionEvaluationTest()(
    implicit ctx => {
      evalEquals("x", "123")
      evalEquals("k", "one")
      evalEquals("v", "1")
    },
    implicit ctx => {
      evalEquals("x", "123")
      evalEquals("k", "two")
      evalEquals("v", "2")
    },
    implicit ctx => {
      evalEquals("x", "123")
      evalEquals("k", "three")
      evalEquals("v", "3")
    }
  )

  addSourceFile("Triples.scala",
    s"""object Triples:
       |  def main(args: Array[String]): Unit =
       |    List(("one", 1, "eno"), ("two", 2, "owt"), ("three", 3, "eerht")).foreach: (a, b, c) =>
       |      println(a)
       |      println(b) $breakpoint
       |      println(c)
       |""".stripMargin)

  def testTriples(): Unit = expressionEvaluationTest()(
    implicit ctx => {
      evalEquals("a", "one")
      evalEquals("b", "1")
      evalEquals("c.reverse", "one")
    },
    implicit ctx => {
      evalEquals("a", "two")
      evalEquals("b", "2")
      evalEquals("c.reverse", "two")
    },
    implicit ctx => {
      evalEquals("a", "three")
      evalEquals("b", "3")
      evalEquals("c.reverse","three")
    }
  )

  addSourceFile("ListOfTuples.scala",
    s"""object ListOfTuples:
       |  def main(args: Array[String]): Unit =
       |    List((0, 1, 2, 3), (4, 5, 6, 7), (8, 9, 10, 11)).foreach: (a, b, c, d) =>
       |      println() $breakpoint
       |      println(a + b + c + d)
       |""".stripMargin)

  def testListOfTuples(): Unit = expressionEvaluationTest()(
    implicit ctx => evalEquals("a + b + c + d", "6"),
    implicit ctx => evalEquals("a + b + c + d", "22"),
    implicit ctx => evalEquals("a + b + c + d", "38")
  )

  addSourceFile("OneLineLambda.scala",
    s"""object OneLineLambda:
       |  def main(args: Array[String]): Unit =
       |    List((0, 1), (1, 2), (3, 5)).map((x, y) => x + y) $breakpoint ${lambdaOrdinal(0)}
       |""".stripMargin)

  def testOneLineLambda(): Unit = expressionEvaluationTest()(
    implicit ctx => evalEquals("x + y", "1"),
    implicit ctx => evalEquals("x + y", "3"),
    implicit ctx => evalEquals("x + y", "8")
  )

  addSourceFile("NestedUntupledLambdas.scala",
    s"""object NestedUntupledLambdas:
       |  def main(args: Array[String]): Unit =
       |    List((1 -> "one"), (2 -> "two"), (3 -> "three")).foreach: (number, name) =>
       |      List((number, name, name * number)).foreach: (x, y, z) =>
       |        println() $breakpoint
       |        println(s"$$number, $$name, $$x, $$y, $$z")
       |""".stripMargin)

  def testNestedUntupledLambdas(): Unit = expressionEvaluationTest()(
    implicit ctx => {
      evalEquals("number == x", "true")
      evalEquals("name == y", "true")
      evalEquals("z", "one")
    },
    implicit ctx => {
      evalEquals("number == x", "true")
      evalEquals("name == y", "true")
      evalEquals("z", "twotwo")
    },
    implicit ctx => {
      evalEquals("number == x", "true")
      evalEquals("name == y", "true")
      evalEquals("z", "threethreethree")
    }
  )

  addSourceFile("MainAnnotationUntupled.scala",
    s"""@main
       |def mainAnnotationUntupled(): Unit =
       |  List((1, "one"), (2, "two"), (3, "three")).foreach: (number, name) =>
       |    println(number) $breakpoint
       |    println(name)
       |""".stripMargin)

  def testMainAnnotationUntupled(): Unit = expressionEvaluationTest("mainAnnotationUntupled")(
    implicit ctx => {
      evalEquals("number", "1")
      evalEquals("name", "one")
    },
    implicit ctx => {
      evalEquals("number", "2")
      evalEquals("name", "two")
    },
    implicit ctx => {
      evalEquals("number", "3")
      evalEquals("name", "three")
    }
  )
}
