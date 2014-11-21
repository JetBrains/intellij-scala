package org.jetbrains.sbt
package lang.completion

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

class CompletionDefinitionTest extends CompletionTestBase {
  def testObjectValues
    = doTest
  def testLocalVars
    = doTest
  def testJavaEnumValues
    = doTest
  def testScopes
    = doTest
  def testSequences
    = doTest
}
