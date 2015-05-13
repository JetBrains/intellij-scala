package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest.{WordSpecScopeTest, FunSpecScopeTest,
FreeSpecScopeTest, FeatureSpecScopeTest}

/**
 * @author Roman.Shein
 * @since 22.01.2015.
 */
trait ScalaTestSelectedTests extends ScalaTestSingleTestTest with Spec2SingleTestTest with FeatureSpecScopeTest with
FreeSpecScopeTest with FunSpecScopeTest with WordSpecScopeTest {

}
