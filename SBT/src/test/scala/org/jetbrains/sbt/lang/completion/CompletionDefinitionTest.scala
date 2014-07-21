package org.jetbrains.sbt
package lang.completion

/**
 * Created by Nikolay Obedin on 7/17/14.
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
