package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
 * User: Alefas
 * Date: 15.10.11
 */

class ScalaObjectEvaluationTest extends ScalaDebuggerTestCase {
  def testSimpleObject() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      |
      |object Simple
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("Simple", "Simple$")
    }
  }

  def testQualifiedObject() {
    addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |object Simple
      """.stripMargin.trim()
    )

    addFileToProject("Sample.scala",
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
      evalStartsWith("qual.Simple", "qual.Simple$")
    }
  }

  def testJavaConversions() {
    addFileToProject("Sample.scala",
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
      evalStartsWith("collection.JavaConversions", "scala.collection.JavaConversions$")
    }
  }

  def testCaseClassObject() {
    addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |case class Simple(x: Int)
      """.stripMargin.trim()
    )

    addFileToProject("Sample.scala",
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
      evalEquals("qual.Simple", "Simple")
    }
  }

  def testStableInnerObject() {
    addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |object Simple {
      |  object Simple
      |}
      """.stripMargin.trim()
    )

    addFileToProject("Sample.scala",
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
      evalStartsWith("qual.Simple.Simple", "qual.Simple$Simple$")
    }
  }

  def testInnerClassObject() {
    addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |class Simple {
      |  object Simple
      |}
      """.stripMargin.trim()
    )

    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val x = new qual.Simple()
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("x.Simple", "qual.Simple$Simple$")
    }
  }

  def testInnerClassObjectFromObject() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  class S {
      |    object SS {
      |      object S {
      |        def foo() {
      |          SS.S //to have $outer field
      |          "stop here"
      |        }
      |      }
      |      object G
      |    }
      |    def foo() {
      |      SS.S.foo()
      |    }
      |  }
      |
      |  def main(args: Array[String]) {
      |    val x = new S()
      |    x.foo()
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("SS.G", "Sample$S$SS$G")
    }
  }
}