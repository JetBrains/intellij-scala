package org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FlatSpecScopeTest extends ScalaTestTestCase {

  private val flatSpecScopeTestClassName = "FlatSpecScopeTest"
  private val className = flatSpecScopeTestClassName
  private val fileName = s"$flatSpecScopeTestClassName.scala"

  addSourceFile(fileName,
    s"""$ImportsForFlatSpec
       |
       |class $className extends $FlatSpecBase with GivenWhenThen {
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

  def testFlatSpecScope_ShouldIncludeItTestsWithoutBehaviourInTheBeginning(): Unit = {
    //TODO: "fix scalatest-finders-patched: FlatSpecFinder#getAllTestSelection does not detect tests without initial scope"
/*
    val testPaths = Seq(
      TestNodePath("[root]", className, "should test name without behaviour with it"),
      TestNodePath("[root]", className, "should test name without behaviour with it tagged"),
      TestNodePath("[root]", className, "Test Prefix", "should test name with string prefix"),
      TestNodePath("[root]", className, "Test Prefix", "should test name with it and string prefix above"),
      TestNodePath("[root]", className, "Behaviour Descriptor", "should test name with it behaviour")
    )
    val testNames = testPaths.map(_.drop(2).mkString(" "))

    runTestByLocation2(
      3, 1,
      fileName,
      configAndSettings => {
        assertConfigAndSettings(configAndSettings, className, testNames: _*)
        true
      },
      root => {
        val pathsNotInTree = testPaths.filterNot(path => assertResultTreeHasExactNamedPath(root, path: _*))
        if (pathsNotInTree.nonEmpty) {
          fail(s"result tree does not contain paths:\n${pathsNotInTree.map("  " + _).mkString("\n")}")
        }
        true
      }
    )*/
  }


}
