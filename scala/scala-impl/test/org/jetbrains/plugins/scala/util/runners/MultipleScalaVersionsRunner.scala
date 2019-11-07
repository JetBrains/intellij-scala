package org.jetbrains.plugins.scala.util.runners

import java.lang.annotation.Annotation

import junit.framework
import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13}
import org.junit.internal.runners.JUnit38ClassRunner

import scala.annotation.tailrec

class MultipleScalaVersionsRunner(klass: Class[_])
  extends JUnit38ClassRunner(
    MultipleScalaVersionsRunner.testSuite(
      klass.asSubclass(classOf[TestCase])
    )
  )

private object MultipleScalaVersionsRunner {

  private val DefaultRunScalaVersionsToRun = Seq(
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13,
  )

  private def testSuite(klass: Class[_ <: TestCase]): TestSuite = {
    assert(classOf[ScalaSdkOwner].isAssignableFrom(klass))

    val suite = new TestSuite
    suite.setName(klass.getName)

    val classVersions = scalaVersionsToRun(klass)
    assert(classVersions.nonEmpty, "at least one scala version should be specified")

    val allTestCases: Seq[(TestCase, ScalaVersion)] = new ScalaVersionAwareTestsCollector(klass, classVersions).collectTests()

    val childTests = childTestsByVersion(allTestCases)
    // val childTests = childTestsByName(allTests)
    childTests.foreach { childTest =>
      suite.addTest(childTest)
    }

    suite

  }

  private def childTestsByName(testsCases: Seq[(TestCase, ScalaVersion)]): Seq[Test] = {
    val nameToTests: Map[String, Seq[(TestCase, ScalaVersion)]] = testsCases.groupBy(_._1.getName)

    for {
      (testName, tests: Seq[(TestCase, ScalaVersion)]) <- nameToTests.toSeq.sortBy(_._1)
    } yield {
      if (tests.size == 1) tests.head._1
      else {
        val suite = new framework.TestSuite()
        suite.setName(testName)
        tests.sortBy(_._2).foreach { case (t, version) =>
          t.setName(testName + "." + sanitize(version.minor))
          suite.addTest(t)
        }
        suite
      }
    }
  }

  private def childTestsByVersion(testsCases: Seq[(TestCase, ScalaVersion)]): Seq[Test] = {
    val versionToTests: Map[ScalaVersion, Seq[Test]] = testsCases.groupBy(_._2).mapValues(_.map(_._1))

    if (versionToTests.size == 1) versionToTests.head._2
    else {
      for {
        (version, tests) <- versionToTests.toSeq.sortBy(_._1)
        if tests.nonEmpty
      } yield {
        val suite = new framework.TestSuite()
        suite.setName(sanitize(s"(scala ${version.minor})"))
        tests.foreach(suite.addTest)
        suite
      }
    }
  }

  private def scalaVersionsToRun(klass: Class[_ <: TestCase]): Seq[ScalaVersion] = {
    val annotation = findAnnotation(klass, classOf[RunWishScalaVersions])
    annotation
      .map(_.value.map(_.toProductionVersion).toSeq)
      .getOrElse(DefaultRunScalaVersionsToRun)
  }

  private def findAnnotation[T <: Annotation](klass: Class[_ <: TestCase], annotationClass: Class[T]): Option[T] = {
    @tailrec
    def inner(c: Class[_]): Annotation = c.getAnnotation(annotationClass) match {
      case null =>
        c.getSuperclass match {
          case null => null
          case parent => inner(parent)
        }
      case annotation => annotation
    }

    Option(inner(klass).asInstanceOf[T])
  }

  // dot is treated as a package separator by IntelliJ which causes broken rendering in tests tree
  private def sanitize(testName: String): String = testName.replace(".", "_")
}

