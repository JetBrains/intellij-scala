package org.jetbrains.plugins.scala
package debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * Nikolay.Tropin
 * 8/5/13
 */
class VariablesFromPatternsEvaluationTest extends VariablesFromPatternsEvaluationTestBase with ScalaVersion_2_11

class VariablesFromPatternsEvaluationTest_212 extends VariablesFromPatternsEvaluationTestBase with ScalaVersion_2_12

abstract class VariablesFromPatternsEvaluationTestBase extends ScalaDebuggerTestCase{
  addFileWithBreakpoints("Match.scala",
    s"""
       |object Match {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    val x = (List(1, 2), Some("z"), None)
       |    x match {
       |      case all @ (list @ List(q, w), some @ Some(z), _) =>
       |        ""$bp
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testMatch() {
    runDebugger() {
      waitForBreakpoint()
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

  addFileWithBreakpoints("MatchInForStmt.scala",
    s"""
       |object MatchInForStmt {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    for (s <- List("a", "b"); if s == "a"; ss = s + s; i <- List(1,2); if i == 1; si = s + i) {
       |      val x = (List(1, 2), Some("z"), ss :: i :: Nil)
       |      x match {
       |        case all @ (q :: qs, some @ Some(z), list @ List(m, _)) =>
       |          ""$bp
       |        case _ =>
       |      }
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testMatchInForStmt() {
    runDebugger() {
      waitForBreakpoint()
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

  addFileWithBreakpoints("RegexMatch.scala",
    {
      val pattern = """"(-)?(\\d+)(\\.\\d*)?".r"""
      s"""
         |object RegexMatch {
         |  val name = "name"
         |  def main(args: Array[String]) {
         |    val Decimal = $pattern
         |    "-2.5" match {
         |      case number @ Decimal(sign, _, dec) =>
         |        ""$bp
         |      case _ =>
         |    }
         |  }
         |}
      """.stripMargin.trim()
    }

  )
  def testRegexMatch() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("number", "-2.5")
      evalEquals("sign", "-")
      evalEquals("dec", ".5")
      evalEquals("name", "name")
    }
  }

  addFileWithBreakpoints("Multilevel.scala",
    s"""
       |object Multilevel {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    List(None, Some(1 :: 2 :: Nil)) match {
       |      case List(none, some) =>
       |        some match {
       |          case Some(seq) =>
       |            seq match {
       |              case Seq(1, two) =>
       |                ""$bp
       |              case _ =>
       |            }
       |          case _ =>
       |        }
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testMultilevel() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("none", "None")
      evalEquals("some", "Some(List(1, 2))")
      evalEquals("seq", "List(1, 2)")
      evalEquals("two", "2")
    }
  }

  addFileWithBreakpoints("LocalInMatch.scala",
    s"""
       |object LocalInMatch {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    Option("a") match {
       |      case None =>
       |      case some @ Some(a) =>
       |        def foo(i: Int) {
       |          ""$bp
       |        }
       |        foo(10)
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testLocalInMatch() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("some", "Some(a)")
      evalEquals("a", "a")
      evalEquals("i", "10")
    }
  }

  addFileWithBreakpoints("AnonymousInMatch.scala",
    s"""
       |object AnonymousInMatch {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    Option("a") match {
       |      case None =>
       |      case some @ Some(a) =>
       |        List(10) foreach { i =>
       |          ""$bp
       |        }
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testAnonymousInMatch() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("name", "name")
      evalEquals("args", "[]")
      evalEquals("some", "Some(a)")
      evalEquals("a", "a")
      evalEquals("i", "10")
    }
  }

}
