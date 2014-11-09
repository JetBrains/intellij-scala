package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

class Specs2TestFramework extends AbstractTestFramework {

  def getDefaultSuperClass: String = "org.specs2.mutable.Specification"

  def getName: String = "Specs2"

  def getMarkerClassFQName: String = "org.specs2.specification.SpecificationStructure"

  def getMnemonic: Char = 'p'
}
