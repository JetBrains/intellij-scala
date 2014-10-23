package org.jetbrains.plugins.scala.lang.resolve2


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "import/"
  }

  def testBrokenChain() {doTest()}
  def testLocal1() {doTest()}
  def testLocal2() {doTest()}
  def testRedundantImport() {doTest()}
  def testRenamed() {doTest()}
  def testSelection() {doTest()}
  def testSingle() {doTest()}
  def testHardImport() {doTest()}
}