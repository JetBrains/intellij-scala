package org.jetbrains.plugins.scala
package testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration

/**
 * @author Roman.Shein
 *         Date: 03.03.14
 */
trait ScalaTestSingleTestTest extends FeatureSpecSingleTestTest with FlatSpecSingleTestTest with
FreeSpecPathSingleTestTest with FreeSpecSingleTestTest with FunSuiteSingleTestTest with
PropSpecSingleTestTest with WordSpecSingleTestTest {
}
