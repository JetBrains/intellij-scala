package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class ImportPathTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "import/path/"
  }

  override def allSourcesFromDirectory: Boolean = true

  def testDir = doTest
  //TODO ok
//  def testDirAndLocal = doTest
  def testDirThenLocal = doTest
  //TODO ok
//  def testTwoLocal = doTest
}