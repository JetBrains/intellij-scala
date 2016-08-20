package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
  * @author Victor Malov
  * 2016-08-16
  */
class ForeachAnonFunctionParameterTest extends OperationsOnCollectionInspectionTest {

  val hint = InspectionBundle.message("convertible.to.method.value.name")
  override val inspectionClass = classOf[SimplifiableForeachInspection]

  def test_1() {
    val selected = s"""Seq("1", "2").${START}foreach(s => foo(s))$END"""
    check(selected)
    val text = """Seq("1", "2").foreach(s => foo(s))"""
    val result = """Seq("1", "2").foreach(foo)"""
    testFix(text, result, hint)
  }

  def test_2() {
    val selected = s"""Seq("1", "2").${START}foreach((s) => foo(s))$END"""
    check(selected)
    val text = """Seq("1", "2").foreach((s) => foo(s))"""
    val result = """Seq("1", "2").foreach(foo)"""
    testFix(text, result, hint)
  }
}