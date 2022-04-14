package org.jetbrains.plugins.scala
package debugger
package evaluation

import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class InAnonFunEvaluationTest_2_11 extends InAnonFunEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class InAnonFunEvaluationTest_2_12 extends InAnonFunEvaluationTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  //todo SCL-9139
  override def testPartialFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "a")
      evalEquals("x", "x")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }
}

@Category(Array(classOf[DebuggerTests]))
class InAnonFunEvaluationTest_2_13 extends InAnonFunEvaluationTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class InAnonFunEvaluationTest_3_0 extends InAnonFunEvaluationTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0

  override def testFunctionExpr(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      failing(evalEquals("a", "a"))
      failing(evalEquals("x", "x"))
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }
}

@Category(Array(classOf[DebuggerTests]))
class InAnonFunEvaluationTest_3_1 extends InAnonFunEvaluationTest_3_0 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class InAnonFunEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("FunctionValue.scala",
    s"""
       |object FunctionValue {
       |  def main(args: Array[String]): Unit = {
       |    val a = "a"
       |    var b = "b"
       |    val f: (Int) => Unit = n => {
       |      val x = "x"
       |      println() $breakpoint
       |    }
       |    f(10)
       |  }
       |}
      """.stripMargin.trim()
  )

  def testFunctionValue(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "a")
      evalEquals("b", "b")
      evalEquals("x", "x")
      evalEquals("n", "10")
      evalEquals("args", "[]")
    }
  }

  addSourceFile("PartialFunction.scala",
    s"""
       |object PartialFunction {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    def printName(param: String, notUsed: String): Unit = {
       |      List(("a", 10)).foreach {
       |        case (a, i: Int) =>
       |            val x = "x"
       |            println(a + param)
       |            println() $breakpoint
       |      }
       |    }
       |    printName("param", "notUsed")
       |  }
       |}
      """.stripMargin.trim()
  )

  def testPartialFunction(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "a")
      evalEquals("i", "10")
      evalEquals("x", "x")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }

  addSourceFile("FunctionExpr.scala",
    s"""
       |object FunctionExpr {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    def printName(param: String, notUsed: String): Unit = {
       |      List("a").foreach {
       |        a =>
       |            val x = "x"
       |            println(a + param)
       |            println() $breakpoint
       |      }
       |    }
       |    printName("param", "notUsed")
       |  }
       |}
      """.stripMargin.trim()
  )

  def testFunctionExpr(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "a")
      evalEquals("x", "x")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }

  addSourceFile("ForStmt.scala",
    s"""
       |object ForStmt {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    def printName(param: String, notUsed: String): Unit = {
       |      for (s <- List("a", "b"); if s == "a"; ss = s + s; i <- List(1,2); if i == 1; si = s + i) {
       |        val in = "in"
       |        println(s + param + ss)
       |        println() $breakpoint
       |      }
       |    }
       |    printName("param", "notUsed")
       |  }
       |}
      """.stripMargin.trim()
  )

  def testForStmt(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("s", "a")
      evalEquals("in", "in")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
      evalEquals("ss", "aa")
      evalFailsWith("i", ScalaBundle.message("not.used.from.for.statement", "i"))
      evalFailsWith("si", ScalaBundle.message("not.used.from.for.statement", "si"))
    }
  }
}
