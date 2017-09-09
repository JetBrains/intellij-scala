package org.jetbrains.sbt
package lang.completion

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

class SbtCompletionDefinitionTest extends SbtCompletionTestBase {
  def testObjectValues(): Unit = doTest()
  def testLocalVars(): Unit = doTest()
  def testJavaEnumValues(): Unit = doTest()
  def testScopes(): Unit = doTest()
  def testSequences(): Unit = doTest()
}
