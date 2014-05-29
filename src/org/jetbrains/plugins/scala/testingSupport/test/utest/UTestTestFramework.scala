package org.jetbrains.plugins.scala
package testingSupport.test.utest

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

/**
 * @author Roman.Shein
 *         Date: 21.04.14
 */
class UTestTestFramework extends AbstractTestFramework {

  def getDefaultSuperClass: String = "utest.framework.TestSuite"

  def getName: String = "uTest"

  def getMarkerClassFQName: String = "utest.framework.TestSuite"

  def getMnemonic: Char = 'm'
}
