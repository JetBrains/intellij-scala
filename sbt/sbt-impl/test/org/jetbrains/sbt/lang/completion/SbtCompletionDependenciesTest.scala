package org.jetbrains.sbt
package lang.completion

class SbtCompletionDependenciesTest extends SbtFileTestDataCompletionTestBase with MockSbt_1_0 {
  def testCompleteVersion(): Unit = doTest()
  def testCompleteGroupArtifact(): Unit = doTest()
}
