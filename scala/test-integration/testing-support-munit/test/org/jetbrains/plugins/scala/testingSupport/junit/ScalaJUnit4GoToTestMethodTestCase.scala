package org.jetbrains.plugins.scala.testingSupport.junit

class ScalaJUnit4GoToTestMethodTestCase extends ScalaJUnit4TestingTestCaseBase {

  private val ScalaJUnitTestCase_FileName = "ScalaJUnitTestCase.scala"
  private val ScalaJUnitTestCaseBase_FileName = "ScalaJUnitTestCaseBase.scala"
  private val ScalaJUnitCommonTests_FileName = "ScalaCommonTests.scala"

  addSourceFile(ScalaJUnitTestCase_FileName,
    """class ScalaJUnitTestCase extends ScalaJUnitTestCaseBase with ScalaJUnitCommonTests {
      |  def testFromScala1(): Unit = {
      |  }
      |
      |  def testFromScala2(): Unit = {
      |  }
      |}
      |""".stripMargin.trim()
  )
  addSourceFile(ScalaJUnitTestCaseBase_FileName,
    """import junit.framework.TestCase
      |
      |abstract class ScalaJUnitTestCaseBase extends TestCase {
      |  def testFromBase1(): Unit = {
      |  }
      |
      |  def testFromBase2(): Unit = {
      |  }
      |}
      |""".stripMargin.trim()
  )
  //NOTE: this trait should be in a separate file
  addSourceFile(ScalaJUnitCommonTests_FileName,
    """trait ScalaJUnitCommonTests {
      |  def testFromScalaTrait1(): Unit = {
      |  }
      |
      |  def testFromScalaTrait2(): Unit = {
      |  }
      |}
      |""".stripMargin.trim()
  )

  def testGoToInheritedTestMethod(): Unit = {
    runTestByLocation(loc(ScalaJUnitTestCase_FileName, 0, 5),
      assertIsJUnitClassConfiguration(_, "ScalaJUnitTestCase"),
      root => {
        assertJUnitTestTree(root, MyTestTreeNode("ScalaJUnitTestCase", "[root]", Seq(
          MyTestTreeNode("testFromScala1", "ScalaJUnitTestCase.testFromScala1"),
          MyTestTreeNode("testFromScala2", "ScalaJUnitTestCase.testFromScala2"),
          MyTestTreeNode("testFromScalaTrait1", "ScalaJUnitTestCase.testFromScalaTrait1"),
          MyTestTreeNode("testFromScalaTrait2", "ScalaJUnitTestCase.testFromScalaTrait2"),
          MyTestTreeNode("testFromBase1", "ScalaJUnitTestCase.testFromBase1"),
          MyTestTreeNode("testFromBase2", "ScalaJUnitTestCase.testFromBase2"),
        )))

        assertGoToSourceTest(root, TestNodePath("[root]", "ScalaJUnitTestCase.testFromScala1"), GoToLocation(ScalaJUnitTestCase_FileName, 1))
        assertGoToSourceTest(root, TestNodePath("[root]", "ScalaJUnitTestCase.testFromScala2"), GoToLocation(ScalaJUnitTestCase_FileName, 4))

        assertGoToSourceTest(root, TestNodePath("[root]", "ScalaJUnitTestCase.testFromScalaTrait1"), GoToLocation(ScalaJUnitCommonTests_FileName, 1))
        assertGoToSourceTest(root, TestNodePath("[root]", "ScalaJUnitTestCase.testFromScalaTrait2"), GoToLocation(ScalaJUnitCommonTests_FileName, 4))

        assertGoToSourceTest(root, TestNodePath("[root]", "ScalaJUnitTestCase.testFromBase1"), GoToLocation(ScalaJUnitTestCaseBase_FileName, 3))
        assertGoToSourceTest(root, TestNodePath("[root]", "ScalaJUnitTestCase.testFromBase2"), GoToLocation(ScalaJUnitTestCaseBase_FileName, 6))
      }
    )
  }
}
