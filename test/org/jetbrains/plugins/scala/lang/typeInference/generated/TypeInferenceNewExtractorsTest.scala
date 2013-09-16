package org.jetbrains.plugins.scala
package lang.typeInference.generated

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Alefas
 * @since 16.09.13
 */
class TypeInferenceNewExtractorsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "newExtractors/"

  protected override def getDefaultScalaSDKVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  protected override def setUp() {
    super.setUp()
  }

  def testUnapply() {doTest()}
  def testUnapply2() {doTest()}
  def testUnapplySeq() {doTest()}
  def testUnapplySeq2() {doTest()}
}
