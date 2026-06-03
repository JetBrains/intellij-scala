package org.jetbrains.plugins.scala.project.settings

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.progress.CoroutinesKt
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.util.text.Strings
import com.intellij.project.ProjectStoreOwner
import com.intellij.testFramework.{FixtureRuleKt, JavaModuleTestCase}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.compiler.data.{CompileOrder, DebuggingInfoLevel, IncrementalityType, ScalaCompilerSettingsState}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.{Files, Path}
import kotlin.coroutines.Continuation

//noinspection ApiStatus,UnstableApiUsage
@RunWith(classOf[JUnit4])
class ScalaCompilerConfigurationTest extends JavaModuleTestCase with ScalaSdkOwner {

  override protected def runInDispatchThread(): Boolean = false

  override protected def isCreateDirectoryBasedProject: Boolean = true

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader())

  private def expectedScalaCompilerConfigXmlContent: String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<project version="4">
       |  <component name="ScalaCompilerConfiguration">
       |    <option name="compileOrder" value="JavaThenScala" />
       |    <option name="nameHashing" value="false" />
       |    <option name="recompileOnMacroDef" value="false" />
       |    <option name="transitiveStep" value="4" />
       |    <option name="recompileAllFraction" value="1.5" />
       |    <option name="debuggingInfoLevel" value="Notailcalls" />
       |    <parameters>
       |      <parameter value="compilerOption1" />
       |      <parameter value="compilerOption2" />
       |    </parameters>
       |    <plugins>
       |      <plugin path="compilerPlugin1" />
       |      <plugin path="compilerPlugin2" />
       |    </plugins>
       |    <option name="incrementalityType" value="IDEA" />
       |    <option name="separateProdTestSources" value="true" />
       |    <profile name="profile1" modules="${getModule.getName}">
       |      <option name="compileOrder" value="JavaThenScala" />
       |      <option name="nameHashing" value="false" />
       |      <option name="recompileOnMacroDef" value="false" />
       |      <option name="transitiveStep" value="4" />
       |      <option name="recompileAllFraction" value="1.5" />
       |      <option name="debuggingInfoLevel" value="Notailcalls" />
       |      <parameters>
       |        <parameter value="compilerOption1" />
       |        <parameter value="compilerOption2" />
       |      </parameters>
       |      <plugins>
       |        <plugin path="compilerPlugin1" />
       |        <plugin path="compilerPlugin2" />
       |      </plugins>
       |    </profile>
       |  </component>
       |</project>
       |""".stripMargin

  @Test
  def saveComponentStateToDisk(): Unit = {
    FixtureRuleKt.runInLoadComponentStateMode(getProject, () => {
      val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject)
      initTestCompilerConfiguration(compilerConfiguration)

      // Save component.
      val componentStore = getProject.asInstanceOf[ProjectStoreOwner].getComponentStore
      componentStore.saveComponent(compilerConfiguration)
      StoreUtil.saveSettings(getProject, true)

      val filePath = scalaCompilerConfigXmlPath
      assertTrue(s"File does not exist: $filePath", filePath.exists)

      val xmlContent = readFileWithConvertedLineSeparators(filePath).trim
      assertEquals(
        "Serialized scala compiler configuration does not match",
        expectedScalaCompilerConfigXmlContent.trim,
        xmlContent
      )
    })
  }

  @Test
  def readComponentStateFromDisk(): Unit = {
    FixtureRuleKt.runInLoadComponentStateMode(getProject, () => {
      StoreUtil.saveSettings(getProject, true)

      val filePath = scalaCompilerConfigXmlPath
      writeFileWithConvertedLineSeparators(filePath, expectedScalaCompilerConfigXmlContent)

      val componentStore = getProject.asInstanceOf[ProjectStoreOwner].getComponentStore
      CoroutinesKt.runBlockingMaybeCancellable { (_, cont: Continuation[_ >: kotlin.Unit]) =>
        componentStore.reloadState(classOf[ScalaCompilerConfiguration], cont)
      }

      val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject)

      val expectedCompilerConfiguration = new ScalaCompilerConfiguration(null)
      initTestCompilerConfiguration(expectedCompilerConfiguration)

      assertEquals(expectedCompilerConfiguration.incrementalityType, compilerConfiguration.incrementalityType)
      assertScalaCompilerSettingsProfilesEquals(expectedCompilerConfiguration.customProfiles, compilerConfiguration.customProfiles)
      assertScalaCompilerSettingsDefaultProfileEquals(expectedCompilerConfiguration.defaultProfile, compilerConfiguration.defaultProfile)
      assertEquals(expectedCompilerConfiguration.separateProdTestSources, compilerConfiguration.separateProdTestSources)
    })
  }

  private def readFileWithConvertedLineSeparators(path: Path): String = {
    val content = Files.readString(path)
    Strings.convertLineSeparators(content)
  }

  private def writeFileWithConvertedLineSeparators(path: Path, content: String): Unit = {
    val converted = Strings.convertLineSeparators(content)
    Files.writeString(path, converted)
  }

  private def assertScalaCompilerSettingsProfilesEquals(expectedSeq: Seq[ScalaCompilerSettingsProfile], actualSeq: Seq[ScalaCompilerSettingsProfile]): Unit = {
    val expectedProfilesByName = expectedSeq.map(p => p.getName -> p).toMap
    val actualProfilesByName = actualSeq.map(p => p.getName -> p).toMap

    val missingExpectedProfiles = expectedProfilesByName.keySet -- actualProfilesByName.keySet
    val unexpectedActualProfiles = actualProfilesByName.keySet -- expectedProfilesByName.keySet

    assertTrue(s"Expected profiles not found in actual: $missingExpectedProfiles", missingExpectedProfiles.isEmpty)
    assertTrue(s"Unexpected profiles found in actual: $unexpectedActualProfiles", unexpectedActualProfiles.isEmpty)

    expectedProfilesByName.foreach { case (name, expectedProfile) =>
      val actualProfile = actualProfilesByName(name)
      assertScalaCompilerSettingsProfileEquals(expectedProfile, actualProfile)
    }
  }

  private def assertScalaCompilerSettingsProfileEquals(expected: ScalaCompilerSettingsProfile, actual: ScalaCompilerSettingsProfile): Unit = {
    assertEquals("Profile name", expected.getName, actual.getName)
    assertEquals("Profile modules", expected.moduleNames.toSet, actual.moduleNames.toSet)
    assertProfileSettingsEquals(expected, actual)
  }

  private def assertScalaCompilerSettingsDefaultProfileEquals(expected: ScalaCompilerSettingsProfile, actual: ScalaCompilerSettingsProfile): Unit = {
    assertProfileSettingsEquals(expected, actual)
  }

  private def assertProfileSettingsEquals(expected: ScalaCompilerSettingsProfile, actual: ScalaCompilerSettingsProfile): Unit = {
    val expectedSettings = expected.getSettings
    val actualSettings = actual.getSettings

    // settings is a case class, so we get OK-ish pretty print by default
    assertEquals("Compile settings", expectedSettings, actualSettings)
  }

  private def scalaCompilerConfigXmlPath: Path = {
    val projectDir = ProjectUtil.guessProjectDir(getProject)
    if (projectDir == null) {
      throw new AssertionError(s"Could not find project directory for project: $getProject")
    }
    projectDir.toNioPath / Project.DIRECTORY_STORE_FOLDER / "scala_compiler.xml"
  }

  private def initTestCompilerConfiguration(configuration: ScalaCompilerConfiguration): Unit = {
    configuration.incrementalityType = IncrementalityType.IDEA
    configuration.customProfiles = Seq(createTestCompilerSettingsProfile("profile1"))
    configuration.defaultProfile = createTestCompilerSettingsProfile("defaultProfile")
    // use non default value
    configuration.separateProdTestSources = !configuration.separateProdTestSources
  }

  private def createTestCompilerSettingsProfile(profileName: String): ScalaCompilerSettingsProfile = {
    val profile = new ScalaCompilerSettingsProfile(profileName)
    profile.addModuleName(getModule.getName)
    profile.setSettings(createTestCompilerSettings)
    profile
  }

  private def createTestCompilerSettings: ScalaCompilerSettings = {
    val state = createCompilerSettingsStateWithNonDefaultValues
    ScalaCompilerSettings.fromState(state)
  }

  /**
   * @return an instance of [[ScalaCompilerSettingsState]] with all fields different from default values
   */
  private def createCompilerSettingsStateWithNonDefaultValues: ScalaCompilerSettingsState = {
    val state = new ScalaCompilerSettingsState

    state.compileOrder = CompileOrder.JavaThenScala
    state.nameHashing = !state.nameHashing
    state.recompileOnMacroDef = !state.recompileOnMacroDef
    state.transitiveStep = state.transitiveStep + 1
    state.recompileAllFraction = state.recompileAllFraction + 1

    //NOTE: we don't test all the boolean fields as they are mostly the same when it comes to the serialization
    state.macros = false
    state.experimental = false
    state.debuggingInfoLevel = DebuggingInfoLevel.Notailcalls

    state.additionalCompilerOptions = Array("compilerOption1", "compilerOption2")
    state.pluginsClasspath = Array("compilerPlugin1", "compilerPlugin2")

    state
  }
}
