package org.jetbrains.plugins.scala.codeInspection.monads

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter

/**
 * @author Sergey Tolmachev (tolsi.ru@gmail.com)
 * @since 29.09.15
 */
class NestedStatefulMonadsInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  val annotation = NestedStatefulMonadsInspection.Annotation
  protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[NestedStatefulMonadsInspection]

  def test_1(): Unit = {
    val text =
      """import scala.util.Try
        |Try {}"""
    checkTextHasNoErrors(text)
  }

  def test_2(): Unit = {
    val text =
      """import scala.concurrent.Future
        |Future {}"""
    checkTextHasNoErrors(text)
  }

  def test_3(): Unit = {
    val text =
      s"""import scala.util.Try
        |${START}Try { Try {} }$END"""
    check(text)
  }

  def test_4(): Unit = {
    val text =
      s"""import scala.concurrent.Future
        |${START}Future { Future {} }$END"""
    check(text)
  }

  def test_5(): Unit = {
    val text = "Array.fill(5)(Array.fill(5)(0))"
    checkTextHasNoErrors(text)
  }

  def test_6(): Unit = {
    val text =
      s"""import scala.concurrent.Future
          |val a = Future { }
          |${START}Future { a }$END"""
    check(text)
  }

  def test_7(): Unit = {
    val text =
      s"""import scala.util._
          |${START}Try { Success() }$END"""
    check(text)
  }

  def test_8(): Unit = {
    val text =
      s"""import scala.util._
          |import scala.concurrent.Future
          |${START}Try { Future.successful() }$END"""
    check(text)
  }

  def test_9(): Unit = {
    val text =
      s"""import scala.util._
        |${START}Success(Success(1))$END"""
    check(text)
  }

  def test_10(): Unit = {
    val text =
      s"""import scala.util._
          |import scala.concurrent.Future
          |${START}Future { Success() }$END"""
    check(text)
  }

  def test_11(): Unit = {
    val text =
      """import scala.util._
        |Array(Failure(1))"""
    checkTextHasNoErrors(text)
  }

  def test_12(): Unit = {
    val text =
      """import scala.util._
        |Success(Array(1))"""
    checkTextHasNoErrors(text)
  }

  def test_13(): Unit = {
    val text =
      s"""import scala.util._
        |Array(${START}Success(Failure(1))$END)"""
    check(text)
  }
}
