package org.jetbrains.plugins.scala.memoryLeaks

import java.io.File

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.{InspectionManagerEx, InspectionProfileImpl, LocalInspectionToolWrapper}
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{DumbService, Project, ProjectManager}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Computable
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.{LeakHunter, PlatformTestCase}
import com.intellij.util.Processor
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

import scala.collection.JavaConverters._

/**
  * @author Nikolay.Tropin
  */
class MemoryLeakTest extends PlatformTestCase {

  protected val testDataPath = new File(TestUtils.getTestDataPath, "memoryLeaks").getPath

  protected val projectPath: String = new File(testDataPath, "HelloScala").getPath

  protected def findScalaFile(project: Project): ScalaFile = {
    val file = project.getBaseDir.findChild("src").findChild("HelloWorld.scala")
    PsiManager.getInstance(project).findFile(file).asInstanceOf[ScalaFile]
  }

  override protected def setUpProject() {}

  //from com.intellij.ide.ProjectLeakTest
  protected def loadAndSetupProject(path: String): Project = {
    val project: Project = ProjectManager.getInstance.loadAndOpenProject(path)
    assertNotNull(project)
    do {
      UIUtil.dispatchAllInvocationEvents()
    } while (DumbService.getInstance(project).isDumb)
    myProject = project
    val startupManager: StartupManagerImpl = StartupManager.getInstance(project).asInstanceOf[StartupManagerImpl]
    val passed: Boolean = startupManager.postStartupActivityPassed
    assertTrue(passed)
    assertTrue(project.isOpen)
    ActionManager.getInstance.asInstanceOf[ActionManagerImpl].preloadActions(new EmptyProgressIndicator)
    val actionGroup: ActionGroup = ActionManager.getInstance.getAction(IdeActions.GROUP_MAIN_TOOLBAR).asInstanceOf[ActionGroup]
    val toolbar: ActionToolbar = ActionManager.getInstance.createActionToolbar(ActionPlaces.MAIN_TOOLBAR, actionGroup, true)
    toolbar.updateActionsImmediately()
    UIUtil.dispatchAllInvocationEvents()
    myProject = null
    project
  }

  protected def addScalaLibrary(project: Project): ScalaLibraryLoader = {
    val module = ModuleManager.getInstance(project).getModules()(0)
    val libraryLoader = ScalaLibraryLoader.withMockJdk(project, module, null)
    libraryLoader.loadScala(TestUtils.DEFAULT_SCALA_SDK_VERSION)
    libraryLoader
  }

  protected def closeAndDispose(project: Project): Unit = {
    ProjectManagerEx.getInstanceEx.closeAndDispose(project)
    assertFalse(project.isOpen)
    assertTrue(project.isDisposed)
  }

  def testLeaksAfterProjectDispose(): Unit = {

    val project = loadAndSetupProject(projectPath)
    val libraryLoader = addScalaLibrary(project)

    doSomeWork(project)

    libraryLoader.clean()

    val allRoots = allRootsForProject(project)

    closeAndDispose(project)

    checkLeak[ScalaPsiManager](allRoots, classOf[ScalaPsiManager], m => m.project == project)
    checkLeak[ScalaFile](allRoots, classOf[ScalaFile], f => f.getProject == project)
  }

  def doSomeWork(project: Project): Unit = {
    val psiFile = findScalaFile(project)
    annotateFile(psiFile)
    runAllInspections(project)
    createRunConfiguration(psiFile)
  }

  def createRunConfiguration(scalaFile: ScalaFile): Unit = {
    val clazz = scalaFile.typeDefinitions.head
    val mainMethod = clazz.members.collectFirst {
      case f: ScFunctionDefinition if f.name == "main" => f
    }
    val context = new ConfigurationContext(mainMethod.get.getFirstChild)
    val result = context.getConfiguration
    assertNotNull("Run configuration wasn't created", result)
  }

  def runAllInspections(project: Project): Unit = {
    val inspectionManager = InspectionManager.getInstance(project).asInstanceOf[InspectionManagerEx]
    val inspectionProfile = new InspectionProfileImpl("test")
    InspectionProfileImpl.initAndDo(new Computable[AnyRef]() {
      def compute: AnyRef = {
        inspectionProfile.initInspectionTools(project)
        InspectionProjectProfileManager.getInstance(project).updateProfile(inspectionProfile)
        InspectionProjectProfileManager.getInstance(project).setProjectProfile(inspectionProfile.getName)
        null
      }
    })

    val tools = inspectionProfile.getAllEnabledInspectionTools(project).asScala
      .toIterator
      .flatMap(_.getTools.asScala)
      .map(_.getTool)
      .filter(_.getLanguage == ScalaLanguage.Instance.getID)
      .collect {case local: LocalInspectionToolWrapper => local.getTool}

    val scalaFile = findScalaFile(project)

    tools.foreach {
      _.processFile(scalaFile, inspectionManager)
    }
  }

  private def annotateFile(psiFile: PsiFile): Unit = {
    new ScalaAnnotator().annotate(psiFile, new AnnotatorHolderMock)
  }

  def checkLeak[T](root: AnyRef, clazz: Class[T], isLeak: T => Boolean): Unit = {
    LeakHunter.checkLeak[T](root, clazz, new Processor[T] {
      override def process(t: T): Boolean = isLeak(t)
    })
  }

  def allRootsForProject(project: Project): Seq[AnyRef] = {
    val picoContainer = project.getPicoContainer
    LeakHunter.allRoots().asScala :+ picoContainer
  }
}