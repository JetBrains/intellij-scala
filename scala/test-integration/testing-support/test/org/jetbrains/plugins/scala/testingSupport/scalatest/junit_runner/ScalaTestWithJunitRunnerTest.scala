package org.jetbrains.plugins.scala.testingSupport.scalatest.junit_runner

import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.configurations.RunConfigCreationContext
import org.jetbrains.plugins.scala.testingSupport.scalatest._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestRunConfiguration

class ScalaTestWithJunitRunnerTest extends ScalaTestTestCase
  with WithScalaTest_3_2
  with WithScala_2_13 {

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

  def testRunAllInPackage_AsJunit(): Unit =
    runTestByLocation(
      RunConfigCreationContext(packageLoc("org.example"), Some(classOf[JUnitConfiguration])),
      config => {
        assertPackageConfigAndSettings(config, "org.example", "org.example in testRunAllInPackage_AsJunit")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "example", "JavaJUnitTest", "testJUnitTest1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "example", "JavaJUnitTest", "testJUnitTest2"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "example", "ScalaScalaTestWithJUnitRunner", "should test 1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "example", "ScalaScalaTestWithJUnitRunner", "should test 2")
        ))
      }
    )

  def testRunAllInPackage_AsScalaTest(): Unit =
    runTestByLocation(
      RunConfigCreationContext(packageLoc("org.example"), Some(classOf[ScalaTestRunConfiguration])),
      config => {
        assertPackageConfigAndSettings(config, "org.example", "ScalaTests in 'example'")
      },
      root => {
        assertResultTreePathsEqualsUnordered(root)(Seq(
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "ScalaScalaTestWithJUnitRunner", "should test 1"),
          TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", "ScalaScalaTestWithJUnitRunner", "should test 2")
        ))
      }
    )
}
