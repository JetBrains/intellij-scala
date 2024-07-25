package org.jetbrains.plugins.scala.util.runners

import com.intellij.testFramework.TestIndexingModeSupporter
import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.util.runners.MultipleScalaVersionsRunner.findAnnotation
import org.junit.internal.MethodSorter

import java.lang.reflect.{Method, Modifier}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ScalaVersionAwareTestsCollector(klass: Class[_ <: TestCase],
                                      classScalaVersion: Seq[TestScalaVersion],
                                      classJdkVersion: Seq[TestJdkVersion]) {

  def collectTests(): Seq[(TestCase, TestScalaVersion, TestJdkVersion, TestIndexingMode)] = {
    val result = ArrayBuffer.empty[(Test, TestScalaVersion, TestJdkVersion, TestIndexingMode)]

    val tests = testsFromTestCase(klass)
    tests.foreach {
      case (test: ScalaSdkOwner, _, scalaVersion, jdkVersion, indexingMode) =>
        val scalaVersionProd = scalaVersion.toProductionVersion
        val jdkVersionProd = jdkVersion.toProductionVersion

        test.injectedScalaVersion = scalaVersionProd // !! should be set before calling test.skip
        test.injectedJdkVersion = jdkVersionProd

        test match {
          case test: TestIndexingModeSupporter => test.setIndexingMode(indexingMode.mode)
          case _ =>
        }

        if (!test.skip) {
          result.append((test, scalaVersion, jdkVersion, indexingMode))
        }
      case (warningTest, _, scalaVersion, jdkVersion, handler) =>
        result.append((warningTest, scalaVersion, jdkVersion, handler))
    }

    result.map(t => (t._1.asInstanceOf[TestCase], t._2, t._3, t._4)).toSeq
  }

  // warning test or collection of tests (each test method is multiplied by the amount of versions it is run with)
  private def testsFromTestCase(klass: Class[_]): Seq[(Test, Method, TestScalaVersion, TestJdkVersion, TestIndexingMode)] = {
    def warn(text: String) = Seq((TestSuite.warning(text), null, null, null, null))

    try TestSuite.getTestConstructor(klass) catch {
      case _: NoSuchMethodException =>
        return warn(s"Class ${klass.getName} has no public constructor TestCase(String name) or TestCase()")
    }

    if (!Modifier.isPublic(klass.getModifiers))
      return warn(s"Class ${klass.getName} is not public")

    val withSuperClasses = Iterator.iterate[Class[_]](klass)(_.getSuperclass)
      .takeWhile(_ != null)
      .takeWhile(classOf[Test].isAssignableFrom)
      .toArray

    val visitedMethods = mutable.ArrayBuffer.empty[Method]
    val tests = for {
      superClass <- withSuperClasses
      method     <- MethodSorter.getDeclaredMethods(superClass)
      if !isShadowed(method, visitedMethods)
      (test, scalaVersion, jdkVersion, indexingMode) <- createTestMethods(klass, method)
    } yield {
      visitedMethods += method
      (test, method, scalaVersion, jdkVersion, indexingMode)
    }

    if (tests.isEmpty) {
      warn(s"No tests found in ${klass.getName}")
    } else {
      tests.toSeq
    }
  }

  private def isShadowed(method: Method, results: Iterable[Method]): Boolean =
    results.exists(isShadowed(method, _))

  private def isShadowed(current: Method, previous: Method): Boolean =
    previous.getName == current.getName &&
      previous.getParameterTypes.toSeq == current.getParameterTypes.toSeq

  private def createTestMethods(
    theClass: Class[_],
    method: Method
  ): Seq[(Test, TestScalaVersion, TestJdkVersion, TestIndexingMode)] = {
    val name = method.getName

    if (isTestMethod(method)) {
      val isPublic = isPublicMethod(method)

      val effectiveScalaVersions = methodEffectiveScalaVersions(method, classScalaVersion)
      val effectiveJdkVersions = methodEffectiveJdkVersions(method, classJdkVersion)
      val effectiveIndexingModes = methodEffectiveIndexingModes(method)
      for {
        scalaVersion <- effectiveScalaVersions
        jdkVersion <- effectiveJdkVersions
        indexingMode <- effectiveIndexingModes
      } yield {
        val test = if (isPublic) {
          TestSuite.createTest(theClass, name)
        } else {
          TestSuite.warning(s"Test method isn't public: ${method.getName}(${theClass.getCanonicalName})")
        }
        (test, scalaVersion, jdkVersion, indexingMode)
      }
    } else {
      Seq()
    }
  }

  private def methodEffectiveScalaVersions(method: Method, classVersions: Seq[TestScalaVersion]): Seq[TestScalaVersion] =
    method.getAnnotation(classOf[RunWithScalaVersions]) match {
      case null =>
        classVersions
      case annotation =>
        val baseVersions = if (annotation.value.isEmpty) {
          classVersions
        } else {
          annotation.value.toSeq
        }
        val extraVersions = annotation.extra.toSeq
        (baseVersions ++ extraVersions).sorted.distinct
    }

  private def methodEffectiveJdkVersions(method: Method, classVersions: Seq[TestJdkVersion]): Seq[TestJdkVersion] =
    method.getAnnotation(classOf[RunWithJdkVersions]) match {
      case null =>
        classVersions
      case annotation =>
        val baseVersions = if (annotation.value.isEmpty) {
          classVersions
        } else {
          annotation.value.toSeq
        }
        val extraVersions = annotation.extra.toSeq
        (baseVersions ++ extraVersions).sorted.distinct
    }

  // SCL-21849
  private def methodEffectiveIndexingModes(method: Method): Seq[TestIndexingMode] =
    if (!classOf[TestIndexingModeSupporter].isAssignableFrom(klass) || findAnnotation(klass, classOf[RunWithAllIndexingModes]).isEmpty) {
      Seq(TestIndexingMode.SMART)
    } else {
      TestIndexingMode.values().toSeq.filterNot { mode =>
        mode.shouldIgnore(klass) || mode.shouldIgnore(method)
      }
    }

  private def isPublicMethod(m: Method): Boolean = Modifier.isPublic(m.getModifiers)

  private def isTestMethod(m: Method): Boolean =
    m.getParameterTypes.length == 0 &&
      m.getName.startsWith("test") &&
      m.getReturnType == Void.TYPE
}
