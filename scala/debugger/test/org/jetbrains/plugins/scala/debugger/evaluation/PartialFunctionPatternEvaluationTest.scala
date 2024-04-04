package org.jetbrains.plugins.scala.debugger.evaluation

import org.jetbrains.plugins.scala.ScalaVersion

class PartialFunctionPatternEvaluationTest_2_12 extends PartialFunctionPatternEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class PartialFunctionPatternEvaluationTest_2_13 extends PartialFunctionPatternEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class PartialFunctionPatternEvaluationTest_3 extends PartialFunctionPatternEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class PartialFunctionPatternEvaluationTest_3_RC extends PartialFunctionPatternEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class PartialFunctionPatternEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("PartialFunctionHigherOrder.scala",
    s"""object PartialFunctionHigherOrder {
       |  def main(args: Array[String]): Unit = {
       |    Seq(1, 2).collect {
       |      case 0 => 0
       |      case x if x % 2 != 0 =>
       |        println() $breakpoint
       |        1
       |      case x if x % 2 == 0 =>
       |        println(x) $breakpoint
       |        x
       |    }
       |  }
       |}
       |""".stripMargin)

  def testPartialFunctionHigherOrder(): Unit = expressionEvaluationTest()(
    { implicit ctx => evalEquals("x", "1") },
    { implicit ctx => evalEquals("x", "2") }
  )

  addSourceFile("LambdaMultipleParams.scala",
    s"""object LambdaMultipleParams {
       |  def main(args: Array[String]): Unit = {
       |    Seq(1, 2, 3).foldLeft(0) {
       |      case (0, x) =>
       |        println() $breakpoint
       |        x
       |      case (x, y) =>
       |        println() $breakpoint
       |        x + y
       |    }
       |  }
       |}
       |""".stripMargin)

  def testLambdaMultipleParams(): Unit = expressionEvaluationTest()(
    { implicit ctx => evalEquals("x", "1") },
    { implicit ctx =>
      evalEquals("x", "1")
      evalEquals("y", "2")
    },
    { implicit ctx =>
      evalEquals("x", "3")
      evalEquals("y", "3")
    }
  )

  addSourceFile("SAMClass.scala",
    s"""abstract class TransformerClass {
       |  def transform(int: Int, string: String): Double
       |}
       |
       |object SAMClass {
       |  def implement(additional: Double): TransformerClass = {
       |    case (0, "abc") => 0.0
       |    case (n, str) =>
       |      println() $breakpoint
       |      n.toDouble + additional
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    implement(5.0).transform(7, "abc")
       |  }
       |}
       |""".stripMargin)

  def testSAMClass(): Unit = expressionEvaluationTest() { implicit ctx =>
    evalEquals("n", "7")
    evalEquals("str", "abc")
    evalEquals("additional", "5.0")
  }

  addSourceFile("SAMTrait.scala",
    s"""trait TransformerTrait {
       |  def transform(string: String): Int
       |}
       |
       |object SAMTrait {
       |  def implement(additional: Int): TransformerTrait = {
       |    case "def" => 0
       |    case str =>
       |      println() $breakpoint
       |      additional
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    implement(4).transform("abcdef")
       |  }
       |}
       |""".stripMargin)

  def testSAMTrait(): Unit = expressionEvaluationTest() { implicit ctx =>
    evalEquals("str", "abcdef")
    evalEquals("additional", "4")
  }

  addSourceFile("CaseClassIntTuple.scala",
    s"""object CaseClassIntTuple {
       |  case class Person(name: String, age: Int)
       |
       |  def main(args: Array[String]): Unit = {
       |    Seq((Person("Foo", 123), 500), (Person("Bar", 234), 600)).foreach {
       |      case (Person("Foo", age), bonus) =>
       |        println() $breakpoint
       |      case (p @ Person(name, age), bonus) if bonus == 600 =>
       |        println() $breakpoint
       |    }
       |  }
       |}
       |""".stripMargin)

  def testCaseClassIntTuple(): Unit = expressionEvaluationTest()(
    { implicit ctx =>
      evalEquals("age", "123")
      evalEquals("bonus", "500")
    },
    { implicit ctx =>
      evalEquals("name", "Bar")
      evalEquals("age", "234")
      evalEquals("bonus", "600")
      evalEquals("p.name", "Bar")
      evalEquals("p.age", "234")
    }
  )

  addSourceFile("CaseClassIntTriple.scala",
    s"""object CaseClassIntTriple {
       |  case class Person(name: String, age: Int)
       |
       |  def main(args: Array[String]): Unit = {
       |    Seq(("something", Person("Foo", 123), 500), ("else", Person("Bar", 234), 600)).foreach {
       |      case (tag, Person("Foo", age), bonus) =>
       |        println() $breakpoint
       |      case (tag, p @ Person(name, age), bonus) if bonus == 600 =>
       |        println() $breakpoint
       |    }
       |  }
       |}
       |""".stripMargin)

  def testCaseClassIntTriple(): Unit = expressionEvaluationTest()(
    { implicit ctx =>
      evalEquals("age", "123")
      evalEquals("bonus", "500")
      evalEquals("tag", "something")
    },
    { implicit ctx =>
      evalEquals("name", "Bar")
      evalEquals("age", "234")
      evalEquals("bonus", "600")
      evalEquals("p.name", "Bar")
      evalEquals("p.age", "234")
      evalEquals("tag", "else")
    }
  )

  addSourceFile("PartialFunctionTupleArgument.scala",
    s"""object PartialFunctionTupleArgument {
       |  def main(args: Array[String]): Unit = {
       |    Seq((1, 2, 3)).collect {
       |      case (first, second, third) =>
       |        println() $breakpoint
       |        0
       |    }
       |  }
       |}""".stripMargin)

  def testPartialFunctionTupleArgument(): Unit = expressionEvaluationTest() { implicit ctx =>
    evalEquals("first", "1")
    evalEquals("second", "2")
    evalEquals("third", "3")
  }

  addSourceFile("TypeTestsSAM.scala",
    s"""object TypeTestsSAM {
       |  abstract class SAM {
       |    def method(cs: CharSequence): Int
       |  }
       |
       |  def implement(outer: Int): SAM = {
       |    case "abc" => 0
       |    case str: String =>
       |      println() $breakpoint
       |      str.toInt + outer
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    implement(10).method("123")
       |  }
       |}""".stripMargin)

  def testTypeTestsSAM(): Unit = expressionEvaluationTest() { implicit ctx =>
    evalEquals("outer", "10")
    evalEquals("str", "123")
  }
}
