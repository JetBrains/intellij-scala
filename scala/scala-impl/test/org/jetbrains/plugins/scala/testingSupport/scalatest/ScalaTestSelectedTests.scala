package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.scalatest.scopeTest._
import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.{ScalaTestSingleTestTest, SpecSingleTestTest}
import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged.{FlatSpecTaggedSingleTestTest, FreeSpecTaggedSingleTestTest}

trait ScalaTestSelectedTests extends ScalaTestTestCase
  with ScalaTestSelectedSingleTests
  with ScalaTestSelectedScopeTests

trait ScalaTestSelectedSingleTests extends ScalaTestTestCase
  with ScalaTestSingleTestTest
  with SpecSingleTestTest
  with FlatSpecTaggedSingleTestTest
  with FreeSpecTaggedSingleTestTest

trait ScalaTestSelectedScopeTests extends ScalaTestTestCase
  with FeatureSpecScopeTest
  with FreeSpecScopeTest
  with FunSpecScopeTest
  with WordSpecScopeTest
  with FlatSpecScopeTest
