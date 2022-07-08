package org.jetbrains.plugins.scala
package refactoring.extractMethod

class ScalaExtractMethodDuplicatesTest extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "duplicates/"

  def testSimpleDuplicate(): Unit = doTest()
  def testSeveralDuplicates(): Unit = doTest()
  def testWithDefinition(): Unit = doTest()
  def testNoSearchGeneric(): Unit = doTest()
  def testNoSearchReturn(): Unit = doTest()
  def testSeveralOutputs(): Unit = doTest()
  def testDifferentType(): Unit = doTest()
  def testWithSemicolon(): Unit = doTest()
  def testParameterAsQualifier(): Unit = doTest()
  def testStringPlusMethod(): Unit = doTest()
  def testDifferentInterpolatedStringLiterals(): Unit = doTest()
  def testSameInterpolatedStringLiterals(): Unit = doTest()
  def testDifferentLiterals(): Unit = doTest()
}
