package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 15.10.11
 */

class ScalaObjectEvaluationTest extends ScalaDebuggerTestCase {
  def testSimpleObject() {
    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |object Simple
      """.stripMargin.trim()
    )

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
      evalStartsWith("qual.Simple", "qual.Simple$")
    }
  }

  def testJavaConversions() {
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
      evalStartsWith("collection.JavaConversions", "scala.collection.JavaConversions$")
    }
  }

  def testCaseClassObject() {
    myFixture.addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |case class Simple(x: Int)
      """.stripMargin.trim()
    )

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
      evalEquals("qual.Simple", "Simple")
    }
  }

  def testStableInnerObject() {
    myFixture.addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |object Simple {
      |  object Simple
      |}
      """.stripMargin.trim()
    )

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
      evalStartsWith("qual.Simple.Simple", "qual.Simple$Simple$")
    }
  }

  def testInnerClassObject() {
    myFixture.addFileToProject("qual/Sample.scala",
      """
      |package qual
      |
      |class Simple {
      |  object Simple
      |}
      """.stripMargin.trim()
    )

    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("Sample.scala",
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