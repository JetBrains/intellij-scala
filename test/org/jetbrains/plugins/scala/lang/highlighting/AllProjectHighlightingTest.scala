package org.jetbrains.plugins.scala.lang.highlighting

import java.io.File

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.{GlobalInspectionContextImpl, InspectionManagerEx, LocalInspectionToolWrapper}
import com.intellij.codeInspection.reference.{RefEntity, RefFile}
import com.intellij.codeInspection.{CommonProblemDescriptor, InspectionManager}
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdkType, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
 * @author Alefas
 * @since 12/11/14.
 */
class AllProjectHighlightingTest extends ExternalSystemImportingTestCase {
  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17
    else internalSdk
    val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]
    //todo: not working, fix and remove similar code from SbtExternalSystemManager
    settings.setJdk(sdkType.getVMExecutablePath(sdk))
    settings.setCreateEmptyContentRootDirectories(true)
    settings
  }

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getExternalSystemConfigFileName: String = "build.sbt"

  override protected def getTestsTempDir: String = ""

  protected def getRootDir: String = "scala-plugin/target/testProjects"

  def testScalaPlugin(): Unit = doRunTest()

  def testDotty(): Unit = doRunTest()

  def doRunTest(): Unit = {
    val projectDir: File = new File(getRootDir, getTestName(false))
    //this test is not intended to ran locally
    if (!projectDir.exists()) {
      println("Project is not found. Test is skipped.")
      return
    }
    importProject()

    extensions.inWriteAction {
      val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
      val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17
      else internalSdk

      //todo: why we need this??? Looks like SBT integration problem, as we attached SDK as setting
      if (ProjectJdkTable.getInstance().findJdk(sdk.getName) == null) {
        ProjectJdkTable.getInstance().addJdk(sdk)
      }
      ProjectRootManager.getInstance(myProject).setProjectSdk(sdk)
    }

    doRunHighlighting()
  }

  def doRunHighlighting(): Unit = {
    val inspectionManagerEx: InspectionManagerEx = InspectionManager.getInstance(myProject).asInstanceOf[InspectionManagerEx]

    val searchScope =
      new SourceFilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(myProject),
        ScalaFileType.SCALA_FILE_TYPE, JavaFileType.INSTANCE), myProject)

    val scope: AnalysisScope = new AnalysisScope(searchScope, myProject)
    val wrapper = new LocalInspectionToolWrapper(new AnnotatorBasedErrorInspection)
    val inspectionContext: GlobalInspectionContextImpl = CodeInsightTestFixtureImpl.createGlobalContextForTool(
      scope, myProject, inspectionManagerEx, wrapper
    )
    inspectionContext.doInspections(scope)
    val presentation = inspectionContext.getPresentation(wrapper)
    val problems = presentation.getProblemElements
    if (!problems.isEmpty) {
      import scala.collection.JavaConversions._
      val number = problems.map(_._2.length).sum

      problems.foreach {
        case (entity: RefEntity, descriptors: Array[CommonProblemDescriptor]) =>
          entity match {
            case entity: RefFile =>
              println(entity.getElement.getVirtualFile.getPath)
            case _ =>
              println(entity.getQualifiedName)
          }
          descriptors.foreach {
            case descriptor: CommonProblemDescriptor =>
              println("  " + descriptor.getDescriptionTemplate)
          }
      }

      assert(assertion = false, s"Project has $number errors!")
    }
  }

  override protected def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    val projectDir: File = new File(getRootDir, getTestName(false))
    if (!projectDir.exists()) return
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDir)
    SbtSystemSettings.getInstance(myProject).setCustomLauncherEnabled(true)
    SbtSystemSettings.getInstance(myProject).setCustomLauncherPath(new File("scala-plugin/jars/sbt-launch.jar").getAbsolutePath)
    SbtSystemSettings.getInstance(myProject).setCustomSbtStructureDir(new File("scala-plugin/jars").getAbsolutePath)
    myFixture.enableInspections(classOf[AnnotatorBasedErrorInspection])
  }
}
