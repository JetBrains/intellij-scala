package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}

class ResolveExistentialDefinitionTest extends ScalaLightCodeInsightFixtureTestAdapter with SimpleResolveTestBase {

  def testSimple(): Unit = {
    doResolveTest(
      s"""
         |trait Foo[A]
         |
         |val x: Foo[${REFSRC}X] forSome { type ${REFTGT}X <: Int }
      """.stripMargin)
  }

  def testRecursive(): Unit = {
    doResolveTest(
      s"""
         |trait Foo[A]
         |
         |val x: Foo[X] forSome { type ${REFTGT}X <: Foo[${REFSRC}X] }
      """.stripMargin)
  }

  def testForwardRef(): Unit = {
    doResolveTest(
      s"""
         |trait Foo[A]
         |trait Bar[A, B]
         |
         |val yy: Bar[K, V] forSome {type V <: Foo[${REFSRC}K]; type ${REFTGT}K} = ???
      """.stripMargin)
  }

  def testValue(): Unit = {
    doResolveTest(
      s"""
         |class Ref[T]
         |abstract class Outer { type T }
         |
         |val ref: Ref[${REFSRC}x.T] forSome { val ${REFTGT}x: Outer }
     """.stripMargin)
  }

  def testSCL10548(): Unit = {
    doResolveTest(
      s"""
         |type A = Set[X] forSome { type X <: ${REFSRC}Y; type ${REFTGT}Y <: Int}
        """.stripMargin)
  }



}
