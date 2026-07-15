package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.corpus.scala3.ScalatestTest
import org.junit.Test

class ScalatestSemanticTest extends SemanticTestBase(ScalatestTest) {
  @Test def orgScalatestAsyncSuperEngine(): Unit = doTest("org.scalatest.AsyncSuperEngine")

  @Test def orgScalatestAsyncTestSuite(): Unit = doTest("org.scalatest.AsyncTestSuite")

  @Test def orgScalatestDoc(): Unit = doTest("org.scalatest.Doc")

  @Test def orgScalatestSuperEngine(): Unit = doTest("org.scalatest.SuperEngine")
}