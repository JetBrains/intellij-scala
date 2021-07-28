package org.jetbrains.sbt
package lang.completion

/**
 * @author Nikolay Obedin
 * @since 8/1/14.
 */
class SbtCompletionDependenciesTest extends SbtCompletionTestBase with MockSbt_1_0 {
  def testCompleteVersion(): Unit = doTest()
  def testCompleteGroupArtifact(): Unit = doTest()
}
