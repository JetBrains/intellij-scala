package org.jetbrains.sbt
package lang.completion

import org.jetbrains.plugins.scala.packagesearch.api.{PackageSearchClient, PackageSearchClientTesting}
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil

class SbtCompletionDependenciesTest
  extends SbtFileTestDataCompletionTestBase
    with MockSbt_1_0
    with PackageSearchClientTesting {

  private val GROUP_ID = "org.scalatest"
  private val versions = Seq("3.0.8", "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")
  private val apiPackage = apiMavenPackage(GROUP_ID, "scalatest", versionsContainer("3.0.8", Some("3.0.8"),
    versions))


  def testCompleteVersion(): Unit = {
    DependencyUtil.updateMockVersionCompletionCache((GROUP_ID -> "scalatest") -> versions)
    doTest()
  }

  def testCompleteGroupArtifact(): Unit = {
    PackageSearchClient.instance().updateByQueryCache(GROUP_ID, "", java.util.Arrays.asList(apiPackage))
    doTest()
  }
}
