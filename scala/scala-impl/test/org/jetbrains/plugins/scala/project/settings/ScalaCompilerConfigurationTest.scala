package org.jetbrains.plugins.scala.project.settings

import com.intellij.configurationStore.ProjectStoreImpl
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandler.SaveTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.{LightProjectDescriptor, TemporaryDirectory}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.compiler.data.{CompileOrder, DebuggingInfoLevel, IncrementalityType, ScalaCompilerSettingsState}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert._

import java.nio.file.Path

//noinspection ApiStatus,UnstableApiUsage,DfaNullableToNotNullParam
class ScalaCompilerConfigurationTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def getProjectDescriptor: LightProjectDescriptor = new MyProjectDescriptor {
    // This will cause the test framework to create a ".idea" directory-based project structure
    // We want it to save component states as .xml files and check their content
    override def generateProjectPath: Path =
      TemporaryDirectory.generateTemporaryPath(ProjectImpl.LIGHT_PROJECT_NAME)
  }

  private val ExpectedScalaCompilerConfigXmlContent =
    """<?xml version="1.0" encoding="UTF-8"?>
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
      |    <profile name="profile1" modules="light_idea_test_case">
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

  def testSaveComponentStateToDisk(): Unit = {
    val project = getProject
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
    initTestCompilerConfiguration(compilerConfiguration)

    saveComponentAndFlushToDisk(project, compilerConfiguration)

    val scalaCompilerConfigXmlFile = getScalaCompilerConfigXmlPath.toFile
    assertTrue(s"File does not exist: $scalaCompilerConfigXmlFile", scalaCompilerConfigXmlFile.exists())
    val scalaCompilerConfigXmlContent = FileUtil.loadFile(scalaCompilerConfigXmlFile, true)
    assertEquals(
      "Serialized scala compiler configuration",
      ExpectedScalaCompilerConfigXmlContent.trim,
      scalaCompilerConfigXmlContent.trim
    )
  }

  def testReadComponentStateFromDisk(): Unit = {
    val project = getProject

    val scalaCompilerConfigXmlFile = getScalaCompilerConfigXmlPath.toFile
    FileUtil.writeToFile(scalaCompilerConfigXmlFile, ExpectedScalaCompilerConfigXmlContent)

    val componentStore = project.asInstanceOf[ProjectImpl].getComponentStore.asInstanceOf[ProjectStoreImpl]
    componentStore.reloadState(classOf[ScalaCompilerConfiguration])

    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)

    val expectedCompilerConfiguration = new ScalaCompilerConfiguration(null)
    initTestCompilerConfiguration(expectedCompilerConfiguration)

    assertEquals(expectedCompilerConfiguration.incrementalityType, compilerConfiguration.incrementalityType)
    assertScalaCompilerSettingsProfilesEquals(expectedCompilerConfiguration.customProfiles, compilerConfiguration.customProfiles)
    assertScalaCompilerSettingsProfileEquals(expectedCompilerConfiguration.defaultProfile, compilerConfiguration.defaultProfile)
    assertEquals(expectedCompilerConfiguration.separateProdTestSources, compilerConfiguration.separateProdTestSources)
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
    assertEquals(s"Profile name", expected.getName, actual.getName)
    assertEquals(s"Profile modules", expected.moduleNames.toSet, actual.moduleNames.toSet)

    val expectedSettings = expected.getSettings
    val actualSettings = actual.getSettings

    // settings is a case class, so we get OK-ish pretty print by default
    assertEquals("Compile settings", expectedSettings, actualSettings)
  }

  private def getScalaCompilerConfigXmlPath = Path.of(getProject.getBasePath) / ".idea/scala_compiler.xml"

  private def saveComponentAndFlushToDisk(project: Project, compilerConfiguration: ScalaCompilerConfiguration): Unit = {
    // save component
    val componentStore = project.asInstanceOf[ProjectImpl].getComponentStore.asInstanceOf[ProjectStoreImpl]
    componentStore.saveComponent(compilerConfiguration)

    // flush component to disk
    SaveAndSyncHandler.getInstance().scheduleSave(new SaveTask(project, true), true)
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