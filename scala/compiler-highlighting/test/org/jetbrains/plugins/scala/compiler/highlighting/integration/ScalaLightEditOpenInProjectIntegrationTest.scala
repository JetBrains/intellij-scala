package org.jetbrains.plugins.scala.compiler.highlighting.integration

import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention
import com.intellij.ide.lightEdit.{LightEdit, LightEditService, LightEditServiceImpl}
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.{HeavyPlatformTestCase, PlatformTestUtil, TestLoggerKt}
import org.jetbrains.plugins.scala.compiler.highlighting.listeners.ToggleHighlightingModeListener
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaProjectSettings}
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue}

import java.nio.file.{Files, Path}

/**
 * SCL-25841: loading a project's Scala settings must be safe while a LightEdit editor is open.
 *
 * No Scala SDK is needed: loading saved Scala settings publishes the application-wide highlighting
 * events even in a project without modules. This exercises the failing path before any Scala SDK
 * or compiler-dependent analysis is required.
 *
 * This scenario opens the file's enclosing directory-based project. The reported UI flow selects
 * an unrelated sbt project; chooser routing, sbt import, and the full tab/window handoff need
 * verification in the full IDE.
 */
final class ScalaLightEditOpenInProjectIntegrationTest extends HeavyPlatformTestCase {
  override protected def isCreateDirectoryBasedProject: Boolean = true

  // The fixture project supplies the test lifecycle; the intention opens a separate project from disk.
  override protected def setUpModule(): Unit = ()
  override protected def setUpJdk(): Unit = ()

  private def lightEditService: LightEditServiceImpl =
    LightEditService.getInstance().asInstanceOf[LightEditServiceImpl]

  private val source =
    """/** Example */
      |object Example { val answer = 42 }
      |""".stripMargin

  def testOpenLightEditFileInProjectWithSavedSettings(): Unit = checkOpeningProjectWithoutIdeErrors()

  private def checkOpeningProjectWithoutIdeErrors(): Unit = {
    TestLoggerKt.assertNothingLogged(false, () => {
      val (projectDirectory, file) = createSavedProject()
      val lightProject = openFileInLightEdit(file)
      enableHighlightingListener(lightProject)
      assertTrue("The intention must open a new project", openedProjectAt(projectDirectory).isEmpty)

      try {
        LightEditOpenInProjectIntention.performOn(lightProject, file)
        val project = openedProjectAt(projectDirectory).orNull
        assertNotNull("Open in Project must open the saved project", project)
        assertProjectOpenedWithFileAndSettings(project, file)
        assertFalse("LightEdit must still be alive when the project loads", lightProject.isDisposed)
      } finally {
        openedProjectAt(projectDirectory).foreach(PlatformTestUtil.forceCloseProjectWithoutSaving)
      }
      kotlin.Unit.INSTANCE
    })
  }

  /**
   * Creates a closed directory-based project with saved Scala settings and a physical source file.
   * Its existing `.idea` directory lets the intention find the project without a directory chooser.
   */
  private def createSavedProject(): (Path, VirtualFile) = {
    val directory = getProjectDirOrFile
    val settingsDirectory = Files.createDirectories(directory.resolve(".idea"))
    val settingsXml =
      """<project version="4">
        |  <component name="ScalaProjectSettings">
        |    <option name="treatDocCommentAsBlockComment" value="true" />
        |  </component>
        |</project>
        |""".stripMargin
    Files.writeString(settingsDirectory.resolve("scala_settings.xml"), settingsXml)
    val sourcePath = directory.resolve("Example.scala")
    Files.writeString(sourcePath, source)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath)
    assertNotNull(file)
    (directory, file)
  }

  /** Opens the initial editor through the real LightEdit service before the transition under test. */
  private def openFileInLightEdit(file: VirtualFile): Project = {
    ActionManager.getInstance()
    val project = lightEditService.openFile(file)
    assertTrue(LightEdit.owns(project))
    assertTrue("The file must first be open in LightEdit", lightEditService.getEditorManager.isFileOpen(file))
    project
  }

  /**
   * Enables the production listener, whose XML registration is disabled in unit-test mode.
   * Register after the initial editor opens so any regression is detected during the project transition.
   */
  private def enableHighlightingListener(project: Project): Unit = {
    project.getMessageBus.connect(getTestRootDisposable).subscribe(
      CompilerHighlightingListener.Topic,
      new ToggleHighlightingModeListener.OnCompilerHighlightingChange(project)
    )
  }

  /** Finds the actual opened project, accounting for canonical path differences in temporary directories. */
  private def openedProjectAt(directory: Path): Option[Project] = {
    ProjectManager.getInstance().getOpenProjects.find { project =>
      val basePath = project.getBasePath
      basePath != null && Files.exists(Path.of(basePath)) && Files.isSameFile(Path.of(basePath), directory)
    }
  }

  /** Checks the regular project's editor and proves its Scala settings were loaded from the saved file. */
  private def assertProjectOpenedWithFileAndSettings(project: Project, file: VirtualFile): Unit = {
    assertFalse(LightEdit.owns(project))
    assertTrue(project.isOpen)
    val editors = FileEditorManager.getInstance(project)
    assertTrue("The intention must open the Scala file in the project", editors.isFileOpen(file))
    val editor = editors.getSelectedTextEditor
    assertNotNull(editor)
    assertEquals(source, editor.getDocument.getText)

    val settings = project.getServiceIfCreated(classOf[ScalaProjectSettings])
    assertNotNull("Opening the project must load Scala settings", settings)
    assertTrue("Saved settings must be read during project opening", settings.isTreatDocCommentAsBlockComment)
  }

  override protected def tearDown(): Unit = {
    try lightEditService.disposeCurrentSession()
    finally super.tearDown()
  }
}
