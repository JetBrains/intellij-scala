package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.{PsiComment, PsiFile}
import com.intellij.testFramework.{LightVirtualFile, PlatformTestUtil}
import com.intellij.util.system.OS
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.shebang.ScalaShebangExecutableInspection
import org.jetbrains.plugins.scala.codeInspection.shebang.ScalaShebangExecutableInspection.MakeExecutableQuickFix
import org.jetbrains.plugins.scala.util.EditorHintFixtureEx
import org.junit.Assert.{assertEquals, assertNull, assertTrue}

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermissions}
import java.nio.file.{Files, Path}
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * The inspection only applies where the file system has POSIX file permissions. Elsewhere, e.g. on Windows, a script
 * has no execute permission that could be missing, so the tests assert that there is no warning, quick fix or hint.
 */
class ScalaShebangExecutableInspectionTest extends ScalaLightCodeInsightFixtureTestCase {
  import ScalaShebangExecutableInspectionTest.*

  override protected def sourceRootPath: Path = scriptSourceRoot

  private val shebang = "#!/usr/bin/env -S scala-cli shebang -q"
  private val shebangScript = s"$shebang\nprintln(42)"
  private val warningText = "Script is not executable. Missing execute permission."
  private val makeExecutableText = "Make script executable"
  private val fileSystemRejectionText =
    "Could not make script executable because the file system rejected the permission change."

  private var editorHintFixture: EditorHintFixtureEx = uninitialized

  /** The script that the test created with [[configureLocalScript]], if any. */
  private var script: Path = uninitialized

  override protected def setUp(): Unit = {
    super.setUp()
    editorHintFixture = new EditorHintFixtureEx(getTestRootDisposable)
    myFixture.enableInspections(classOf[ScalaShebangExecutableInspection])
  }

  override protected def tearDown(): Unit =
    try super.tearDown()
    finally if (script != null) Files.deleteIfExists(script)

  def testPosixFilePermissionsAreSupportedExceptOnWindows(): Unit = {
    assertEquals(
      "The script directory must have POSIX file permissions everywhere except on Windows, " +
        "so that each platform runs the intended assertions of this test",
      OS.CURRENT != OS.Windows,
      posixFilePermissions
    )
  }

  def testInspectionIsEnabledByDefault(): Unit = {
    assertTrue(
      "The executable-shebang inspection must be enabled by default",
      new ScalaShebangExecutableInspection().isEnabledByDefault
    )
  }

  def testNonExecutableShebangScriptHasWarningAndQuickFix(): Unit = {
    configureLocalScript(shebangScript, executable = false)

    assertWarningWithPosixFilePermissions()
    if (posixFilePermissions) {
      myFixture.launchAction(myFixture.findSingleIntention(makeExecutableText))
      assertTrue(
        "The quick fix must set the script execute permission",
        Files.isExecutable(script)
      )

      assertNoWarning()
    }
    else {
      assertNoQuickFix()
    }
  }

  def testExecutableShebangScriptHasNoWarning(): Unit = {
    configureLocalScript(shebangScript, executable = true)

    assertNoWarning()
  }

  def testFailedPermissionChangeShowsErrorHint(): Unit = {
    val (file, quickFix) = configureDeletedScriptWithQuickFix()

    quickFix.invoke(getProject, myFixture.getEditor, file)

    assertFailureHintWithPosixFilePermissions()
  }

  def testFailedPermissionChangeShowsErrorHintWithoutInvocationEditor(): Unit = {
    val (file, quickFix) = configureDeletedScriptWithQuickFix()

    // A quick fix from the Inspection Results has no invocation editor, so the hint goes to the selected editor.
    quickFix.invoke(getProject, null, file)

    assertFailureHintWithPosixFilePermissions()
  }

  def testFailedPermissionChangeHasNoHintWithoutMatchingEditor(): Unit = {
    val (file, quickFix) = configureDeletedScriptWithQuickFix()

    myFixture.configureByText(ScalaFileType.INSTANCE, "println(42)")
    quickFix.invoke(getProject, null, file)

    assertNoHint("A batch quick fix without an editor for the affected file must not show a non-contextual failure hint")
  }

  def testScriptWithoutShebangHasNoWarning(): Unit = {
    configureLocalScript("println(42)", executable = false)

    assertNoWarning()
  }

  def testLaterShebangHasNoWarning(): Unit = {
    configureLocalScript(s"// ordinary comment\n$shebang", executable = false)

    assertNoWarning()
  }

  def testIndentedShebangHasNoWarning(): Unit = {
    configureLocalScript(s"  $shebang", executable = false)

    assertNoWarning()
  }

  def testBomPrefixedShebangHasNoWarning(): Unit = {
    configureLocalScript(s"\uFEFF$shebang", executable = false)

    assertNoWarning()
  }

  def testCrLfShebangScriptHasWarning(): Unit = {
    configureLocalScript(s"$shebang\r\nprintln(42)", executable = false)

    assertWarningWithPosixFilePermissions()
  }

