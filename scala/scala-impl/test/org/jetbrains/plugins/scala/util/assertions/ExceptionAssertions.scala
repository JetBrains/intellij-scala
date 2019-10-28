package org.jetbrains.plugins.scala.util.assertions

import org.junit.ComparisonFailure

import scala.reflect.ClassTag

trait ExceptionAssertions {

  import org.junit.Assert._

  def assertExceptionMessage[E <: Throwable: ClassTag](expectedMessage: Option[String])(code: => Unit): Unit = {
    val expectedExceptionName = implicitly[ClassTag[E]].runtimeClass.getName
    try {
      code
    } catch {
      case e: E =>
        expectedMessage.foreach { expectedMsg =>
          if (expectedMsg != e.getMessage) {
            System.err.println("Wrong message in exception:")
            System.err.println(e.getMessage)
            e.printStackTrace(System.err)
            throw new ComparisonFailure("", expectedMsg, e.getMessage)
          }
        }
        return

      case e: Throwable =>
        throw new AssertionError(s"Expected exception ${expectedExceptionName} but ${e.getClass.getName} was thrown", e)
    }

    expectedMessage match {
      case Some(msg) => fail(s"$expectedExceptionName with message '$msg' should have been thrown")
      case None      => fail(s"$expectedExceptionName should have been thrown")
    }

  }

  def assertExceptionMessage[E <: Throwable: ClassTag](expectedMessage: String)(code: => Unit): Unit =
    assertExceptionMessage(Some(expectedMessage))(code)

  def assertException[E <: Throwable: ClassTag](code: => Unit): Unit =
    assertExceptionMessage(None)(code)
}
