package org.jetbrains.plugins.scala
package lang.adjustTypes.tests

import org.jetbrains.plugins.scala.lang.adjustTypes.AdjustTypesTestBase

/**
 * Nikolay.Tropin
 * 7/11/13
 */
class AdjustTypesTests extends AdjustTypesTestBase {

  def testSimpleJava() = doTest()

  def testParameterizedJava() = doTest()

  def testJavaWithImportAlias() = doTest()

  def testJavaInnerClasses() = doTest()

  def testPackagings() = doTest()

  def testAmbiguity() = doTest()

  def testSeveralLevelImports() = doTest()

  def testTypeAlias() = doTest()

  def testTypeProjection() = doTest()

  def testThisType() = doTest()
}
