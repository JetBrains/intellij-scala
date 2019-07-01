package org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.junit.Assert._

trait FlatSpecScopeTest extends ScalaTestTestCase {
  private val flatSpecScopeTestClassName = "FlatSpecScopeTest"
  private val className = flatSpecScopeTestClassName
  private val fileName = s"$flatSpecScopeTestClassName.scala"

  addSourceFile(fileName,
    s"""import org.scalatest._
       |
       |class $className extends FlatSpec with GivenWhenThen {
       |
       |  it should "test name without behaviour with it" in {}
       |  it should "test name without behaviour with it tagged" taggedAs (ItTag2) in {}
       |
       |  "Test Prefix" should "test name with string prefix" in {}
       |  it should "test name with it and string prefix above" in {}
       |
       |  behavior of "Behaviour Descriptor"
       |  it should "test name with it behaviour" in {}
       |
       |}
       |
       |object ItTag2 extends Tag("MyTag")
       |""".stripMargin
  )

  def testFlatSpecScope_ShouldIncludeItTestsWithoutBehaviourInTheBeginning() {
    //TODO: "fix scalatest-finders-patched: FlatSpecFinder#getAllTestSelection does not detect tests without initial scope"
    return

    val testPaths = Seq(
      Seq("[root]", className, "should test name without behaviour with it"),
      Seq("[root]", className, "should test name without behaviour with it tagged"),
      Seq("[root]", className, "Test Prefix", "should test name with string prefix"),
      Seq("[root]", className, "Test Prefix", "should test name with it and string prefix above"),
      Seq("[root]", className, "Behaviour Descriptor", "should test name with it behaviour")
    )
    val testNames = testPaths.map(_.drop(2).mkString(" "))

    runTestByLocation(
      3, 1,
      fileName,
      configAndSettings => {
        assertConfigAndSettings(configAndSettings, className, testNames: _*)
        true
      },
      root => {
        val pathsNotInTree = testPaths.filterNot(path => checkResultTreeHasExactNamedPath(root, path: _*))
        if (pathsNotInTree.nonEmpty) {
          fail(s"result tree does not contain paths:\n${pathsNotInTree.map("  " + _).mkString("\n")}")
        }
        true
      }
    )
  }


}
