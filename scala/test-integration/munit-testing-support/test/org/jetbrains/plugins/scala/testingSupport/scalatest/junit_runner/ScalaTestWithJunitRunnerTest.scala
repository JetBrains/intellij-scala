package org.jetbrains.plugins.scala.testingSupport.scalatest.junit_runner

import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.testframework.SearchForTestsTask
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.configurations.RunConfigCreationContext
import org.jetbrains.plugins.scala.testingSupport.junit.JUnitIntegrationTestConfigAssertions
import org.jetbrains.plugins.scala.testingSupport.scalatest._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration
import org.jetbrains.plugins.scala.util.RevertableChange

class ScalaTestWithJunitRunnerTest extends ScalaTestTestCase
  with WithScalaTest_3_2
  with WithScala_2_13
  with JUnitIntegrationTestConfigAssertions {

  override protected def setUp(): Unit = {
    super.setUp()

    // ATTENTION:
    // Without enabling the `CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY`, test "testRunAllInPackage_AsJUnit" will hang
    // and terminate by timeout (10 seconds) in ScalaTestingTestCase.waitForTestEnd.
    // Under the hood the test runner process will hang reading from the socket for information from IntelliJ that tests have been discovered.
    // (search for "SOCKET" in com.intellij.rt.junit.JUnitStarter.processParameters)
    //
    // This happens because currently JUnit test running is not well-designed for being run in unit tests when it involves test search.
    // And for the "all in package" test kind, it needs to search for tests.
    // If you go to `com.intellij.execution.testframework.SearchForTestsTask.startSearch` you will notice
    // that when it starts the test search, it doesn't invoke "finish" in the end.
    // Or, it pretends to do it by calling "onFound", but its implementation from `TestPackage.createSearchingForTestsTask`
    // doesn't invoke the "finish" method that is responsible for pinging the junit process via the socket.
    //
    // So in this workaround we rely on `SearchForTestsTask.startSearch`
    // to run the true production logic if `CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY` is enabled.
    //
    // NOTE: IntelliJ IDEA Java plugin also has integration tests for JUnit tests in `community/plugins/junit5_rt_tests/test/com/intellij/junit4`
    // There it works fine because their base test cases reimplement a lot of the process machinery
    // in `com.intellij.java.execution.AbstractTestFrameworkIntegrationTest#doStartTestsProcess`
    // including invocation of `searchForTestsTask.onSuccess()` that invokes the `finish` method.
    // (it reimplements some parts of com.intellij.execution.JavaTestFrameworkRunnableState#execute/#createHandler)
    // Ideally, this should not be the case, and the tests should reuse as much prod logic as possible.
    // But it's probably the "historical reasons" and no one every rewrote this part of the tests.
    RevertableChange
      .withModifiedTestModeFlag(SearchForTestsTask.CONNECT_IN_UNIT_TEST_MODE_PROPERTY_KEY, value = true)
      .applyChange(this)
  }

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    // Example:
    // ScalaTest version     : 3.2.16
    // ScalaTestPlus version : 3.2.16.0
    // (it's enough to use the first version for our needs)
    val scalatestPlusVersion = ScalaTestLatestVersions.Scalatest_3_2 + ".0"
    super.librariesLoaders ++ Seq(
      IvyManagedLoader(("org.scalatestplus" %% "junit-4-13" % scalatestPlusVersion).transitive())
    )
  }

  addSourceFile("org/example/JavaJUnitTest.java",
    //language=Java
    """package org.example;
      |
      |import org.junit.Test;
      |
      |public class JavaJUnitTest {
      |
      |    @Test
      |    public void testJUnitTest1() {
      |    }
      |
      |    @Test
      |    public void testJUnitTest2() {
      |    }
      |}
      |""".stripMargin
  )

  addSourceFile("org/example/ScalaScalaTestWithJUnitRunner.scala",
    //language=Scala
    """package org.example
      |
      |import org.junit.runner.RunWith
      |import org.scalatest.flatspec.AnyFlatSpecLike
      |import org.scalatest.matchers.must.Matchers
      |import org.scalatestplus.junit.JUnitRunner
      |
      |@RunWith(classOf[JUnitRunner])
      |class ScalaScalaTestWithJUnitRunner extends AnyFlatSpecLike with Matchers {
      |  it should "test 1" in {
      |  }
      |
      |  it should "test 2" in {
      |  }
      |}
      |""".stripMargin
  )

  def testRunAllInPackage_AsJUnit(): Unit =
    runTestByLocation(
      RunConfigCreationContext(packageLoc("org.example"), Some(classOf[JUnitConfiguration])),
      config => {
        assertRunConfigTestPackage(config, "org.example")
        assertRunConfigName(config, "org.example in testRunAllInPackage_AsJUnit")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "JavaJUnitTest", "JavaJUnitTest.testJUnitTest1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "JavaJUnitTest", "JavaJUnitTest.testJUnitTest2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "ScalaScalaTestWithJUnitRunner", "ScalaScalaTestWithJUnitRunner.should test 1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "ScalaScalaTestWithJUnitRunner", "ScalaScalaTestWithJUnitRunner.should test 2")
        ))
      }
    )

  def testRunAllInPackage_AsScalaTest(): Unit =
    runTestByLocation(
      RunConfigCreationContext(packageLoc("org.example"), Some(classOf[ScalaTestRunConfiguration])),
      config => {
        assertRunConfigTestPackage(config, "org.example")
        assertRunConfigName(config, "ScalaTests in 'example'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "ScalaScalaTestWithJUnitRunner", "should test 1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "ScalaScalaTestWithJUnitRunner", "should test 2")
        ))
      }
    )
}
