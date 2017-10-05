package org.jetbrains.plugins.scala
package lang.typeInference.generated

import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
 * @author Alefas
 * @since 16.09.13
 */
class TypeInferenceNewExtractorsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "newExtractors/"

  override implicit val version: ScalaVersion = Scala_2_11

  def testUnapply() {doTest()}
  def testUnapply2() {doTest()}
  def testUnapplySeq() {doTest()}
  def testUnapplySeq2() {doTest()}
  def testUnapplyWithImplicitParam() {doTest()}
  def testUnapplySeqWithImplicitParam() {doTest()}
}
