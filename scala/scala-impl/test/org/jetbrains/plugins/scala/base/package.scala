package org.jetbrains.plugins.scala

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
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
     * Find indexing mode annotation and get its indexing mode.
     * Return [[IndexingMode.SMART]] if no annotation found.
     *
     * Replace [[IndexingMode.DUMB_EMPTY_INDEX]] if `ide.dumb.mode.check.awareness` registry is disabled.
     * Otherwise, some tests might fail on empty index.
     * E.g.: `myFixture.doHighlighting()` calls.
     *
     * @see [[https://youtrack.jetbrains.com/issue/IJPL-164584 IJPL-164584]]
     * @see [[findIndexingModeAnnotation]]
     */
    def getIndexingModeConsideringDumbModeChecks: IndexingMode =
      findIndexingModeAnnotation().fold(IndexingMode.SMART) { annotation =>
        val mode = annotation.mode()
        if (!Registry.is("ide.dumb.mode.check.awareness") && mode == IndexingMode.DUMB_EMPTY_INDEX)
          IndexingMode.DUMB_RUNTIME_ONLY_INDEX
        else mode
      }

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
