package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.11.11
 */

class NewTemplateEvaluationTest extends ScalaDebuggerTestCase {
  def testJavaLib() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("new StringBuilder(\"test\").append(23)", "test23")
    }
  }

  def testInnerClass() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class Expr {}
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("new Expr", "Sample$Expr")
    }
  }

  def testOverloadingClass() {
    myFixture.addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class Expr(s: String) {
      |    def this(t: Int) {
      |      this("test")
      |    }
      |  }
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 7)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("new Expr(\"\")", "Sample$Expr")
      evalStartsWith("new Expr(2)", "Sample$Expr")
    }
  }
}