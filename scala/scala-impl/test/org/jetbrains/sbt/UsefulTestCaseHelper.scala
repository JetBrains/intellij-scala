package org.jetbrains.sbt

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
trait UsefulTestCaseHelper { self: UsefulTestCase =>
  def assertException[T <: Throwable](expectedMessage: Option[String])(closure: => Unit)(implicit m: Manifest[T]): Unit =
    assertException(new AbstractExceptionCase[T]() {
      override def getExpectedExceptionClass: Class[T] = m.runtimeClass.asInstanceOf[Class[T]]
      override def tryClosure(): Unit = closure
    }, expectedMessage.orNull)
}
