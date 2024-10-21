package org.jetbrains.plugins.scala.util.runners

import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.junit.internal.MethodSorter

import java.lang.reflect.{Method, Modifier}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ScalaVersionAwareTestsCollector(klass: Class[_ <: TestCase],
                                      classScalaVersion: Seq[TestScalaVersion],
                                      classJdkVersion: Seq[TestJdkVersion]) {

  def collectTests(): Seq[(TestCase, TestScalaVersion, TestJdkVersion)] = {
    val result = ArrayBuffer.empty[(Test, TestScalaVersion, TestJdkVersion)]

    val tests = testsFromTestCase(klass)
    tests.foreach {
      case (test: ScalaSdkOwner, _, scalaVersion, jdkVersion) =>
        val scalaVersionProd = scalaVersion.toProductionVersion
        val jdkVersionProd = jdkVersion.toProductionVersion

        test.injectedScalaVersion = scalaVersionProd // !! should be set before calling test.skip
        test.injectedJdkVersion = jdkVersionProd

        if (!test.skip) {
          result.append((test, scalaVersion, jdkVersion))
        }
      case (warningTest, _, scalaVersion, jdkVersion) =>
        result.append((warningTest, scalaVersion, jdkVersion))
    }

    result.map(t => (t._1.asInstanceOf[TestCase], t._2, t._3)).toSeq
  }

  // warning test or collection of tests (each test method is multiplied by the amount of versions it is run with)
  private def testsFromTestCase(klass: Class[_]): Seq[(Test, Method, TestScalaVersion, TestJdkVersion)] = {
    def warn(text: String) = Seq((TestSuite.warning(text), null, null, null))

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
      (test, scalaVersion, jdkVersion) <- createTestMethods(klass, method)
    } yield {
      visitedMethods += method
      (test, method, scalaVersion, jdkVersion)
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
  ): Seq[(Test, TestScalaVersion, TestJdkVersion)] = {
    val name = method.getName

    if (isTestMethod(method)) {
      val isPublic = isPublicMethod(method)

      val effectiveScalaVersions = methodEffectiveScalaVersions(method, classScalaVersion)
      val effectiveJdkVersions = methodEffectiveJdkVersions(method, classJdkVersion)
      for {
        scalaVersion <- effectiveScalaVersions
        jdkVersion <- effectiveJdkVersions
      } yield {
        val test = if (isPublic) {
          TestSuite.createTest(theClass, name)
        } else {
          TestSuite.warning(s"Test method isn't public: ${method.getName}(${theClass.getCanonicalName})")
        }
        (test, scalaVersion, jdkVersion)
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

  private def isPublicMethod(m: Method): Boolean = Modifier.isPublic(m.getModifiers)

  private def isTestMethod(m: Method): Boolean =
    m.getParameterTypes.length == 0 &&
      m.getName.startsWith("test") &&
      m.getReturnType == Void.TYPE
}
