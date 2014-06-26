package org.jetbrains.plugins.scala
package refactoring.extractMethod

/**
 * Nikolay.Tropin
 * 2014-05-19
 */
class ScalaExtractMethodDuplicatesTest extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "duplicates/"

  def testSimpleDuplicate() = doTest()
  def testSeveralDuplicates() = doTest()
  def testWithDefinition() = doTest()
  def testNoSearchGeneric() = doTest()
  def testNoSearchReturn() = doTest()
  def testSeveralOutputs() = doTest()
  def testDifferentType() = doTest()
  def testWithSemicolon() = doTest()
  def testParameterAsQualifier() = doTest()
  def testStringPlusMethod() = doTest()
  def testDifferentInterpolatedStringLiterals() = doTest()
  def testSameInterpolatedStringLiterals() = doTest()
  def testDifferentLiterals() = doTest()
}
