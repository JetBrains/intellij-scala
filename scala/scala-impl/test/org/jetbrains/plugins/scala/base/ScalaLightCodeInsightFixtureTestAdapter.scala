package org.jetbrains.plugins.scala
package base

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.fixtures.{JavaCodeInsightTestFixture, LightJavaCodeInsightFixtureTestCase}
import com.intellij.testFramework.{EditorTestUtil, LightPlatformTestCase, LightProjectDescriptor}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.junit.Assert.{assertNotNull, fail}

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */
abstract class ScalaLightCodeInsightFixtureTestAdapter
  extends LightJavaCodeInsightFixtureTestCase with ScalaSdkOwner with TestFixtureProvider with FailableTest {

  import ScalaLightCodeInsightFixtureTestAdapter._
  import libraryLoaders._

  val CARET = EditorTestUtil.CARET_TAG

  override final def getFixture: JavaCodeInsightTestFixture = myFixture

  override def getTestDataPath: String = util.TestUtils.getTestDataPath + "/"

  protected def loadScalaLibrary: Boolean = true

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader()
  )

  override protected def getProjectDescriptor: LightProjectDescriptor = new ScalaLightProjectDescriptor() {
    override def tuneModule(module: Module): Unit = setUpLibraries(module)
    override def getSdk: Sdk = SmartJDKLoader.getOrCreateJDK()
  }

  override def setUpLibraries(implicit module: Module): Unit = {
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
    if (loadScalaLibrary) {
      getFixture.allowTreeAccessForAllFiles()
      super.setUpLibraries(module)
    }
  }

  override protected def tearDown(): Unit = {
    disposeLibraries(getModule)
    super.tearDown()
  }

  protected def configureFromFileText(fileText: String): PsiFile =
    configureFromFileText(fileText, ScalaFileType.INSTANCE)

  protected def configureFromFileText(fileText: String, fileType: FileType): PsiFile = {
    val file = getFixture.configureByText(fileType, normalize(fileText))
    assertNotNull(file)
    file
  }

  protected def checkTextHasNoErrors(text: String): Unit = {
    getFixture.configureByText(
      ScalaFileType.INSTANCE,
      text
    )
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(getEditor)

    if (shouldPass) {
      testHighlighting(getFile.getVirtualFile)
    } else {
      try {
        testHighlighting(getFile.getVirtualFile)
      } catch {
        case _: AssertionError => return
      }
      failingTestPassed()
    }
  }

  protected def failingTestPassed(): Unit = throw new RuntimeException(failingPassed)

  protected def getCurrentCodeStyleSettings: CodeStyleSettings = CodeStyle.getSettings(getProject)

  protected def getCommonSettings: CommonCodeStyleSettings = getCurrentCodeStyleSettings.getCommonSettings(ScalaLanguage.INSTANCE)

  protected def getScalaSettings: ScalaCodeStyleSettings = getCurrentCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])

  private def testHighlighting(virtualFile: VirtualFile): Unit = getFixture.testHighlighting(
    false,
    false,
    false,
    virtualFile
  )

  protected def changePsiAt(offset: Int): Unit = {
    invokeAndWait {
      getEditor.getCaretModel.moveToOffset(offset)
      myFixture.`type`('a')
      commitDocument()
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      commitDocument()
    }
  }

  private def commitDocument(): Unit = PsiDocumentManager.getInstance(getProject).commitDocument(getEditor.getDocument)
}

object ScalaLightCodeInsightFixtureTestAdapter {

  def normalize(text: String, stripTrailingSpaces: Boolean = true): String =
    text.stripMargin.replace("\r", "") match {
      case result if stripTrailingSpaces => result.trim
      case result => result
    }

  def findCaretOffset(text: String, stripTrailingSpaces: Boolean): (String, Int) = {
    val (textActual, caretOffsets) = findCaretOffsets(text, stripTrailingSpaces)
    caretOffsets match {
      case Seq(caretIdx) => (textActual, caretIdx)
      case Seq()         => (textActual, -1)
      case _             => fail(s"single caret expected but found: ${caretOffsets.size}").asInstanceOf[Nothing]
    }
  }

  def findCaretOffsets(text: String, stripTrailingSpaces: Boolean): (String, Seq[Int]) = {
    import EditorTestUtil.CARET_TAG

    val textNormalized = normalize(text, stripTrailingSpaces)

    def caretIndex(offset: Int) = textNormalized.indexOf(CARET_TAG, offset)

    @scala.annotation.tailrec
    def collectCaretIndices(idx: Int)(indices: Seq[Int]): Seq[Int] =
      if (idx < 0) indices else {
        val nextIdx = caretIndex(idx + 1)
        collectCaretIndices(nextIdx)(indices :+ idx)
      }

    val caretIndices = collectCaretIndices(caretIndex(0))(Seq[Int]())
    val caretIndicesNormalized = caretIndices.zipWithIndex.map { case (caretIdx, idx) => caretIdx - idx * CARET_TAG.length }
    (
      textNormalized.replace(CARET_TAG, ""),
      caretIndicesNormalized
    )
  }

  implicit class Ext(private val adapter: ScalaLightCodeInsightFixtureTestAdapter) extends AnyVal {

    def configureJavaFile(fileText: String,
                          className: String,
                          packageName: String = null): Unit = inWriteAction {
      val root = LightPlatformTestCase.getSourceRoot match {
        case sourceRoot if packageName == null => sourceRoot
        case sourceRoot => sourceRoot.createChildDirectory(null, packageName)
      }

      val file = root.createChildData(null, className + ".java")
      VfsUtil.saveText(file, normalize(fileText))
    }
  }
}
