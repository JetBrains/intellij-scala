package org.jetbrains.plugins.scala.testingSupport.utest

import junit.framework.TestCase
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestSbtTestRunningSupport.UTestSbtCommandsBuilder
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions._

class UTestSbtCommandsBuilderTest extends TestCase {

  private def doTest(
    classToTests: Map[String, Set[String]],
    expectedCommands: Seq[String]
  ): Unit = {
    val actual = (new UTestSbtCommandsBuilder).buildTestOnly(classToTests)
    assertCollectionEquals(
      expectedCommands.sorted,
      actual.sorted
    )
  }

  def testEmpty(): Unit =
    doTest(
      Map.empty[String, Set[String]],
      Seq.empty[String]
    )

  def testSingleClass(): Unit =
    doTest(
      Map("org.example.MyClass" -> Set()),
      Seq("-- org.example.MyClass"),
    )

  def testSeveralClasses(): Unit =
    doTest(
      Map("org.example.MyClass1" -> Set(), "org.example.MyClass2" -> Set()),
      Seq("-- org.example.MyClass1", "-- org.example.MyClass2"),
    )

  def testSeveralClassesWithTests(): Unit = {
    val classesWithTests = Map(
      "org.example.MyClass1" -> Set(
        """tests\testName""",
        """tests\testName\innerTestName"""
      ),
      "org.example.MyClass2" -> Set(
        """tests\test with spaces""",
        """tests\test with spaces\inner test with spaces 1""",
        """tests\test with spaces\inner test with spaces 2"""
      )
    )
    val expected = Seq(
      """-- org.example.MyClass1.testName""",
      """-- org.example.MyClass1.testName.innerTestName""",
      """-- "org.example.MyClass2.test with spaces"""",
      """-- "org.example.MyClass2.test with spaces.inner test with spaces 1"""",
      """-- "org.example.MyClass2.test with spaces.inner test with spaces 2"""",
    )
    doTest(
      classesWithTests,
      expected,
    )
  }
}