package org.jetbrains.plugins.scala

import junit.framework.TestCase
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.util.NotNothing
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

import java.lang.annotation.Annotation
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

package object base {
  final implicit class TestCaseExt(private val testCase: TestCase) extends AnyVal {
    // SCL-21849
    def findIndexingModeAnnotation(): Option[WithIndexingMode] = findTestAnnotation[WithIndexingMode]

    /**
     * Tries to find the specified annotation on the current test method and then on the current class.
     * And then on the superclass if marked as [[java.lang.annotation.Inherited]].
     */
    def findTestAnnotation[A <: Annotation : ClassTag : NotNothing]: Option[A] =
      testMethodAnnotation[A].orElse(classAnnotation[A])

    private def testMethodAnnotation[A <: Annotation : ClassTag]: Option[A] =
      for {
        name <- testCase.getName.toOption
        // java.lang.NoSuchMethodException can happen in generated tests (e.g.: FileSetTests)
        method <- Try(testCase.getClass.getMethod(name)).toOption
        annotation <- method.getAnnotation(classTag[A].runtimeClass.asInstanceOf[Class[A]]).toOption
      } yield annotation

    private def classAnnotation[A <: Annotation : ClassTag]: Option[A] =
      testCase.getClass.getAnnotation(classTag[A].runtimeClass.asInstanceOf[Class[A]]).toOption
  }
}
