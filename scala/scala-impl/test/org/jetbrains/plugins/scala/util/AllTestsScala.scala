package org.jetbrains.plugins.scala.util

import java.lang.reflect.{InvocationTargetException, Modifier}

import junit.framework.Test
import org.junit.internal.runners.JUnit38ClassRunner

/**
 * Analogue of [[org.junit.runners.AllTests]] test runner that supports `suite` method defined in Scala companion object
 */
class AllTestsScala(val klass: Class[_]) extends JUnit38ClassRunner(AllTestsScala.testForClass(klass))

object AllTestsScala {

  def testForClass(klass: Class[_]): Test = {
    try {
      val className = klass.getName
      val klassActual: Class[_] = if (className.endsWith("$")) {
        klass.getClassLoader.loadClass(className.dropRight(1))
      } else {
        klass
      }
      val suiteMethod = klassActual.getMethod("suite")

      if (!Modifier.isStatic(suiteMethod.getModifiers)) {
        throw new Exception(className + ".suite() must be provided in companion object")
      }

      suiteMethod.invoke(null).asInstanceOf[Test]
    } catch {
      case e: InvocationTargetException =>
        throw e.getCause
    }
  }

}