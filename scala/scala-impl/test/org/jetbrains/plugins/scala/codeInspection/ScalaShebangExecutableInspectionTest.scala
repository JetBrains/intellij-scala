package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiComment
import com.intellij.testFramework.{LightVirtualFile, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.shebang.ScalaShebangExecutableInspection
import org.jetbrains.plugins.scala.util.EditorHintFixtureEx
import org.junit.Assert.{assertEquals, assertTrue, fail}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

class ScalaShebangExecutableInspectionTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def sourceRootPath: Path = ScalaShebangExecutableInspectionTest.scriptSourceRoot

  private val shebang = "#!/usr/bin/env -S scala-cli shebang -q"
  private val shebangScript = s"$shebang\nprintln(42)"
  private val warningText = "Script is not executable. Missing execute permission."
  private val makeExecutableText = "Make script executable"
  private val fileSystemRejectionText =
    "Could not make script executable because the file system rejected the permission change."
  private val securityExceptionText =
    "Could not make script executable because permission to change file permissions was denied."

  private var editorHintFixture: EditorHintFixtureEx = _

  override protected def setUp(): Unit = {
    super.setUp()
    editorHintFixture = new EditorHintFixtureEx(getTestRootDisposable)
    myFixture.enableInspections(classOf[ScalaShebangExecutableInspection])
  }

  def testInspectionIsEnabledByDefault(): Unit = {
    assertTrue(
      "The executable-shebang inspection must be enabled by default",
      new ScalaShebangExecutableInspection().isEnabledByDefault
    )
  }

  def testNonExecutableShebangScriptHasWarningAndQuickFix(): Unit = {
    val script = configureLocalScript(shebangScript, executable = false)

    assertWarning()

    myFixture.launchAction(myFixture.findSingleIntention(makeExecutableText))
    assertTrue(
      "The quick fix must set the script execute permission",
      script.canExecute
    )

    assertNoWarning()
  }

  def testExecutableShebangScriptHasNoWarning(): Unit = {
    configureLocalScript(shebangScript, executable = true)

    assertNoWarning()
  }

  def testPermissionChangeRejectionShowsErrorHint(): Unit = {
    val script = new PermissionChangingFile(result = false)

    assertMakeExecutableFailureHint(fileSystemRejectionText, script)
  }

  def testPermissionChangeSecurityExceptionShowsErrorHint(): Unit = {
    val script = new PermissionChangingFile(throw new SecurityException)

    assertMakeExecutableFailureHint(securityExceptionText, script)
  }

  def testPermissionChangeRejectionReturnsFileSystemFailure(): Unit = {
    val script = new PermissionChangingFile(result = false)

    assertMakeExecutableFailure(
      ScalaShebangExecutableInspection.makeScriptExecutable(script),
      fileSystemRejectionText
    )
  }

  def testPermissionChangeSecurityExceptionReturnsPermissionChangeDenied(): Unit = {
    val script = new PermissionChangingFile(throw new SecurityException)

    assertMakeExecutableFailure(
      ScalaShebangExecutableInspection.makeScriptExecutable(script),
      securityExceptionText
    )
  }

  def testPermissionChangeRejectionShowsErrorHintWithoutInvocationEditor(): Unit = {
    val script = new PermissionChangingFile(result = false)

    assertMakeExecutableFailureHint(fileSystemRejectionText, script, useInvocationEditor = false)
  }

  def testPermissionChangeRejectionHasNoHintWithoutMatchingEditor(): Unit = {
    val script = new PermissionChangingFile(result = false)
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, shebangScript)
    val comment = file.findElementAt(0).asInstanceOf[PsiComment]
    val quickFix = new ScalaShebangExecutableInspection.MakeExecutableQuickFix(comment, script)

    myFixture.configureByText(ScalaFileType.INSTANCE, "println(42)")
    quickFix.invoke(getProject, null, file)

    dispatchAllInvocationEvents()

    assertEquals(
      "A batch quick fix without an editor for the affected file must not show a non-contextual failure hint",
      null,
      editorHintFixture.getCurrentHintText(stripHtml = true)
    )
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

    assertWarning()
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

  private def configureLocalScript(text: String, executable: Boolean): File = {
    val script = Files.createTempFile(ScalaShebangExecutableInspectionTest.scriptSourceRoot, "script", ".scala").toFile
    script.deleteOnExit()
    Files.writeString(script.toPath, text, StandardCharsets.UTF_8)

    assertTrue(
      s"The test fixture must allow setting execute permission to $executable",
      script.setExecutable(executable)
    )
    assertEquals(
      s"The test fixture must have execute permission $executable before inspection",
      executable,
      script.canExecute
    )

    val virtualFile = Option(LocalFileSystem.getInstance.refreshAndFindFileByNioFile(script.toPath)).getOrElse {
      throw new AssertionError(s"Could not refresh the local script file: $script")
    }
    assertTrue(
      "The inspection test must use a local virtual file",
      virtualFile.isInLocalFileSystem
    )

    myFixture.configureFromExistingVirtualFile(virtualFile)
    script
  }

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

  private def shebangWarnings: scala.collection.Seq[HighlightInfo] =
    myFixture.doHighlighting().asScala.filter(_.getDescription == warningText)

  private def descriptions(highlights: scala.collection.Seq[HighlightInfo]): String =
    highlights.map(_.getDescription).mkString(", ")

  private def assertMakeExecutableFailureHint(
    expectedMessage: String,
    script: File,
    useInvocationEditor: Boolean = true
  ): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, shebangScript)
    val comment = file.findElementAt(0).asInstanceOf[PsiComment]
    val quickFix = new ScalaShebangExecutableInspection.MakeExecutableQuickFix(comment, script)
    val editor =
      if (useInvocationEditor)
        myFixture.getEditor
      else
        null

    quickFix.invoke(getProject, editor, file)

    dispatchAllInvocationEvents()

    assertEquals(
      "The permission-change failure hint must explain why the permission change failed",
      expectedMessage,
      editorHintFixture.getCurrentHintBodyText
    )
  }

  private def dispatchAllInvocationEvents(): Unit = {
    UIUtil.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private def assertMakeExecutableFailure(
    result: Either[String, Unit],
    expectedFailureMessage: String
  ): Unit = {
    result match {
      case Left(actualFailureMessage) =>
        assertEquals(
          "A failed permission change must return its specific failure message",
          expectedFailureMessage,
          actualFailureMessage
        )
      case Right(_) =>
        fail("A failed permission change must not report success")
    }
  }

  private final class PermissionChangingFile(result: => Boolean) extends File("script.scala") {
    override def setExecutable(executable: Boolean): Boolean = result
  }
}

private object ScalaShebangExecutableInspectionTest {
  val scriptSourceRoot: Path = {
    val root = Files.createTempDirectory("ScalaShebangExecutableInspectionTest")
    root.toFile.deleteOnExit()
    root
  }
}
