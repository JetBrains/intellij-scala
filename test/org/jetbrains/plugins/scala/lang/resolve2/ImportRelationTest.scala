package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportRelationTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/relation/"
  }

  def testStub = Assert.assertTrue(true)

  //TODO
//  def testAbsolute = doTest
  //TODO
//  def testClash = doTest
  //TODO
//  def testRelative = doTest
  //TODO
//  def testRoot = doTest
}