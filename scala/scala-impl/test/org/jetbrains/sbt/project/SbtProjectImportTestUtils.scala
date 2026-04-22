package org.jetbrains.sbt.project

import java.nio.file.{Files, Path}

object SbtProjectImportTestUtils {

  /**
   * Replaces all occurrences of `variableName` in `file` with `value`.
   *
   * Example:
   * {{{
   *   // project/build.properties (before):
   *   // sbt.version=$SBT_VERSION$
   *
   *   SbtProjectImportTestUtils.injectVariable(
   *     file = projectRoot.resolve("project/build.properties"),
   *     variableName = "$SBT_VERSION$",
   *     value = "1.11.7"
   *   )
   *
   *   // project/build.properties (after):
   *   // sbt.version=1.11.7
   * }}}
   */
  def injectVariable(file: Path, variableName: String, value: String): Unit = {
    val fileContent = Files.readString(file)
    val updatedContent = fileContent.replace(variableName, value)
    Files.writeString(file, updatedContent)
  }
}