  def testNonLocalShebangFileHasNoWarning(): Unit = {
    val virtualFile = new LightVirtualFile("script.scala", ScalaFileType.INSTANCE, shebangScript)
    assertTrue(
      "The non-local test fixture must not use the local file system",
      !virtualFile.isInLocalFileSystem
    )

    myFixture.configureFromExistingVirtualFile(virtualFile)
    assertTrue(
      "The configured non-local test file must not use the local file system",
      !myFixture.getFile.getVirtualFile.isInLocalFileSystem
    )

    assertNoWarning()
  }

  /**
   * Creates the [[script]] of the test in the local file system and opens it in the editor.
   * `tearDown` deletes it, after the editor is closed.
   *
   * Where the file system has POSIX file permissions, the script gets the requested execute permission.
   * Elsewhere, e.g. on Windows, a file has no execute permission to set.
   */
  private def configureLocalScript(text: String, executable: Boolean): Unit = {
    assertNull("A test creates at most one script", script)
    script = Files.createTempFile(scriptSourceRoot, getTestName(false), ".scala")
    Files.writeString(script, text, StandardCharsets.UTF_8)

    if (posixFilePermissions) {
      val permissions = if (executable) "rwx------" else "rw-------"
      Files.setPosixFilePermissions(script, PosixFilePermissions.fromString(permissions))
      assertEquals(
        s"The test fixture must have execute permission $executable before inspection",
        executable,
        Files.isExecutable(script)
      )
    }

    val virtualFile = Option(LocalFileSystem.getInstance.refreshAndFindFileByNioFile(script)).getOrElse {
      throw new AssertionError(s"Could not refresh the local script file: $script")
    }
    assertTrue(
      "The inspection test must use a local virtual file",
      virtualFile.isInLocalFileSystem
    )

    myFixture.configureFromExistingVirtualFile(virtualFile)
  }

  /**
   * Opens a non-executable shebang script, creates its quick fix and deletes the script outside the IDE,
   * so that the quick fix fails to make it executable.
   *
   * The tests invoke the quick fix directly: `myFixture.launchAction` makes the file read-only around the intention,
   * which fails for a deleted file.
   */
  private def configureDeletedScriptWithQuickFix(): (PsiFile, MakeExecutableQuickFix) = {
    configureLocalScript(shebangScript, executable = false)
    val file = myFixture.getFile
    val quickFix = new MakeExecutableQuickFix(file.findElementAt(0).asInstanceOf[PsiComment])

    Files.delete(script)
    (file, quickFix)
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
      s"Expected no shebang execute-permission warnings, but found: ${descriptions(warnings)}",
      0,
      warnings.size
    )
  }

  private def assertWarning(): Unit = {
    val warnings = shebangWarnings
    assertEquals(
      s"Expected one shebang execute-permission warning, but found: ${descriptions(warnings)}",
      1,
      warnings.size
    )
    val warning = warnings.head
    assertEquals(
      "The executable-permission warning must start at the shebang",
      0,
      warning.getStartOffset
    )
    assertEquals(
      "The executable-permission warning must cover the complete shebang",
      shebang.length,
      warning.getEndOffset
    )
  }

  private def assertNoQuickFix(): Unit =
    assertTrue(
      "Without POSIX file permissions, the quick fix must not be offered",
      myFixture.filterAvailableIntentions(makeExecutableText).isEmpty
    )

  /**
   * The failure hint appears where the file system has POSIX file permissions.
   * Elsewhere, making the script executable does nothing, so it cannot fail.
   */
  private def assertFailureHintWithPosixFilePermissions(): Unit =
    if (posixFilePermissions)
      assertFailureHint()
    else
      assertNoHint("Without POSIX file permissions, making the script executable does nothing, so it cannot fail")

  private def assertFailureHint(): Unit = {
    dispatchAllInvocationEvents()

    assertEquals(
      "The permission-change failure hint must explain why the permission change failed",
      fileSystemRejectionText,
      editorHintFixture.getCurrentHintBodyText
    )
  }

  private def assertNoHint(message: String): Unit = {
    dispatchAllInvocationEvents()

    assertNull(message, editorHintFixture.getCurrentHintText(stripHtml = true))
  }

  private def shebangWarnings: scala.collection.Seq[HighlightInfo] =
    myFixture.doHighlighting().asScala.filter(_.getDescription == warningText)

  private def descriptions(highlights: scala.collection.Seq[HighlightInfo]): String =
    highlights.map(_.getDescription).mkString(", ")

  private def dispatchAllInvocationEvents(): Unit = {
    UIUtil.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}

private object ScalaShebangExecutableInspectionTest {
  /**
   * Shared by all tests of the class, because the source root is added once to the light project that they share.
   * Each test deletes its script in `tearDown`, and the directory is deleted when the test JVM exits.
   */
  val scriptSourceRoot: Path = {
    val root = Files.createTempDirectory("ScalaShebangExecutableInspectionTest")
    sys.addShutdownHook(NioFiles.deleteRecursively(root))
    root
  }

  /** Whether the file system of the scripts has POSIX file permissions, which the inspection relies on. */
  val posixFilePermissions: Boolean =
    Files.getFileAttributeView(scriptSourceRoot, classOf[PosixFileAttributeView]) != null
}
