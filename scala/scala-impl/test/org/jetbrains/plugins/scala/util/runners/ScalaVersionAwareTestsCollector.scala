package org.jetbrains.plugins.scala.util.runners

import java.lang.reflect.{Method, Modifier}

import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.junit.internal.MethodSorter

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ScalaVersionAwareTestsCollector(klass: Class[_ <: TestCase], classScalaVersion: Seq[ScalaVersion]) {

  def collectTests(): Seq[(TestCase, ScalaVersion)] = {
    val result = ArrayBuffer.empty[(Test, ScalaVersion)]

    val tests = testsFromTestCase(klass)
    tests.foreach {
      case (test: ScalaSdkOwner, method, version) =>
        test.injectedScalaVersion = version // should be set before calling test.skip

        if (isScalaVersionSupported(test, method, version)) {
          result += test -> version
        }
      case (warningTest, _, version) =>
        result += warningTest -> version
    }

    result.map(t => (t._1.asInstanceOf[TestCase], t._2))
  }

  private def isScalaVersionSupported(test: ScalaSdkOwner, method: Method, scalaVersion: ScalaVersion): Boolean = {
    val supportedVersions    = Option(method.getAnnotation(classOf[SupportedScalaVersions])).map(_.value.map(_.toProductionVersion))
    val notSupportedVersions = Option(method.getAnnotation(classOf[NotSupportedScalaVersions])).map(_.value.map(_.toProductionVersion))
    assert(supportedVersions.isEmpty || notSupportedVersions.isEmpty, "both annotations can not go together")

    // TODO: later "supported in" mechanism should be unified to only use annotations
    !test.skip &&
      supportedVersions.forall(v => v.contains(scalaVersion)) &&
      notSupportedVersions.forall(v => !v.contains(scalaVersion))
  }

  // warning test or collection of tests (each test method is multiplied by the amount of versions it is run with)
  private def testsFromTestCase(klass: Class[_]): Seq[(Test, Method, ScalaVersion)] = {
    def warn(text: String) = Seq((warning(text), null, null))

    try TestSuite.getTestConstructor(klass) catch {
      case _: NoSuchMethodException =>
        return warn(s"Class ${klass.getName} has no public constructor TestCase(String name) or TestCase()")
    }

    if (!Modifier.isPublic(klass.getModifiers))
      return warn(s"Class ${klass.getName} is not public")

    val superClasses = Iterator.iterate[Class[_]](klass)(_.getSuperclass)
      .takeWhile(_ != null)
      .takeWhile(classOf[Test].isAssignableFrom)

    val existingNames = new mutable.ArrayBuffer[String]
    val tests = for {
      superClass      <- superClasses
      method          <- MethodSorter.getDeclaredMethods(superClass)
      (test, version) <- createTestMethods(klass, method, existingNames)
    } yield (test, method, version)

    if (tests.isEmpty) {
      warn(s"No tests found in ${klass.getName}")
    } else {
      tests.toSeq
    }
  }

  private def createTestMethods(
    theClass: Class[_],
    method: Method,
    existingNames: mutable.ArrayBuffer[String]
  ): Seq[(Test, ScalaVersion)] = {
    val name = method.getName
    if (existingNames.contains(name)) return Seq()

    if (isTestMethod(method)) {
      val effectiveVersions = methodEffectiveScalaVersions(method, classScalaVersion)
      val isPublic = isPublicMethod(method)
      effectiveVersions.map { v =>
        val test = if (isPublic) {
          TestSuite.createTest(theClass, name)
        } else {
          warning(s"Test method isn't public: ${method.getName}(${theClass.getCanonicalName})")
        }
        (test, v)
      }
    } else {
      Seq()
    }
  }

  private def methodEffectiveScalaVersions(method: Method, classVersions: Seq[ScalaVersion]): Seq[ScalaVersion] =
    method.getAnnotation(classOf[RunWishScalaVersions]) match {
      case null =>
        classScalaVersion
      case annotation =>
        val baseVersions = if (annotation.value.isEmpty) {
          classScalaVersion
        } else {
          annotation.value.map(_.toProductionVersion).toSeq
        }
        val extraVersions = annotation.extra.map(_.toProductionVersion).toSeq
        (baseVersions ++ extraVersions).sorted.distinct
    }

  private def isPublicMethod(m: Method): Boolean = Modifier.isPublic(m.getModifiers)

  private def isTestMethod(m: Method): Boolean =
    m.getParameterTypes.length == 0 &&
      m.getName.startsWith("test") &&
      m.getReturnType == Void.TYPE

  // duplicate from `orgjunit.framework.TestSuite.warning`
  // the method is public in junit 4.12 in but private in `junit.jar` in IDEA jars which leads to a compilation error on TeamCity
  private def warning(message: String) = new TestCase("warning") {
    //noinspection ScalaDeprecation
    override protected def runTest(): Unit = junit.framework.Assert.fail(message)
  }
}
