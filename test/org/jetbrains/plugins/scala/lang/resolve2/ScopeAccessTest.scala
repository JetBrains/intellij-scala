package org.jetbrains.plugins.scala.lang.resolve2

import junit.framework.Assert


/**
 * Pavel.Fatin, 02.02.2010
 */

class ScopeAccessTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "scope/access/"
  }

  def testPrivateCompanionClass = doTest
  def testPrivateCompanionObject = doTest
  def testPrivateThisCompanionClass = doTest
  def testPrivateThisCompanionObject = doTest
}