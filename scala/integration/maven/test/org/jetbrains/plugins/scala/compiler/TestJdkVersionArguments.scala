package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.{Arguments, ArgumentsProvider}
import org.junit.jupiter.params.support.ParameterDeclarations

import java.util.stream.Stream
import scala.util.Try

/**
 * JUnit 5 counterpart of [[JdkVersionParameters]]: supplies [[TestJdkVersion]] values to a `@ParameterizedClass`,
 * honoring the `filter.test.jdk.version` system property.
 */
final class TestJdkVersionArguments extends ArgumentsProvider:
  override def provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream[? <: Arguments] =
    val versionFromProperty =
      Option(System.getProperty("filter.test.jdk.version"))
        .flatMap(p => Try(TestJdkVersion.valueOf(p)).toOption)

    versionFromProperty
      .fold(java.util.Arrays.stream(TestJdkVersion.values()))(Stream.of)
      .map(Arguments.of(_))
