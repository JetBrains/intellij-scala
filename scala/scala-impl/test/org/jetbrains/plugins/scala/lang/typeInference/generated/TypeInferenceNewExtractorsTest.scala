package org.jetbrains.plugins.scala
package lang
package typeInference
package generated

/**
 * @author Alefas
 * @since 16.09.13
 */
class TypeInferenceNewExtractorsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "newExtractors/"

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_11

  def testUnapply() {doTest()}
  def testUnapply2() {doTest()}
  def testUnapplySeq() {doTest()}
  def testUnapplySeq2() {doTest()}
  def testUnapplyWithImplicitParam() {doTest()}
  def testUnapplySeqWithImplicitParam() {doTest()}
}
