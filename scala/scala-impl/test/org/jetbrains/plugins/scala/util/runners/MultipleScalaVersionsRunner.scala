package org.jetbrains.plugins.scala.util.runners

import java.lang.annotation.Annotation

import junit.framework.{TestCase, TestSuite}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.{Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13}
import org.junit.internal.runners.JUnit38ClassRunner

import scala.annotation.tailrec

class MultipleScalaVersionsRunner(klass: Class[_])
  extends JUnit38ClassRunner(
    MultipleScalaVersionsRunner.testSuite(
      klass.asSubclass(classOf[TestCase])
    )
  )

private object MultipleScalaVersionsRunner {

  private val DefaultRunScalaVersions = Seq(
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13,
  )

  private def testSuite(klass: Class[_ <: TestCase]): TestSuite = {
    assert(classOf[ScalaSdkOwner].isAssignableFrom(klass))

    val scalaVersionsToRun = {
      val annotation = findAnnotation(klass, classOf[RunWishScalaVersions])
      annotation
        .map(_.value.map(_.toProductionVersion).toSeq)
        .getOrElse(DefaultRunScalaVersions)
    }
    assert(scalaVersionsToRun.nonEmpty, "at least one scala version should be specified")

    scalaVersionsToRun match {
      case Seq(version) =>
        // no need in extra level lin hierarchy for a single version
        val suite = new ScalaVersionAwareTestSuite(klass, version)
        suite.setName(klass.getName)
        suite

      case _ =>
        val suite = new TestSuite
        suite.setName(klass.getName)

        val childSuites = scalaVersionsToRun.map { version =>
          val suite = new ScalaVersionAwareTestSuite(klass, version)
          suite.setName(sanitize(s"(scala ${version.minor})"))
          suite
        }
        childSuites.foreach { childTest =>
          if (childTest.tests().hasMoreElements) {
            suite.addTest(childTest)
          }
        }

        suite
    }

  }

  private def findAnnotation[T <: Annotation](klass: Class[_ <: TestCase], annotationClass: Class[T]): Option[T] = {
    @tailrec
    def inner(c: Class[_]): Annotation = c.getAnnotation(annotationClass) match {
      case null => c.getSuperclass match {
        case null   => null
        case parent => inner(parent)
      }
      case anno => anno
    }

    Option(inner(klass).asInstanceOf[T])
  }

  // dot is treated as a package separator by IntelliJ which causes broken rendering in tests tree
  private def sanitize(testName: String): String = testName.replace(".", "_")
}

