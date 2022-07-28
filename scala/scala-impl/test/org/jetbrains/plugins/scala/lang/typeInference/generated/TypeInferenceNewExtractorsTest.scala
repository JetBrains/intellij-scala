package org.jetbrains.plugins.scala
package lang
package typeInference
package generated

class TypeInferenceNewExtractorsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "newExtractors/"

  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_11

  def testUnapply(): Unit = {doTest()}
  def testUnapply2(): Unit = {doTest()}
  def testUnapplySeq(): Unit = {doTest()}
  def testUnapplySeq2(): Unit = {doTest()}
  def testUnapplyWithImplicitParam(): Unit = {doTest()}
  def testUnapplySeqWithImplicitParam(): Unit = {doTest()}
}
