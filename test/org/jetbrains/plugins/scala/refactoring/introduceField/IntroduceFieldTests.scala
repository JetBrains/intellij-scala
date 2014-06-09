package org.jetbrains.plugins.scala
package refactoring.introduceField

/**
 * Nikolay.Tropin
 * 7/17/13
 */
class IntroduceFieldTests extends IntroduceFieldTestBase {
  def testSimple() = doTest()
  def testSimpleReplaceAll() = doTest()
  def testSimpleReplaceOne() = doTest()
  def testSimpleFromMethodInitInDecl() = doTest()
  def testSimpleFromMethodInitLocally() = doTest()
  def testSimpleInInner() = doTest()
  def testTwoMethodsInitInDecl() = doTest()
  def testFromAnonymousLocally() = doTest()
  def testFromAnonymousInDeclaration() = doTest()
  def testReplaceAllInOuter() = doTest()
  def testFromBaseConstructorAddEarlyDefs() = doTest()
  def testFromBaseConstructorToEarlyDefs() = doTest()
  def testFromBaseConstructorToEarlyDefs2() = doTest()
}
