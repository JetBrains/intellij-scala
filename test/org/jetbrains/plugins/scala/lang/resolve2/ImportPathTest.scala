package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportPathTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/path/"
  }

  def testStub = Assert.assertTrue(true)

  //TODO
//  def testDir = doTest
  //TODO
//  def testDirAndLocal = doTest
  //TODO
//  def testDirThenLocal = doTest
  //TODO
//  def testTwoLocal = doTest
}