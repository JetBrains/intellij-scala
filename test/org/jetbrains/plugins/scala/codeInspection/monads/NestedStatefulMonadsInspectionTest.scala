package org.jetbrains.plugins.scala
package codeInspection
package monads

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil

/**
 * @author Sergey Tolmachev (tolsi.ru@gmail.com)
 * @since 29.09.15
 */
class NestedStatefulMonadsInspectionTest extends ScalaInspectionTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[NestedStatefulMonadsInspection]

  override protected val description: String =
    NestedStatefulMonadsInspection.Annotation

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
    checkTextHasError(text)
  }

  def test_4(): Unit = {
    val text =
      s"""import scala.concurrent.Future
        |${START}Future { Future {} }$END"""
    checkTextHasError(text)
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
    checkTextHasError(text)
  }

  def test_7(): Unit = {
    val text =
      s"""import scala.util._
          |${START}Try { Success() }$END"""
    checkTextHasError(text)
  }

  def test_8(): Unit = {
    val text =
      s"""import scala.util._
          |import scala.concurrent.Future
          |${START}Try { Future.successful() }$END"""
    checkTextHasError(text)
  }

  def test_9(): Unit = {
    val text =
      s"""import scala.util._
        |${START}Success(Success(1))$END"""
    checkTextHasError(text)
  }

  def test_10(): Unit = {
    val text =
      s"""import scala.util._
          |import scala.concurrent.Future
          |${START}Future { Success() }$END"""
    checkTextHasError(text)
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
    checkTextHasError(text)
  }
}
