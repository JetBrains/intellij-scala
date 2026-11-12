package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.{LightVirtualFile, PlatformTestUtil}
import com.intellij.util.system.OS
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.codeInspection.shebang.ScalaShebangExecutableInspection
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.EditorHintFixtureEx
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertTrue}
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermissions}
import java.nio.file.{Files, Path}
import scala.annotation.static
import scala.jdk.CollectionConverters.*

/**
 * The inspection only applies where the file system has POSIX file permissions. Elsewhere, e.g. on Windows, a script
 * has no execute permission that could be missing, so the tests assert that there is no warning, quick fix or hint.
 *
 * The tests share one project, and each test gets its own code insight fixture. The temporary directory of the fixture
 * is the project directory, which is also the source root of the module, so the scripts are in the local file system.
 * They are deleted together with the project directory, after the project is closed.
 */
@TestApplication
class ScalaShebangExecutableInspectionTest {
  import ScalaShebangExecutableInspectionTest.*

  private val codeInsightFixture: TestFixture[CodeInsightTestFixture] =
    CodeInsightFixtureKt.codeInsightFixture(projectFixture)

  private val shebang = "#!/usr/bin/env -S scala-cli shebang -q"
  private val shebangScript = s"$shebang\nprintln(42)"
  private val warningText = "Script is not executable. Missing execute permission."
  private val makeExecutableText = "Make script executable"
  private val fileSystemRejectionText =
    "Could not make script executable because the file system rejected the permission change."

  @Test
  def posixFilePermissionsAreSupportedExceptOnWindows(): Unit =
    assertEquals(
      OS.CURRENT != OS.Windows,
      posixFilePermissions,
      "The script directory must have POSIX file permissions everywhere except on Windows, " +
        "so that each platform runs the intended assertions of this test"
    )

  @Test
  def inspectionIsEnabledByDefault(): Unit =
    assertTrue(
      ScalaShebangExecutableInspection().isEnabledByDefault,
      "The executable-shebang inspection must be enabled by default"
    )

  @Test
  def nonExecutableShebangScriptHasWarningAndQuickFix(): Unit = withInspection {
    val script = configureLocalScript(shebangScript, executable = false)

    assertWarningWithPosixFilePermissions()
    if (posixFilePermissions) {
      fixture.launchAction(fixture.findSingleIntention(makeExecutableText))
      assertTrue(
        Files.isExecutable(script),
        "The quick fix must set the script execute permission"
      )

      assertNoWarning()
    }
    else {
      assertNoQuickFix()
    }
  }

  @Test
  def executableShebangScriptHasNoWarning(): Unit = withInspection {
    configureLocalScript(shebangScript, executable = true)

    assertNoWarning()
  }

  @Test
  def failedPermissionChangeShowsErrorHint(): Unit = withInspection {
    withQuickFixForDeletedScript { (file, quickFix, hints) =>
      invokeQuickFix(quickFix, fixture.getEditor, file)

      assertFailureHint(hints)
    }
  }

  @Test
  def failedPermissionChangeShowsErrorHintWithoutInvocationEditor(): Unit = withInspection {
    withQuickFixForDeletedScript { (file, quickFix, hints) =>
      // A quick fix from the Inspection Results has no invocation editor, so the hint goes to the selected editor.
      invokeQuickFix(quickFix, null, file)

      assertFailureHint(hints)
    }
  }

  @Test
  def failedPermissionChangeHasNoHintWithoutMatchingEditor(): Unit = withInspection {
    withQuickFixForDeletedScript { (file, quickFix, hints) =>
      fixture.configureByText(ScalaFileType.INSTANCE, "println(42)")
      invokeQuickFix(quickFix, null, file)

      assertNoHint(hints, "A batch quick fix without an editor for the affected file must not show a non-contextual failure hint")
    }
  }

  @Test
  def scriptWithoutShebangHasNoWarning(): Unit = withInspection {
    configureLocalScript("println(42)", executable = false)

    assertNoWarning()
  }

  @Test
  def laterShebangHasNoWarning(): Unit = withInspection {
    configureLocalScript(s"// ordinary comment\n$shebang", executable = false)

    assertNoWarning()
  }

  @Test
  def indentedShebangHasNoWarning(): Unit = withInspection {
    configureLocalScript(s"  $shebang", executable = false)

    assertNoWarning()
  }

  @Test
  def bomPrefixedShebangHasNoWarning(): Unit = withInspection {
    configureLocalScript(s"\uFEFF$shebang", executable = false)

    assertNoWarning()
  }

  @Test
  def crLfShebangScriptHasWarning(): Unit = withInspection {
    configureLocalScript(s"$shebang\r\nprintln(42)", executable = false)

    assertWarningWithPosixFilePermissions()
  }

  @Test
  def nonLocalShebangFileHasNoWarning(): Unit = withInspection {
    val virtualFile = LightVirtualFile("script.scala", ScalaFileType.INSTANCE, shebangScript)
    assertTrue(
      !virtualFile.isInLocalFileSystem,
      "The non-local test fixture must not use the local file system"
    )

    fixture.configureFromExistingVirtualFile(virtualFile)
    assertTrue(
      !fixture.getFile.getVirtualFile.isInLocalFileSystem,
      "The configured non-local test file must not use the local file system"
    )

    assertNoWarning()
  }

  private def fixture: CodeInsightTestFixture = codeInsightFixture.get()

  /** Runs `test` with the inspection enabled. */
  private def withInspection(test: => Unit): Unit = {
    fixture.enableInspections(classOf[ScalaShebangExecutableInspection])
    test
  }

