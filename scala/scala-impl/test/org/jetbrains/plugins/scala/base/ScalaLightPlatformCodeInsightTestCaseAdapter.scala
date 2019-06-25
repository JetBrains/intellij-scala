package org.jetbrains.plugins.scala.base

import java.io.IOException
import java.util

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, LightPlatformTestCase, LightProjectDescriptor}
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.Seq

/**
 * @author Alexander Podkhalyuzin
 * @deprecated use { @link org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter} instead
 */
@deprecated
abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase with ScalaSdkOwner {
  import LightPlatformCodeInsightTestCase._
  import LightPlatformTestCase._

  protected def sourceRootPath: String = null

  final protected def baseRootPath: String = TestUtils.getTestDataPath + "/"

  protected def getSourceRootAdapter: VirtualFile = getSourceRoot

  override protected def getProjectJDK: Sdk = SmartJDKLoader.getOrCreateJDK(JavaSdkVersion.JDK_1_8)

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    val back = new util.ArrayList[LibraryLoader]
    val scalaLoader = new ScalaSDKLoader(isIncludeReflectLibrary)
    back.add(scalaLoader)
    val path = sourceRootPath
    if (path != null) back.add(new SourcesLoader(path))
    val result = scala.collection.JavaConverters.asScalaBuffer(back)
    val addLibs = additionalLibraries
    //noinspection unchecked (because variance)
    result.$plus$plus$eq(addLibs)
    result
  }

  override protected def getProjectDescriptor: LightProjectDescriptor = new ScalaLightProjectDescriptor() {
    override def tuneModule(module: Module): Unit = afterSetUpProject(module)

    override

    def getSdk: Sdk = return SmartJDKLoader.getOrCreateJDK(JavaSdkVersion.JDK_1_8)
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

  protected def configureFromFileTextAdapter(fileName: String, fileText: String): Unit = configureFromFileText(fileName, StringUtil.convertLineSeparators(fileText))

  @throws(classOf[Exception])
  override protected def tearDown(): Unit = try {
    disposeLibraries(getModule)
    val allJdks = ProjectJdkTable.getInstance.getAllJdks
    WriteAction.run(() => {
      def foo() =
        for (jdk <- allJdks) {
          ProjectJdkTable.getInstance.removeJdk(jdk)
        }

      foo()
    })
  } finally super.tearDown()
}

@deprecated
object ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val EMPTY_LOADERS_ARRAY = new Array[ThirdPartyLibraryLoader](0)
}