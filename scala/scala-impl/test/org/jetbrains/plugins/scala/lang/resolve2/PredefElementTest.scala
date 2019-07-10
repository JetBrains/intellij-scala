package org.jetbrains.plugins.scala
package lang
package resolve2

abstract class PredefElementTestBase extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "predef/element/"
  }
}

class PredefElementTest extends PredefElementTestBase {
  def testClass(): Unit = doTest()
  //TODO getClass
  //def testCompanionObject = doTest
  def testFunction(): Unit = doTest()
  def testObject(): Unit = doTest()
  //TODO packageobject
  //def testPackage = doTest
  def testTypeAlias(): Unit = doTest()
}


class PredefElementTest_with_ScalaObject extends PredefElementTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version < Scala_2_11

  def testTrait(): Unit = doTest()
}

