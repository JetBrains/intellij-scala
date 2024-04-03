package org.jetbrains.plugins.scala
package debugger
package evaluation

class VariablesFromPatternsEvaluationTest_2_11 extends VariablesFromPatternsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class VariablesFromPatternsEvaluationTest_2_12 extends VariablesFromPatternsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class VariablesFromPatternsEvaluationTest_2_13 extends VariablesFromPatternsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class VariablesFromPatternsEvaluationTest_3 extends VariablesFromPatternsEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testAnonymousInMatch(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("some", "Some(a)")
      evalEquals("a", "a")
      failing(evalEquals("i", "10"))
    }
  }
}

class VariablesFromPatternsEvaluationTest_3_RC extends VariablesFromPatternsEvaluationTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class VariablesFromPatternsEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("Match.scala",
    s"""
       |object Match {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    val x = (List(1, 2), Some("z"), None)
       |    x match {
       |      case all @ (list @ List(q, w), some @ Some(z), _) =>
       |        println() $breakpoint
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim
  )

  def testMatch(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("all", "(List(1, 2),Some(z),None)")
      evalEquals("list", "List(1, 2)")
      evalEquals("x", "(List(1, 2),Some(z),None)")
      evalEquals("name", "name")
      evalEquals("q", "1")
      evalEquals("z", "z")
      evalEquals("some", "Some(z)")
      evalEquals("args", "[]")
    }
  }

  addSourceFile("MatchInForStmt.scala",
    s"""
       |object MatchInForStmt {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    for (s <- List("a", "b"); if s == "a"; ss = s + s; i <- List(1,2); if i == 1; si = s + i) {
       |      val x = (List(1, 2), Some("z"), ss :: i :: Nil)
       |      x match {
       |        case all @ (q :: qs, some @ Some(z), list @ List(m, _)) =>
       |          println() $breakpoint
       |        case _ =>
       |      }
       |    }
       |  }
       |}
      """.stripMargin.trim
  )

  def testMatchInForStmt(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("all", "(List(1, 2),Some(z),List(aa, 1))")
      evalEquals("x", "(List(1, 2),Some(z),List(aa, 1))")
      evalEquals("name", "name")
      evalEquals("q", "1")
      evalEquals("qs", "List(2)")
      evalEquals("z", "z")
      evalEquals("list", "List(aa, 1)")
      evalEquals("m", "aa")
      evalEquals("some", "Some(z)")
      evalEquals("ss", "aa")
      evalEquals("i", "1")
      evalEquals("args", "[]")
    }
  }

  addSourceFile("RegexMatch.scala",
    {
      val pattern = """"(-)?(\\d+)(\\.\\d*)?".r"""
      s"""
         |object RegexMatch {
         |  val name = "name"
         |  def main(args: Array[String]): Unit = {
         |    val Decimal = $pattern
         |    "-2.5" match {
         |      case number @ Decimal(sign, _, dec) =>
         |        println() $breakpoint
         |      case _ =>
         |    }
         |  }
         |}
      """.stripMargin.trim
    }

  )

  def testRegexMatch(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("number", "-2.5")
      evalEquals("sign", "-")
      evalEquals("dec", ".5")
      evalEquals("name", "name")
    }
  }

  addSourceFile("Multilevel.scala",
    s"""
       |object Multilevel {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    List(None, Some(1 :: 2 :: Nil)) match {
       |      case List(none, some) =>
       |        some match {
       |          case Some(seq) =>
       |            seq match {
       |              case Seq(1, two) =>
       |                println() $breakpoint
       |              case _ =>
       |            }
       |          case _ =>
       |        }
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim
  )

  def testMultilevel(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("none", "None")
      evalEquals("some", "Some(List(1, 2))")
      evalEquals("seq", "List(1, 2)")
      evalEquals("two", "2")
    }
  }

  addSourceFile("LocalInMatch.scala",
    s"""
       |object LocalInMatch {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    Option("a") match {
       |      case None =>
       |      case some @ Some(a) =>
       |        def foo(i: Int): Unit = {
       |          println() $breakpoint
       |        }
       |        foo(10)
       |    }
       |  }
       |}
      """.stripMargin.trim
  )

  def testLocalInMatch(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("some", "Some(a)")
      evalEquals("a", "a")
      evalEquals("i", "10")
    }
  }

  addSourceFile("AnonymousInMatch.scala",
    s"""
       |object AnonymousInMatch {
       |  val name = "name"
       |  def main(args: Array[String]): Unit = {
       |    Option("a") match {
       |      case None =>
       |      case some @ Some(a) =>
       |        List(10) foreach { i =>
       |          println() $breakpoint
       |        }
       |    }
       |  }
       |}
      """.stripMargin.trim
  )

  def testAnonymousInMatch(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("some", "Some(a)")
      evalEquals("a", "a")
      evalEquals("i", "10")
    }
  }

  addSourceFile("TypedPatterns.scala",
    s"""
       |object TypedPatterns {
       |  def main(args: Array[String]): Unit = {
       |    val something: Any = "hello"
       |    something match {
       |      case str: String =>
       |        println(str) $breakpoint
       |      case sth =>
       |        println(sth)
       |    }
       |
       |    val option = Option(5)
       |    option match {
       |      case Some(n: Int) =>
       |        println(n) $breakpoint
       |      case None =>
       |        println("None")
       |    }
       |  }
       |}
       |""".stripMargin.trim)

  def testTypedPatterns(): Unit = {
    expressionEvaluationTest()(
      { implicit ctx => evalEquals("str", "hello") },
      { implicit ctx => evalEquals("n", "5") }
    )
  }
}
