package org.jetbrains.plugins.scala
package codeInspection
package monads

import com.intellij.codeInspection.LocalInspectionTool

class NestedStatefulMonadsInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[NestedStatefulMonadsInspection]

  override protected val description: String =
    NestedStatefulMonadsInspection.Description

  def test_1(): Unit = checkTextHasNoErrors(
    text =
      """import scala.util.Try
        |Try {}""".stripMargin
  )

  def test_2(): Unit = checkTextHasNoErrors(
    text =
      """import scala.concurrent.Future
        |Future {}""".stripMargin
  )

  def test_3(): Unit = checkTextHasError(
    text =
      s"""import scala.util.Try
         |${START}Try { Try {} }$END""".stripMargin
  )

  def test_4(): Unit = checkTextHasError(
    text =
      s"""import scala.concurrent.Future
         |${START}Future { Future {} }$END""".stripMargin
  )

  def test_5(): Unit = checkTextHasNoErrors(
    text = "Array.fill(5)(Array.fill(5)(0))"
  )

  def test_6(): Unit = checkTextHasError(
    text =
      s"""import scala.concurrent.Future
         |val a = Future { }
         |${START}Future { a }$END""".stripMargin
  )

  def test_7(): Unit = checkTextHasError(
    text =
      s"""import scala.util._
         |${START}Try { Success() }$END""".stripMargin
  )

  def test_8(): Unit = checkTextHasError(
    text =
      s"""import scala.util._
         |import scala.concurrent.Future
         |${START}Try { Future.successful() }$END""".stripMargin
  )

  def test_9(): Unit = checkTextHasError(
    text =
      s"""import scala.util._
         |${START}Success(Success(1))$END""".stripMargin
  )

  def test_10(): Unit = checkTextHasError(
    text =
      s"""import scala.util._
         |import scala.concurrent.Future
         |${START}Future { Success() }$END""".stripMargin
  )

  def test_11(): Unit = checkTextHasNoErrors(
    text =
      """import scala.util._
        |Array(Failure(1))""".stripMargin
  )

  def test_12(): Unit = checkTextHasNoErrors(
    text =
      """import scala.util._
        |Success(Array(1))""".stripMargin
  )

  def test_13(): Unit = checkTextHasError(
    text =
      s"""import scala.util._
         |Array(${START}Success(Failure(1))$END)""".stripMargin
  )
}
