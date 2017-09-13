package org.jetbrains.sbt
package lang.completion

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

abstract class SbtCompletionDefinitionTestBase extends SbtCompletionTestBase {
  def testObjectValues(): Unit = doTest()
  def testLocalVars(): Unit = doTest()
  def testJavaEnumValues(): Unit = doTest()
  def testScopes(): Unit = doTest()
}

class SbtCompletionDefinitionTest_0_13 extends SbtCompletionDefinitionTestBase with MockSbt_0_13 {
  override val sbtVersion = Sbt.Latest_0_13

  def testIvyConfigurations_0_13(): Unit = doTest()
}

class SbtCompletionDefinitionTest_1_0 extends SbtCompletionDefinitionTestBase with MockSbt_1_0 {
  override val sbtVersion = Sbt.LatestVersion

  def testIvyConfigurations_1_0(): Unit = doTest()
}