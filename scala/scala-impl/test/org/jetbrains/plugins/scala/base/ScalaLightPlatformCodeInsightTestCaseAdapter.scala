package org.jetbrains.plugins.scala.base

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, LightProjectDescriptor}
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.TestUtils

@deprecated
abstract class ScalaLightPlatformCodeInsightTestCaseAdapter
  extends LightPlatformCodeInsightTestCase
    with ScalaSdkOwner {

  protected def sourceRootPath: String = null

  final protected def baseRootPath: String = TestUtils.getTestDataPath + "/"

  override protected def getProjectJDK: Sdk = SmartJDKLoader.getOrCreateJDK()

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    val builder = Seq.newBuilder[LibraryLoader]
    val scalaLoader = new ScalaSDKLoader(isIncludeReflectLibrary)
    builder += scalaLoader
    builder ++= Option(sourceRootPath).map(SourcesLoader)
    builder ++= additionalLibraries
    builder.result()
  }

  protected def isIncludeReflectLibrary = false

  protected def additionalLibraries: Seq[LibraryLoader] = Vector.empty

  protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  // TODO: can we reuse the project between test cases in an isolated class in ScalaLightPlatformCodeInsightTestCaseAdapter inheritors?
  override protected def getProjectDescriptor: LightProjectDescriptor = new ScalaLightProjectDescriptor(sharedProjectToken) {
    override def tuneModule(module: Module): Unit = afterSetUpProject(module)

    override def getSdk: Sdk = ScalaLightPlatformCodeInsightTestCaseAdapter.this.getProjectJDK
  }

  protected def afterSetUpProject(module: Module): Unit = {
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
    setUpLibraries(module)
  }

  override protected def setUp(): Unit = {
    super.setUp()
    TestUtils.disableTimerThread()
  }

  @throws(classOf[Exception])
  override protected def tearDown(): Unit = try {
    disposeLibraries(getModule)
    inWriteAction {
      ProjectJdkTable.getInstance().removeJdk(getProjectJDK)
    }
  } finally {
    super.tearDown()
  }

  override protected def configureFromFileText(fileName: String, fileText: String): Document = {
    val fileTextNormalized = StringUtil.convertLineSeparators(fileText)
    super.configureFromFileText(fileName, fileTextNormalized)
  }

  //overriding to make java protected method public
  //to be able to mix into other classes (see https://github.com/scala/bug/issues/3564)
  override def getProject: Project = super.getProject
}