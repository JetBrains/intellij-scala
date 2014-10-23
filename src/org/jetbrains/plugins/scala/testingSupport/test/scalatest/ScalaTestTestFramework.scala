package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

class ScalaTestTestFramework extends AbstractTestFramework {

  def getDefaultSuperClass: String = "org.scalatest.FunSuite"

  def getName: String = "ScalaTest"

  def getMarkerClassFQName: String = "org.scalatest.Suite"

  def getMnemonic: Char = 'c'
}
