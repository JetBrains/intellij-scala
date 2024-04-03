package org.jetbrains.plugins.scala.base

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.testFramework.fixtures.{JavaCodeInsightTestFixture, LightJavaCodeInsightFixtureTestCase}
import com.intellij.testFramework.{EditorTestUtil, LightProjectDescriptor}
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader, SmartJDKLoader, SourcesLoader}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.junit.Assert
import org.junit.Assert.fail

import scala.jdk.CollectionConverters._

//TODO: try to remove EditorTestUtil.buildInitialFoldingsInBackground(getEditor) and see if tests pass?
abstract class ScalaLightCodeInsightFixtureTestCase
  extends LightJavaCodeInsightFixtureTestCase
    with ScalaSdkOwner
    with FailableTest {

  //common useful constants
  protected val CARET = EditorTestUtil.CARET_TAG
  protected val START = EditorTestUtil.SELECTION_START_TAG
  protected val END = EditorTestUtil.SELECTION_END_TAG

  protected lazy val scalaFixture: ScalaCodeInsightTestFixture = new ScalaCodeInsightTestFixture(getFixture)

  override def getTestDataPath: String = TestUtils.getTestDataPath + "/"

  protected def sourceRootPath: String = null

  //start section: project libraries configuration
  protected def loadScalaLibrary: Boolean = true

  protected def includeReflectLibrary: Boolean = false

  protected def includeCompilerAsLibrary: Boolean = false

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    val scalaSdkLoader = ScalaSDKLoader(includeReflectLibrary, includeCompilerAsLibrary)
    //note: do we indeed need to register it as libraries?
    // shouldn't source roots be registered just as source roots?
    val sourceLoaders = Option(sourceRootPath).map(SourcesLoader).toSeq
    val additionalLoaders = additionalLibraries
    scalaSdkLoader +: sourceLoaders :++ additionalLoaders
  }
  //end section: project libraries configuration

  //start section: project descriptor
  protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)

  override protected def getProjectDescriptor: LightProjectDescriptor = new ScalaLightProjectDescriptor(sharedProjectToken) {
    override def tuneModule(module: Module, project: Project): Unit = {
      afterSetUpProject(project, module)
    }

    override def getSdk: Sdk = SmartJDKLoader.getOrCreateJDK()

    override def getSourceRootType: JavaSourceRootType =
      if (placeSourceFilesInTestContentRoot)
        JavaSourceRootType.TEST_SOURCE
      else
        JavaSourceRootType.SOURCE
  }

  protected def placeSourceFilesInTestContentRoot: Boolean = false

  /**
   * @note If you are overriding this method, most likely, the light project cannot be shared between subsequent
   *       test invocations. Look into also overriding [[sharedProjectToken]].
   */
  protected def afterSetUpProject(project: Project, module: Module): Unit = {
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)

    setUpLibraries(module)
  }

  override def setUpLibraries(implicit module: Module): Unit = {
    if (loadScalaLibrary) {
      myFixture.allowTreeAccessForAllFiles()
      super.setUpLibraries(module)

      val compilerOptions = additionalCompilerOptions
      if (compilerOptions.nonEmpty) {
        addCompilerOptions(module, compilerOptions)
      }
    }
  }

  protected def additionalCompilerOptions: Seq[String] = Nil

  private def addCompilerOptions(module: Module, options: Seq[String]): Unit = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(module.getProject)

    val settings = compilerConfiguration.settingsForHighlighting(module) match {
      case Seq(s) => s
      case _ =>
        Assert.fail("expected single settings for module").asInstanceOf[Nothing]
    }

    val newSettings =
      if (options.forall(settings.additionalCompilerOptions.contains)) settings
      else settings.copy(additionalCompilerOptions = settings.additionalCompilerOptions ++ options)
    compilerConfiguration.configureSettingsForModule(module, "unit tests", newSettings)
  }
  //end section: project descriptor

  override protected def setUp(): Unit = {
    super.setUp()
    scalaFixture //init fixture lazy val
    TestUtils.disableTimerThread()
  }

  override protected def tearDown(): Unit = {
    disposeLibraries(getModule)
    super.tearDown()
  }

  //start section: helper methods
  protected final def configureFromFileText(fileText: String): PsiFile = scalaFixture.configureFromFileText(fileText)
  protected final def configureFromFileText(fileType: FileType, fileText: String): PsiFile = scalaFixture.configureFromFileText(fileType, fileText)
  protected final def configureFromFileTextWithSomeName(fileType: String, fileText: String): PsiFile = scalaFixture.configureFromFileTextWithSomeName(fileType, fileText)
  protected final def configureFromFileText(fileName: String, fileText: String): PsiFile = scalaFixture.configureFromFileText(fileName, fileText)
  protected final def openEditorAtOffset(startOffset: Int): Editor = scalaFixture.openEditorAtOffset(startOffset)

  protected final def configureScalaFromFileText(@Language("Scala") fileText: String): PsiFile = scalaFixture.configureFromFileText(fileText)
  protected final def configureScala3FromFileText(@Language("Scala 3") fileText: String): PsiFile = scalaFixture.configureFromFileText(fileText)
  protected final def addScalaFileToProject(relativePath: String, @Language("Scala") fileText: String): PsiFile = myFixture.addFileToProject(relativePath, fileText)
  //end section: helper methods

  //TODO: consider extracting implementation body to ScalaCodeInsightTestFixture
  // or crete a similar fixture which would be more specific for highlighting
  //start section: check errors
  protected def checkTextHasNoErrors(text: String): Unit = {
    myFixture.configureByText(ScalaFileType.INSTANCE, text)

    //EditorTestUtil.buildInitialFoldingsInBackground(getEditor)

    def doTestHighlighting(virtualFile: VirtualFile): Unit = {
      myFixture.testHighlighting(false, false, false, virtualFile)
    }

    if (shouldPass) {
      doTestHighlighting(getFile.getVirtualFile)
    } else {
      try {
        doTestHighlighting(getFile.getVirtualFile)
      } catch {
        case _: AssertionError =>
          return
      }
      throw new RuntimeException(failingPassed)
    }
  }

  protected def checkHasErrorAroundCaret(text: String): Unit = {
    val normalizedText = text.withNormalizedSeparator
    myFixture.configureByText("dummy.scala", normalizedText)
    val caretIndex = normalizedText.indexOf(CARET)

    def isAroundCaret(info: HighlightInfo) = caretIndex == -1 || new TextRange(info.getStartOffset, info.getEndOffset).contains(caretIndex)

    val infos = myFixture.doHighlighting().asScala

    val warnings = infos.filter(i => StringUtil.isNotEmpty(i.getDescription) && isAroundCaret(i))

    if (shouldPass) {
      if (warnings.isEmpty) {
        val message =
          if (infos.isEmpty) "No highlightings found"
          else s"No matching highlightings found. All highlightings:\n${infos.map(_.toString).mkString("\n")}"
        fail(message)
      }
    } else if (warnings.nonEmpty) {
      throw new RuntimeException(failingPassed)
    }
  }
  //end section: check errors

  //code style settings
  private def getCurrentCodeStyleSettings: CodeStyleSettings =
    CodeStyle.getSettings(getProject)

  protected def getCommonCodeStyleSettings: CommonCodeStyleSettings =
    getCurrentCodeStyleSettings.getCommonSettings(ScalaLanguage.INSTANCE)

  protected def getScalaCodeStyleSettings: ScalaCodeStyleSettings =
    getCurrentCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])

  //start section: workaround methods
  //Workarounds to make the method callable from traits (using cake pattern)
  //Also needed to workaround https://github.com/scala/bug/issues/3564
  override protected def getProject: Project = super.getProject

  //don't use getFixture, use `myFixture` directly
  protected def getFixture: JavaCodeInsightTestFixture = myFixture
  //end section: workaround methods
}
