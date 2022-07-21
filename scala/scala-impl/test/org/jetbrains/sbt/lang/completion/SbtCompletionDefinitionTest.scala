package org.jetbrains.sbt
package lang.completion

import org.jetbrains.plugins.scala.base.SharedTestProjectToken

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

abstract class SbtCompletionDefinitionTestBase extends SbtCompletionTestBase {
  self: MockSbtBase =>

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

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