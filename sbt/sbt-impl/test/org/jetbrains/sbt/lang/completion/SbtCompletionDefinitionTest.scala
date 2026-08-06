package org.jetbrains.sbt
package lang.completion

abstract class SbtCompletionDefinitionTestBase extends SbtFileTestDataCompletionTestBase {
  self: MockSbtBase =>

  def testObjectValues(): Unit = doTest()
  def testLocalVars(): Unit = doTest()
  def testJavaEnumValues(): Unit = doTest()
  def testScopes(): Unit = doTest()
}

class SbtCompletionDefinitionTest_0_13 extends SbtCompletionDefinitionTestBase with MockSbt_0_13 {
  def testIvyConfigurations_0_13(): Unit = doTest()
}

class SbtCompletionDefinitionTest_1_0 extends SbtCompletionDefinitionTestBase with MockSbt_1_0 {
  def testIvyConfigurations_1_0(): Unit = doTest()
}