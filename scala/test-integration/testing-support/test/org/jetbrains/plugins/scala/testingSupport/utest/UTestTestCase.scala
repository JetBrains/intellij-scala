package org.jetbrains.plugins.scala.testingSupport.utest

import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestConfigurationProducer

abstract class UTestTestCase extends ScalaTestingTestCase {
  private val LatestUtestVersion = "0.8.1"

  override protected def additionalLibraries: Seq[LibraryLoader] = {
    IvyManagedLoader("com.lihaoyi" %% "utest" % LatestUtestVersion) :: Nil
  }

  override protected lazy val configurationProducer: AbstractTestConfigurationProducer[_] =
    RunConfigurationProducer.getInstance(classOf[UTestConfigurationProducer])

  protected val testSuiteSecondPrefix = "import utest.framework.TestSuite"

  // TestRunnerUtil.unescapeTestNam is not used in UTestRunner
  override protected def unescapeTestName(str: String): String = str
}
