package org.jetbrains.plugins.scala.testingSupport.utest

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestRunConfiguration

abstract class UTestTestCase extends ScalaTestingTestCase {

  def uTestVersion: Version

  override protected def buildVersionsDetailsMessage: String =
    super.buildVersionsDetailsMessage + s", uTest: ${uTestVersion.presentation}"

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    // transitive also fetches the "portable scala" library that used in the uTest runners (org.jetbrains.plugins.scala.testingSupport.uTest.UTestSuiteRunner)
    IvyManagedLoader(("com.lihaoyi" %% "utest" % uTestVersion.presentation).transitive())
  )

  override protected val expectedDefaultRunConfigurationClass: Class[UTestRunConfiguration] =
    classOf[UTestRunConfiguration]

  // TestRunnerUtil.unescapeTestNam is not used in UTestRunner
  override protected def unescapeTestName(str: String): String = str
}

object UTestTestCase {
  object LatestVersions {
    val UTest_0_8: Version = Version("0.8.9")
    val UTest_0_9: Version = Version("0.9.1")
  }
}