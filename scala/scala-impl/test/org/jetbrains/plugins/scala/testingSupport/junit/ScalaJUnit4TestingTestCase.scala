package org.jetbrains.plugins.scala.testingSupport.junit

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

class ScalaJUnit4TestingTestCase extends ScalaJUnitTestingTestCaseBase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("junit" % "junit" % "4.13.2").transitive())
  )

  private val ScalaJUnit4_Tests_FileName = "ScalaJUnit4_Tests.scala"
  addSourceFile(ScalaJUnit4_Tests_FileName,
    """import junit.framework.TestCase
      |import org.junit.Test
      |
      |class ScalaJUnit4_Test_TestCase extends TestCase {
      |  def test1(): Unit = {}
      |  def test2(): Unit = {}
      |}
      |
      |class ScalaJUnit4_Test_TestAnnotation {
      |  @Test
      |  def test3(): Unit = {}
      |  @Test
      |  def test4(): Unit = {}
      |}
      |""".stripMargin.trim()
  )

  def testTestCase_WholeSuite(): Unit = {
    runTestByLocation(loc(ScalaJUnit4_Tests_FileName, 3, 10),
      assertIsJUnitClassConfiguration(_, "ScalaJUnit4_Test_TestCase"),
      assertJUnitTestTree(_, MyTestTreeNode("ScalaJUnit4_Test_TestCase", "[root]", Seq(
        MyTestTreeNode("test1", "ScalaJUnit4_Test_TestCase.test1"),
        MyTestTreeNode("test2", "ScalaJUnit4_Test_TestCase.test2")
      )))
    )
  }

  def testTestCase_SingleTest(): Unit = {
    runTestByLocation(loc(ScalaJUnit4_Tests_FileName, 5, 10),
      assertIsJUnitTestMethodConfiguration(_, "ScalaJUnit4_Test_TestCase", "test2"),
      assertJUnitTestTree(_, MyTestTreeNode("ScalaJUnit4_Test_TestCase", "[root]", Seq(
        MyTestTreeNode("test2", "ScalaJUnit4_Test_TestCase.test2")
      )))
    )
  }

  def testTestAnnotation_WholeSuite(): Unit = {
    runTestByLocation(loc(ScalaJUnit4_Tests_FileName, 8, 10),
      assertIsJUnitClassConfiguration(_, "ScalaJUnit4_Test_TestAnnotation"),
      assertJUnitTestTree(_, MyTestTreeNode("ScalaJUnit4_Test_TestAnnotation", "[root]", Seq(
        MyTestTreeNode("test3", "ScalaJUnit4_Test_TestAnnotation.test3"),
        MyTestTreeNode("test4", "ScalaJUnit4_Test_TestAnnotation.test4")
      )))
    )
  }

  def testTestAnnotation_SingleTest(): Unit = {
    runTestByLocation(loc(ScalaJUnit4_Tests_FileName, 12, 10),
      assertIsJUnitTestMethodConfiguration(_, "ScalaJUnit4_Test_TestAnnotation", "test4"),
      assertJUnitTestTree(_, MyTestTreeNode("ScalaJUnit4_Test_TestAnnotation", "[root]", Seq(
        MyTestTreeNode("test4", "ScalaJUnit4_Test_TestAnnotation.test4")
      )))
    )
  }
}
