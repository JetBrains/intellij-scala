package org.jetbrains.plugins.scala
package refactoring.introduceField

class IntroduceFieldTests extends IntroduceFieldTestBase {
  def testSimple(): Unit = doTest()
  def testSimpleReplaceAll(): Unit = doTest()
  def testSimpleReplaceOne(): Unit = doTest()
  def testSimpleFromMethodInitInDecl(): Unit = doTest()
  def testSimpleFromMethodInitLocally(): Unit = doTest()
  def testSimpleInInner(): Unit = doTest()
  def testTwoMethodsInitInDecl(): Unit = doTest()
  def testFromAnonymousLocally(): Unit = doTest()
  def testFromAnonymousInDeclaration(): Unit = doTest(scType = null)
  def testReplaceAllInOuter(): Unit = doTest()
  def testFromBaseConstructorAddEarlyDefs(): Unit = doTest()
  def testFromBaseConstructorToEarlyDefs(): Unit = doTest()
  def testFromBaseConstructorToEarlyDefs2(): Unit = doTest()
}
