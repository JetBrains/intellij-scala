package org.jetbrains.plugins.scala
package lang
package typeInference
package generated

class TypeInferenceXmlTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_10

  override def folderPath: String = super.folderPath + "xml/"

  def testCDSect(): Unit = doTest()

  def testComment(): Unit = doTest()

  def testElement(): Unit = doTest()

  def testEmptyElement(): Unit = doTest()

  def testNodeBuffer(): Unit = doTest()

  def testProcInstr(): Unit = doTest()

  def testSCL3542(): Unit = doTest()
}