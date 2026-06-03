package org.jetbrains.plugins.scala.testingSupport.scalatest.base

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.scopeTest._
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.ScalaTestSingleTestTest
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.tagged.{FlatSpecTaggedSingleTestTest, FreeSpecTaggedSingleTestTest}

trait ScalaTestSelectedTests extends ScalaTestTestCase
  with ScalaTestSelectedSingleTests
  with ScalaTestSelectedScopeTests

trait ScalaTestSelectedSingleTests extends ScalaTestTestCase
  with ScalaTestSingleTestTest
  with FlatSpecTaggedSingleTestTest
  with FreeSpecTaggedSingleTestTest

trait ScalaTestSelectedScopeTests extends ScalaTestTestCase
  with FeatureSpecScopeTest
  with FreeSpecScopeTest
  with FunSpecScopeTest
  with WordSpecScopeTest
  with FlatSpecScopeTest
