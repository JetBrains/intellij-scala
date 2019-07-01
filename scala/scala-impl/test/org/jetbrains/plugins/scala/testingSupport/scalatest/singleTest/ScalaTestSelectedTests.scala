package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest.{FeatureSpecScopeTest, FlatSpecScopeTest, FreeSpecScopeTest, FunSpecScopeTest, WordSpecScopeTest}
import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged.{FlatSpecTaggedSingleTestTest, FreeSpecTaggedSingleTestTest}

/**
 * @author Roman.Shein
 * @since 22.01.2015.
 */
trait ScalaTestSelectedTests
  extends ScalaTestSingleTestTest
    with Spec2SingleTestTest
    with FeatureSpecScopeTest
    with FreeSpecScopeTest
    with FunSpecScopeTest
    with WordSpecScopeTest
    with FlatSpecTaggedSingleTestTest
    with FlatSpecScopeTest
    with FreeSpecTaggedSingleTestTest
