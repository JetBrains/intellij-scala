package org.jetbrains.plugins.scala.util.runners

import java.lang.reflect.{Method, Modifier}

import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.junit.internal.MethodSorter

import scala.collection.mutable

class ScalaVersionAwareTestSuite(klass: Class[_ <: TestCase], scalaVersion: ScalaVersion) extends TestSuite {

  import TestSuite._

  init()

  private def init(): Unit = {
    val tests = testsFromTestCase(klass)
    tests.foreach {
      case (test: ScalaSdkOwner, method) =>
        test.injectedScalaVersion = scalaVersion  // should be set before calling test.skip

        if (isScalaVersionSupported(test, method, scalaVersion)) {
          addTest(test)
        }
      case (test, _)                            =>
        addTest(test)
    }
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

  private def testsFromTestCase(klass: Class[_]): Seq[(Test, Method)] = {
    def warn(text: String) = Seq((warning(text), null))

    try getTestConstructor(klass) catch {
      case _: NoSuchMethodException =>
        return warn(s"Class ${klass.getName} has no public constructor TestCase(String name) or TestCase()")
    }

    if (!Modifier.isPublic(klass.getModifiers))
      return warn(s"Class ${klass.getName} is not public")

    val superClasses = Iterator.iterate[Class[_]](klass)(_.getSuperclass)
      .takeWhile(_ != null)
      .takeWhile(classOf[Test].isAssignableFrom)

    val existingNames = new mutable.ArrayBuffer[String]
    val tests = superClasses
      .flatMap { superClass =>
        val methods = MethodSorter.getDeclaredMethods(superClass)
        methods.flatMap { method =>
          val maybeTest = createTestMethod(klass, method)(existingNames)
          maybeTest.map((_, method))
        }
      }
      .toSeq

    if (tests.isEmpty) {
      warn(s"No tests found in ${klass.getName}")
    } else {
      tests
    }
  }

  private def createTestMethod(theClass: Class[_], method: Method)(existingNames: mutable.ArrayBuffer[String]): Option[Test] = {
    val name = method.getName
    if (existingNames.contains(name)) return None

    if (isPublicTestMethod(method)) {
      existingNames += name
      Some(createTest(theClass, name))
    } else if (isTestMethod(method)) {
      Some(warning(s"Test method isn't public: ${method.getName}(${theClass.getCanonicalName})"))
    } else {
      None
    }
  }

  private def isPublicTestMethod(m: Method): Boolean =
    isTestMethod(m) && Modifier.isPublic(m.getModifiers)

  private def isTestMethod(m: Method): Boolean =
    m.getParameterTypes.length == 0 &&
      m.getName.startsWith("test") &&
      m.getReturnType == Void.TYPE
}
