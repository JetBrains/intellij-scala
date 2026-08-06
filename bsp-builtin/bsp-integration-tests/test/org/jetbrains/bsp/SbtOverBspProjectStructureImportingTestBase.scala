package org.jetbrains.bsp

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.external.JdkByName
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl.{javaLanguageLevel, javaTargetBytecodeLevel, javacOptions, module, modules, project, sdk}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureMatcher, RequiresJdk, SbtProjectImportTestUtils, ScalaExternalSystemImportingTestBase}

import java.nio.file.Files
import scala.util.Try

abstract class SbtOverBspProjectStructureImportingTestBase
  extends SbtOverBspExternalSystemImportingTestCase
    with ProjectStructureMatcher
    with ExactMatch {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/${getTestName(true)}"

  override protected def reuseExistingConnectionFile = false

  override protected def getTestProjectCopyOptions: ScalaExternalSystemImportingTestBase.TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  protected implicit lazy val defaultCompareContext: ProjectStructureComparisonContext =
    ProjectStructureComparisonContext.Implicit.default(using getMyProject)

  /**
   * Injects sbt version into the test project. If `$SBT_VERSION$` placeholder exists in
   * `project/build.properties`, replaces it with the requested version. Otherwise, creates
   * the `build.properties` file with `sbt.version` property.
   */
  override protected def injectSbtVersion(): Unit =
    sbtVersionToInject.foreach { version =>
      val projectDir = getTestProjectPath / "project"
      val buildPropertiesFile = projectDir / "build.properties"

      def createPropertiesFile(): Unit =
        Files.createDirectories(projectDir)
        Files.writeString(buildPropertiesFile, s"sbt.version=${version.minor}")

      val containsPlaceholder = Try {
        Files.readString(buildPropertiesFile).contains("$SBT_VERSION$")
      }.toOption.getOrElse(false)

      if containsPlaceholder then
        SbtProjectImportTestUtils.injectVariable(buildPropertiesFile, "$SBT_VERSION$", version.minor)
      else
        createPropertiesFile()
    }

  /**
   * This test captures the current state of java language level and target bytecode level
   * settings in sbt/BSP projects. The current limitations are:
   *
   *  - The `-source` flag from `javacOptions` is not taken into account (SCL-25608)
   *  - Language level is not updated to preview versions
   *  - Target bytecode level is not set at all
   */
  @RequiresJdk(LanguageLevel.JDK_17)
  def testJavaLanguageLevelAndTargetByteCodeLevel(): Unit = {
    importProject(false)

    val sdkLanguageLevel = LanguageLevel.JDK_17

    def moduleX(name: String, source: LanguageLevel): module = new module(name) {
      javaLanguageLevel := source
      javaTargetBytecodeLevel := null
      javacOptions := Nil
      sdk := JdkByName(getJdkConfiguredForTestCase.getName)
    }

    val root = moduleX("java-language-level-and-target-byte-code-level", sdkLanguageLevel)

    // Module naming: `source_target_release` - `x` means option is missing
    val module_x_x_x = moduleX(s"module_x_x_x", sdkLanguageLevel)

    // In BSP, the language level is determined in the following priority order:
    //   1. From the `-source` flag in javacOptions
    //   2. From JvmBuildTarget#javaVersion
    //   3. From the module's JDK
    //
    // However, in sbt/BSP projects, the `-source` flag is currently not taken into account:
    //   - It's not read from javacOptions (SCL-25608)
    //   - It's not provided by the sbt/BSP server in JvmBuildTarget. sbt uses only `-release` and `-target` flags
    //     to calculate JvmBuildTarget#javaVersion
    //
    // For this reason, in the modules below the language level is set to the value specified by the `-target` flag.
    val module_8_8_x = moduleX(s"module_8_8_x", LanguageLevel.JDK_1_8)
    val module_8_11_x = moduleX(s"module_8_11_x", LanguageLevel.JDK_11)
    val module_11_8_x = moduleX(s"module_11_8_x", LanguageLevel.JDK_1_8)
    val module_11_11_x = moduleX(s"module_11_11_x", LanguageLevel.JDK_11)

    // Since the `-source` flag is not considered, the language level falls back to the module's JDK.
    val module_8_x_x = moduleX(s"module_8_x_x", sdkLanguageLevel)
    val module_11_x_x = moduleX(s"module_11_x_x", sdkLanguageLevel)
    val module_14_x_x = moduleX(s"module_14_x_x", sdkLanguageLevel)
    val module_15_x_x = moduleX(s"module_15_x_x", sdkLanguageLevel)

    // The language level matches the `--target` flag value, as it's derived from JvmBuildTarget#javaVersion
    val module_x_8_x = moduleX(s"module_x_8_x", LanguageLevel.JDK_1_8)
    val module_x_11_x = moduleX(s"module_x_11_x", LanguageLevel.JDK_11)

    // The language level matches the `--release` flag value, as it's derived from JvmBuildTarget#javaVersion
    val module_x_x_8 = moduleX(s"module_x_x_8", LanguageLevel.JDK_1_8)
    val module_x_x_11 = moduleX(s"module_x_x_11", LanguageLevel.JDK_11)

    // Since the `-source` flag is not considered, the language level
    // falls back to the module's JDK (preview flag ignored).
    val module_8_x_x_preview = moduleX(s"module_8_x_x_preview", sdkLanguageLevel)
    val module_11_x_x_preview = moduleX(s"module_11_x_x_preview", sdkLanguageLevel)
    val module_14_x_x_preview = moduleX(s"module_14_x_x_preview", sdkLanguageLevel)
    val module_20_x_x_preview = moduleX(s"module_20_x_x_preview", sdkLanguageLevel)

    // The language level matches the `--release` flag (preview flag ignored).
    val module_x_x_8_preview = moduleX(s"module_x_x_8_preview", LanguageLevel.JDK_1_8)
    val module_x_x_11_preview = moduleX(s"module_x_x_11_preview", LanguageLevel.JDK_11)
    val module_x_x_14_preview = moduleX(s"module_x_x_14_preview", LanguageLevel.JDK_14)
    val module_x_x_20_preview = moduleX(s"module_x_x_20_preview", LanguageLevel.JDK_20)

    val expectedProject = new project("javaLanguageLevelAndTargetByteCodeLevel") {
      modules := Seq(
        root,
        module_x_x_x,
        module_8_8_x, module_8_11_x, module_11_8_x, module_11_11_x,
        module_8_x_x, module_11_x_x, module_14_x_x, module_15_x_x,
        module_x_8_x, module_x_11_x,
        module_x_x_8, module_x_x_11,
        module_8_x_x_preview, module_11_x_x_preview, module_14_x_x_preview, module_20_x_x_preview,
        module_x_x_8_preview, module_x_x_11_preview, module_x_x_14_preview, module_x_x_20_preview
      )
    }

    assertProjectsEqual(expectedProject, getMyProject)
  }
}