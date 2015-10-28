package org.jetbrains.plugins.scala
package codeInspection.controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 2014-09-23
 */
class UselessExpressionInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaUselessExpressionInspection]
  override protected def annotation: String = "Useless expression"

  def testLiteral(): Unit = {
    val text = s"""def foo(): Int = {
                 |    if (true) return 1
                 |    else ${START}2$END
                 |
                 |    0
                 |}"""
    checkTextHasError(text)
  }

  def testTuple(): Unit = {
    val text = s"""def foo(): Unit = {
                 |    var x = 0
                 |    $START(0, 2)$END
                 |    0
                 |}"""
    checkTextHasError(text)
  }

  def testReference(): Unit = {
    val text = s"""def foo(): Unit = {
                 |    $START(0, 2)._1$END
                 |    0
                 |}"""
    checkTextHasError(text)
  }

  def testReferenceToVal(): Unit = {
    val text = s"""def foo(): Unit = {
                 |  val a = 1
                 |  ${START}a$END
                 |  0
                 |}"""
    checkTextHasError(text)
  }

  def testTypedAndParenthesized(): Unit = {
    val text = s"""def foo(): Unit = {
                   |  val s = "aaa"
                   |  $START(s: String).substring(0)$END
                   |  0
                   |}"""
    checkTextHasError(text)
  }

  def testReferenceToByNameParam(): Unit = {
    val text = s"""def foo(i: => Int): Int = {
                 |  ${START}i$END
                 |  0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testStringBuffer(): Unit = {
    val text = s"""def foo(): Int = {
                 |  val b = new StringBuffer()
                 |  ${START}b.append("a")$END
                 |  0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testObjectMethodWithSideEffects(): Unit = {
    val text = s"""def foo(): Int = {
                 |  $START"1".wait()$END
                 |  0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testImmutableCollection(): Unit = {
    val text = s"""def foo(): Int = {
                  |  0 match {
                  |    case 0 => ${START}List(1)$END
                  |    case 1 =>
                  |  }
                  |  1
                  |}"""
    checkTextHasError(text)
  }

  def testImmutableCollection2(): Unit = {
    val text = s"""def foo(): Int = {
                  |  0 match {
                  |    case 0 => ${START}List(1).dropRight(2)$END
                  |    case 1 =>
                  |  }
                  |  1
                  |}"""
    checkTextHasError(text)
  }

  def testImmutableCollection3(): Unit = {
    val text = s"""def foo(): Int = {
                  |  val f = (i: Int) => i + 1
                  |  0 match {
                  |    case 0 => ${START}List(1).foreach(f)$END
                  |    case 1 =>
                  |  }
                  |  1
                  |}"""
    checkTextHasNoErrors(text)
  }

  def testFunctionalParam(): Unit = {
    val text =
      s"""def foo(f: Int => Unit): Unit = {
         |  ${START}List(1) foreach f$END
         |}
       """
    checkTextHasNoErrors(text)
  }

  def testStringMethod(): Unit = {
    val text = s"""def foo(): Int = {
                    |  0 match {
                    |    case 0 => $START"1".substring(1)$END
                    |    case 1 =>
                    |  }
                    |  1
                    |}"""
    checkTextHasError(text)
  }

  def testNoForAssignment(): Unit = {
    val text = s"""def foo(): Int = {
                 |    var x = 0
                 |    ${START}x += 1$END
                 |    0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testNoForAssignment2(): Unit = {
    val text = s"""def foo(): Int = {
                 |    var x = 0
                 |    ${START}x = 1$END
                 |    0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testUnitFunction(): Unit = {
    val text = s"""def foo(): Unit = {
                 |  var z = 0
                 |  if (true) z = 1
                 |  else ${START}2$END
                 |}"""
    checkTextHasError(text)
  }

  def testUnitFunction2(): Unit = {
    val text = s"""def foo(): Unit = {
                 |  $START"1".wait()$END
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testImplicitClass(): Unit = {
    val text =
      s"""implicit class StringOps(val s: String) {
         |  def print(): Unit = println(s)
         |}
         |
         |def foo(): Unit = {
         |  $START"1".print()$END
         |}"""
    checkTextHasNoErrors(text)
  }

  def testImplicitFunction(): Unit = {
    val text =
      s"""implicit def stringToInteger(s: String): Integer = Integer.valueOf(s.length)
        |
        |def foo(): Unit = {
        |  $START"1".intValue()$END
        |}"""
    checkTextHasNoErrors(text)
  }
}
