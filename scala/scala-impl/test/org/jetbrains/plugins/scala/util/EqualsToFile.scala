package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.text.Strings
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert.fail

import java.nio.file.{Files, Path}

/**
 * A Scala port of [[com.intellij.testFramework.EqualsToFile]] without the use of `java.io.File`.
 */
object EqualsToFile {
  private def trimTrailingWhitespacesAndAddNewlineAtEOF(str: String): String = {
    val res = str.split("\n").map(_.trim).mkString("\n")
    if (res.endsWith("\n")) res else res + "\n"
  }

  def assertEqualsToFile(description: String, expected: Path, actual: String): Unit = {
    import java.nio.charset.StandardCharsets.UTF_8

    if (!expected.exists) {
      Files.writeString(expected, actual, UTF_8)
      fail(s"File didn't exist. New file was created (${expected.toCanonicalPath.toString})")
    }

    val fileContents = Files.readString(expected, UTF_8).trim
    val expectedText = trimTrailingWhitespacesAndAddNewlineAtEOF(Strings.convertLineSeparators(fileContents))
    val actualText = trimTrailingWhitespacesAndAddNewlineAtEOF(Strings.convertLineSeparators(actual.trim))
    if (expectedText != actualText) {
      throw new FileComparisonFailedError(description, expectedText, actualText, expected.toCanonicalPath.toString)
    }
  }
}
