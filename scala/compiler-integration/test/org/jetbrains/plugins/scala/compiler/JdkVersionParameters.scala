package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.runners.Parameterized

import scala.util.Try

trait JdkVersionParameters {
  @Parameterized.Parameters(name = "{0}")
  def jdkVersionsParameters: java.util.Collection[TestJdkVersion] = {
    val versionFromProperty =
      Option(System.getProperty("filter.test.jdk.version"))
        .flatMap(p => Try(TestJdkVersion.valueOf(p)).toOption)
    versionFromProperty match {
      case Some(version) => java.util.Collections.singletonList(version)
      case None => java.util.Arrays.asList(TestJdkVersion.values()*)
    }
  }
}
