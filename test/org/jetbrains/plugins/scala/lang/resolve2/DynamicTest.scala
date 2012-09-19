package org.jetbrains.plugins.scala
package lang.resolve2

/**
 * Pavel.Fatin, 02.02.2010
 */

class DynamicTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "dynamic/"
  }

  def testApplyDynamic() { doTest() }
  def testApplyDynamicNoMethod() { doTest() }
  def testApplyDynamicOrdinaryType() { doTest() }
  def testApplyDynamicWrongSygnature() { doTest() }
}