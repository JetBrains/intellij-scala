package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportRelationTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/relation/"
  }

  def testAbsolute = doTest
  def testClash = doTest
  def testRelative = doTest
  def testRoot = doTest
}