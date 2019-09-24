package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest._
import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged.{FlatSpecTaggedSingleTestTest, FreeSpecTaggedSingleTestTest}

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
