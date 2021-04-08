package org.jetbrains.plugins.scala.base

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, LightPlatformTestCase, LightProjectDescriptor}
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * @author Alexander Podkhalyuzin
 * @deprecated use [[org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter]] instead
 */
@deprecated
abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase with ScalaSdkOwner {
  import LightPlatformTestCase._

  protected def sourceRootPath: String = null

  final protected def baseRootPath: String = TestUtils.getTestDataPath + "/"

  protected def getSourceRootAdapter: VirtualFile = getSourceRoot

  override protected def getProjectJDK: Sdk = SmartJDKLoader.getOrCreateJDK()

  protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    val builder = Seq.newBuilder[LibraryLoader]
    val scalaLoader = new ScalaSDKLoader(isIncludeReflectLibrary)
    builder += scalaLoader
    builder ++= Option(sourceRootPath).map(SourcesLoader)
    builder ++= additionalLibraries
    builder.result()
  }

  // TODO: can we reuse the project between test cases in an isolated class in ScalaLightPlatformCodeInsightTestCaseAdapter inheritors?
  override protected def getProjectDescriptor: LightProjectDescriptor = new ScalaLightProjectDescriptor(sharedProjectToken) {
    override def tuneModule(module: Module): Unit = afterSetUpProject(module)

    override def getSdk: Sdk = ScalaLightPlatformCodeInsightTestCaseAdapter.this.getProjectJDK
  }

  protected def afterSetUpProject(module: Module): Unit = {
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
    setUpLibraries(module)
  }

  @throws(classOf[Exception])
  override protected def setUp(): Unit = {
    super.setUp()
    TestUtils.disableTimerThread()
  }

  protected def isIncludeReflectLibrary = false

  protected def additionalLibraries: Seq[LibraryLoader] = Vector.empty

  protected def getVFileAdapter: VirtualFile = getVFile

  protected def getEditorAdapter: Editor = getEditor

  protected def getProjectAdapter: Project = getProject

  protected def getModuleAdapter: Module = getModule

  protected def getFileAdapter: PsiFile = getFile

  protected def getPsiManagerAdapter: PsiManager = getPsiManager

  protected def getCurrentEditorDataContextAdapter: DataContext = getCurrentEditorDataContext

  protected def executeActionAdapter(actionId: String): Unit = executeAction(actionId)

  protected def configureFromFileTextAdapter(fileName: String, fileText: String): Unit =
    configureFromFileText(fileName, StringUtil.convertLineSeparators(fileText))

  @throws(classOf[Exception])
  override protected def tearDown(): Unit = try {
    disposeLibraries(getModule)
  } finally super.tearDown()
}