  /**
   * Invokes the quick fix on the EDT, where the IDE invokes it, with a `null` editor as from the Inspection Results.
   *
   * The pending EDT events run before and after it:
   *  - before, because a newly opened editor can still change its scrolling state, which would hide the failure hint;
   *  - after, because the quick fix shows the failure hint later on the EDT.
   */
  private def invokeQuickFix(quickFix: IntentionAction, editor: Editor, file: PsiFile): Unit = invokeAndWait {
    dispatchAllInvocationEvents()
    quickFix.invoke(fixture.getProject, editor, file)
    dispatchAllInvocationEvents()
  }

  /** The directory of the scripts, which is the project directory and the source root of the module. */
  private def scriptDirectory: Path = Path.of(fixture.getTempDirPath)

  /** Whether the file system of the scripts has POSIX file permissions, which the inspection relies on. */
  private def posixFilePermissions: Boolean =
    Files.getFileAttributeView(scriptDirectory, classOf[PosixFileAttributeView]) != null

  /**
   * Creates a script in the local file system and opens it in the editor.
   *
   * Where the file system has POSIX file permissions, the script gets the requested execute permission.
   * Elsewhere, e.g. on Windows, a file has no execute permission to set.
   */
  private def configureLocalScript(text: String, executable: Boolean): Path = {
    val script = Files.createTempFile(scriptDirectory, "script", ".scala")
    Files.writeString(script, text, StandardCharsets.UTF_8)

    if (posixFilePermissions) {
      val permissions = if (executable) "rwx------" else "rw-------"
      Files.setPosixFilePermissions(script, PosixFilePermissions.fromString(permissions))
      assertEquals(
        executable,
        Files.isExecutable(script),
        s"The test fixture must have execute permission $executable before inspection"
      )
    }

    val virtualFile = Option(LocalFileSystem.getInstance.refreshAndFindFileByNioFile(script)).getOrElse {
      throw AssertionError(s"Could not refresh the local script file: $script")
    }
    assertTrue(
      virtualFile.isInLocalFileSystem,
      "The inspection test must use a local virtual file"
    )

    fixture.configureFromExistingVirtualFile(virtualFile)
    script
  }

  /**
   * Opens a non-executable shebang script, takes the quick fix that the inspection offers for it and deletes the script
   * outside the IDE, so that the quick fix fails to make it executable. Then runs `test` with the file of the script,
   * the quick fix and the hints shown from then on.
   *
   * Without POSIX file permissions, e.g. on Windows, the inspection offers no quick fix, which is asserted instead.
   *
   * The tests invoke the quick fix directly: `launchAction` makes the file read-only around the intention,
   * which fails for a deleted file.
   */
  private def withQuickFixForDeletedScript(test: (PsiFile, IntentionAction, EditorHintFixtureEx) => Unit): Unit = {
    val script = configureLocalScript(shebangScript, executable = false)

    if (posixFilePermissions) {
      val file = fixture.getFile
      val quickFix = fixture.findSingleIntention(makeExecutableText)
      val hints = EditorHintFixtureEx(fixture.getTestRootDisposable)

      Files.delete(script)
      test(file, quickFix, hints)
    }
    else {
      assertNoQuickFix()
    }
  }

  /**
   * A non-executable shebang script has the warning where the file system has POSIX file permissions,
   * and no warning elsewhere.
   */
  private def assertWarningWithPosixFilePermissions(): Unit =
    if (posixFilePermissions)
      assertWarning()
    else
      assertNoWarning()

  private def assertNoWarning(): Unit = {
    val warnings = shebangWarnings
    assertEquals(
      0,
      warnings.size,
      s"Expected no shebang execute-permission warnings, but found: ${descriptions(warnings)}"
    )
  }

  private def assertWarning(): Unit = {
    val warnings = shebangWarnings
    assertEquals(
      1,
      warnings.size,
      s"Expected one shebang execute-permission warning, but found: ${descriptions(warnings)}"
    )
    val warning = warnings.head
    assertEquals(
      0,
      warning.getStartOffset,
      "The executable-permission warning must start at the shebang"
    )
    assertEquals(
      shebang.length,
      warning.getEndOffset,
      "The executable-permission warning must cover the complete shebang"
    )
  }

  private def assertNoQuickFix(): Unit =
    assertTrue(
      fixture.filterAvailableIntentions(makeExecutableText).isEmpty,
      "Without POSIX file permissions, the quick fix must not be offered"
    )

  private def assertFailureHint(hints: EditorHintFixtureEx): Unit =
    assertEquals(
      fileSystemRejectionText,
      hints.getCurrentHintBodyText,
      "The permission-change failure hint must explain why the permission change failed"
    )

  private def assertNoHint(hints: EditorHintFixtureEx, message: String): Unit =
    assertNull(hints.getCurrentHintText(stripHtml = true), message)

  private def shebangWarnings: scala.collection.Seq[HighlightInfo] =
    fixture.doHighlighting().asScala.filter(_.getDescription == warningText)

  private def descriptions(highlights: scala.collection.Seq[HighlightInfo]): String =
    highlights.map(_.getDescription).mkString(", ")

  /** Runs the pending EDT events. Must be called on the EDT. */
  private def dispatchAllInvocationEvents(): Unit = {
    UIUtil.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}

private object ScalaShebangExecutableInspectionTest {
  /** An opened project with one module, shared by all tests of the class, because it is stored in a static field. */
  @static val projectFixture: TestFixture[Project] = CodeInsightFixtureKt.codeInsightProjectFixture()
}
