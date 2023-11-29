package org.jetbrains.plugins.scala.base

import com.intellij.testFramework.TestIndexingModeSupporter
import com.intellij.testFramework.TestIndexingModeSupporter.{EmptyIndexSuite, FullIndexSuite, RuntimeOnlyIndexSuite}
import junit.framework.{Test, TestCase, TestSuite}

import scala.reflect.{ClassTag, classTag}
import scala.util.chaining.scalaUtilChainingOps

/**
 * Dumb Mode testing utility. Use to check functionality during indexing (SCL-21849)
 *
 * @see [[org.jetbrains.plugins.scala.lang.navigation.GoToClassAndSymbolTest]] companion object
 */
abstract class TestIndexingModeSupporterCompanion[T <: TestCase with TestIndexingModeSupporter : ClassTag] {
  final def suite(): Test = new TestSuite().tap { suite =>
    val runtimeClass = classTag[T].runtimeClass

    val testCaseSubclass = runtimeClass.asSubclass(classOf[TestCase])
    suite.addTestSuite(testCaseSubclass)

    val testIndexingModeSupporterSubclass = runtimeClass.asSubclass(classOf[TestIndexingModeSupporter])
    TestIndexingModeSupporter.addTest(testIndexingModeSupporterSubclass, new FullIndexSuite, suite)
    TestIndexingModeSupporter.addTest(testIndexingModeSupporterSubclass, new RuntimeOnlyIndexSuite, suite)
    TestIndexingModeSupporter.addTest(testIndexingModeSupporterSubclass, new EmptyIndexSuite, suite)
  }
}
