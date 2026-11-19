package org.jetbrains.plugins.scala.compiler.highlighting.integration

import com.intellij.ide.lightEdit.{LightEdit, LightEditService, LightEditServiceImpl}
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.platform.ide.progress.TasksKt
import com.intellij.project.ProjectStoreOwner
import com.intellij.testFramework.{FixtureRuleKt, HeavyPlatformTestCase, TestLoggerKt}
import org.jetbrains.plugins.scala.compiler.highlighting.listeners.ToggleHighlightingModeListener
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaProjectSettings}
import org.junit.Assert.{assertEquals, assertNotNull, assertNull, assertSame, assertTrue}

import java.nio.file.{Files, Path}
import kotlin.coroutines.Continuation

/**
 * Editor integration coverage for SCL-25841.
 *
 * No Scala SDK is needed: opening the editor loads saved settings and publishes the highlighting
 * events responsible for this regression even without a Scala module. The syntax assertion checks
 * lexical keyword colors supplied by the plugin, without Scala compiler or library jars.
 */
abstract class ScalaEditorModesIntegrationTestBase extends HeavyPlatformTestCase {
  protected def lightEdit: Boolean

  override protected def isCreateDirectoryBasedProject: Boolean = true

  // Opening a standalone file requires neither a module nor a project JDK.
  override protected def setUpModule(): Unit = ()
  override protected def setUpJdk(): Unit = ()

  private def lightEditService: LightEditServiceImpl =
    LightEditService.getInstance().asInstanceOf[LightEditServiceImpl]

  final def testOpenScalaFileWithSavedSettings(): Unit = checkOpeningWithoutIdeErrors()

  private def checkOpeningWithoutIdeErrors(): Unit = {
    // Editor initialization can log outside the test runner's thread-local error capture.
    TestLoggerKt.assertNothingLogged(false, () => {
      checkOpeningScalaFile()
      kotlin.Unit.INSTANCE
    })
  }

  private def checkOpeningScalaFile(): Unit = {
    val project = prepareEditorProject()
    val source =
      """/** Example */
        |object Example { val answer = 42 }
        |""".stripMargin
    val file = createScalaFile(source)

    withSavedSettings(project) {
      FixtureRuleKt.runInLoadComponentStateMode(project, () => {
        reloadSavedSettingsIfAlreadyLoaded(project)
        val textEditor = openEditor(project, file)
        try {
          assertEditorOpenedWithSavedSettings(project, textEditor, source)
        } finally {
          if (!lightEdit) TextEditorProvider.getInstance().disposeEditor(textEditor)
        }
      })
    }
  }

  /**
   * Prepares the selected project mode and enables the production highlighting listener for this test.
   * LightEdit settings must remain uninitialized so opening the editor exercises their first load.
   */
  private def prepareEditorProject(): Project = {
    val project = if (lightEdit) lightEditService.getOrCreateProject() else getProject
    assertEquals("Editor project mode", lightEdit, LightEdit.owns(project))
    if (lightEdit) {
      assertNull("LightEdit settings must first be loaded by opening the editor",
        project.getServiceIfCreated(classOf[ScalaProjectSettings]))
    }

    // The production registration has activeInTestMode="false". Use the actual listener for this integration test.
    val connection = project.getMessageBus.connect(getTestRootDisposable)
    connection.subscribe(CompilerHighlightingListener.Topic,
      new ToggleHighlightingModeListener.OnCompilerHighlightingChange(project))
    project
  }

  /** Creates a physical Scala file and refreshes the VFS so either editor mode can open it. */
  private def createScalaFile(source: String): VirtualFile = {
    val path = Path.of(getProject.getBasePath, "Example.scala")
    Files.createDirectories(path.getParent)
    Files.writeString(path, source)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    assertNotNull(file)
    file
  }

  /**
   * Reloads saved settings that normal project startup initialized before any editor opened.
   * LightEdit leaves its first settings load to editor initialization.
   */
  private def reloadSavedSettingsIfAlreadyLoaded(project: Project): Unit = {
    if (!lightEdit && project.getServiceIfCreated(classOf[ScalaProjectSettings]) != null) {
      val store = project.asInstanceOf[ProjectStoreOwner].getComponentStore
      TasksKt.runWithModalProgressBlocking(project, "Reload saved Scala settings",
        (_, continuation: Continuation[? >: kotlin.Unit]) =>
          store.reloadState(classOf[ScalaProjectSettings], continuation))
    }
  }

  /** Verifies the editor's project, text, Scala syntax highlighting, and successful loading of saved settings. */
  private def assertEditorOpenedWithSavedSettings(project: Project, textEditor: TextEditor, source: String): Unit = {
    val editor = textEditor.getEditor.asInstanceOf[EditorEx]
    assertSame(project, editor.getProject)
    assertEquals(source, editor.getDocument.getText)
    val keyword = editor.getHighlighter.createIterator(source.indexOf("object"))
    assertTrue("Scala keywords must be highlighted",
      keyword.getTextAttributesKeys.contains(DefaultHighlighter.KEYWORD))

    val settings = project.getServiceIfCreated(classOf[ScalaProjectSettings])
    assertNotNull("The Scala editor must have project settings", settings)
    assertTrue("Saved settings must be read, exercising the settings-load event path",
      settings.isTreatDocCommentAsBlockComment)
  }

  private def openEditor(project: Project, file: VirtualFile): TextEditor = {
    if (lightEdit) {
      // Make LightEditService.openFile synchronous on the test EDT.
      ActionManager.getInstance()
      assertSame(project, lightEditService.openFile(file))
      lightEditService.getEditorManager.findOpen(file).getFileEditor.asInstanceOf[TextEditor]
    } else {
      TextEditorProvider.getInstance().createEditor(project, file).asInstanceOf[TextEditor]
    }
  }

  private def withSavedSettings(project: Project)(body: => Unit): Unit = {
    val path = Path.of(project.getBasePath, ".idea", "scala_settings.xml")
    val previous = Option.when(Files.exists(path))(Files.readAllBytes(path))
    Files.createDirectories(path.getParent)
    val settingsXml =
      """<project version="4">
        |  <component name="ScalaProjectSettings">
        |    <option name="treatDocCommentAsBlockComment" value="true" />
        |  </component>
        |</project>
        |""".stripMargin
    Files.writeString(path, settingsXml)
    try body
    finally {
      // Dispose before restoring persistent LightEdit storage, which is shared between test sessions.
      if (lightEdit) lightEditService.disposeCurrentSession()
      previous match {
        case Some(bytes) => Files.write(path, bytes)
        case None => Files.deleteIfExists(path)
      }
    }
  }

  override protected def tearDown(): Unit = {
    try {
      if (lightEdit) lightEditService.disposeCurrentSession()
    } finally super.tearDown()
  }
}

final class ScalaEditorLightEditIntegrationTest extends ScalaEditorModesIntegrationTestBase {
  override protected def lightEdit: Boolean = true
}

final class ScalaEditorProjectIntegrationTest extends ScalaEditorModesIntegrationTestBase {
  override protected def lightEdit: Boolean = false
}
