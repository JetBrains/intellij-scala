package org.jetbrains.plugins.scala
package lang
package resolve2

abstract class ImportElementTestBase extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/element/"
  }
}

class ImportElementTest extends ImportElementTestBase {

  def testCaseClass(): Unit = doTest()
  def testCompanion(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testPackage(): Unit = doTest()
  def testFunctionParameter(): Unit = doTest()
  def testInherited(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
  def testTypeAlias(): Unit = doTest()
  //TODO classparameter
//  def testCaseClassParameter = doTest
}

class ImportElementTest_without_AbstractMap extends ImportElementTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  <= LatestScalaVersions.Scala_2_10

  def testTrait(): Unit = doTest()
}