package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion

import scala.util.Try

private object JdkVersionDiscovery {
  def discoveredJdk: LanguageLevel =
    Option(System.getProperty("filter.test.jdk.version"))
      .flatMap(p => Try(TestJdkVersion.valueOf(p)).toOption)
      .getOrElse(TestJdkVersion.JDK_17)
      .toProductionVersion
}
