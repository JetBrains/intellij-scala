package org.jetbrains.plugins.scala
package debugger
package evaluation

class InAnonFunEvaluationTest_2_11 extends InAnonFunEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class InAnonFunEvaluationTest_2_12 extends InAnonFunEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class InAnonFunEvaluationTest_2_13 extends InAnonFunEvaluationTest_2_12 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class InAnonFunEvaluationTest_3 extends InAnonFunEvaluationTest_2_13 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class InAnonFunEvaluationTest_3_RC extends InAnonFunEvaluationTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
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
       |        case (a, i) =>
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
       |            println()
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
      evalFailsWith("i", DebuggerBundle.message("not.used.from.for.statement", "i"))
      evalFailsWith("si", DebuggerBundle.message("not.used.from.for.statement", "si"))
    }
  }

  addSourceFile("PartialFunction2.scala",
    s"""object PartialFunction2 {
       |  class A { override def toString: String = "A" }
       |  def main(args: Array[String]): Unit = {
       |    Seq(new A, new A).foreach {
       |      case a =>
       |        println() $breakpoint
       |    }
       |  }
       |}""".stripMargin.trim)

  def testPartialFunction2(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("a", "A")
    }
  }

  addSourceFile("PartialFunctionCaseClass.scala",
    s"""object PartialFunctionCaseClass {
       |  final case class Person(name: String, age: Int)
       |  def main(args: Array[String]): Unit = {
       |    Seq(Person("Name", 25)).foreach {
       |      case p @ Person(name, age) =>
       |        println() $breakpoint
       |    }
       |  }
       |}""".stripMargin.trim)

  def testPartialFunctionCaseClass(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("name", "Name")
      evalEquals("age", "25")
      evalEquals("p", "Person(Name,25)")
    }
  }
}
