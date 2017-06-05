package org.jetbrains.plugins.scala.memoryLeaks

import java.nio.file.Paths

import com.intellij.codeInspection.ex.{InspectionProfileImpl, InspectionToolWrapper, LocalInspectionToolWrapper}
import com.intellij.codeInspection.{InspectionManager, InspectionProfile}
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{DumbService, Project, ProjectManager}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Condition
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.{LeakHunter, PlatformTestCase}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, LibraryLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.debugger.Scala_2_10
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.TestUtils.getTestDataPath
import org.junit.Assert._

import scala.collection.JavaConverters._

/**
  * @author Nikolay.Tropin
  */
class MemoryLeakTest extends PlatformTestCase {

  import MemoryLeakTest._

  private val projectPath = Paths.get(getTestDataPath, "memoryLeaks", "HelloScala")

  override protected def setUpProject(): Unit = {}

  private def loadAndSetupProject(): Project = {
    implicit val project = ProjectManager.getInstance.loadAndOpenProject(projectPath.toString)
    assertNotNull(project)

    do {
      UIUtil.dispatchAllInvocationEvents()
    } while (DumbService.getInstance(project).isDumb)

    myProject = project
    StartupManager.getInstance(project) match {
      case manager: StartupManagerImpl => assertTrue(manager.postStartupActivityPassed)
    }

    assertTrue(project.isOpen)
    ActionManager.getInstance match {
      case manager: ActionManagerImpl =>
        manager.preloadActions(new EmptyProgressIndicator)

        val actionGroup = manager.getAction(IdeActions.GROUP_MAIN_TOOLBAR).asInstanceOf[ActionGroup]
        manager.createActionToolbar(ActionPlaces.MAIN_TOOLBAR, actionGroup, true).updateActionsImmediately()
    }

    UIUtil.dispatchAllInvocationEvents()
    myProject = null

    librariesLoaders.foreach(_.init(Scala_2_10))

    project
  }

  private def librariesLoaders(implicit project: ProjectContext): Seq[LibraryLoader] = {
    implicit val module = ModuleManager.getInstance(project).getModules()(0)
    Seq(
      ScalaLibraryLoader(),
      JdkLoader()
    )
  }

  private def closeAndDispose(implicit project: ProjectContext): Unit = {
    librariesLoaders.foreach(_.clean())
    ProjectManagerEx.getInstanceEx.closeAndDispose(project)

    assertFalse(project.isOpen)
    assertTrue(project.project.isDisposed)
  }

  def testLeaksAfterProjectDispose(): Unit = {
    implicit val project = loadAndSetupProject()

    doSomeWork
    val allRoots = allRootsForProject

    closeAndDispose

    checkLeak[ScalaPsiManager](allRoots, classOf[ScalaPsiManager], m => m.project == project)
    checkLeak[ScalaFile](allRoots, classOf[ScalaFile], f => f.getProject == project)
  }

  private def doSomeWork(implicit project: ProjectContext): Unit = {
    val file = findFile("HelloWorld.scala").asInstanceOf[ScalaFile]

    processFile(file)
    assertNotNull(createRunConfiguration(file))
  }

  private def checkLeak[T](root: AnyRef, clazz: Class[T], isLeak: T => Boolean): Unit = {
    LeakHunter.checkLeak(root, clazz, new Condition[T] {
      override def value(t: T): Boolean = isLeak(t)
    })
  }

  private def allRootsForProject(implicit project: ProjectContext): Seq[AnyRef] =
    LeakHunter.allRoots().asScala :+ project.getPicoContainer
}

object MemoryLeakTest {

  private def findFile(fileName: String)
                      (implicit project: ProjectContext): PsiFile = {
    val file = project.getBaseDir.findChild("src").findChild(fileName)
    PsiManager.getInstance(project).findFile(file)
  }

  private def processFile(implicit file: PsiFile): Unit = {
    annotateFile
    inspectFile
  }

  private def createRunConfiguration(file: ScalaFile): RunnerAndConfigurationSettings = {
    val clazz = file.typeDefinitions.head
    val mainMethod = clazz.members.collectFirst {
      case function: ScFunctionDefinition if function.name == "main" => function
    }.get

    new ConfigurationContext(mainMethod.getFirstChild).getConfiguration
  }

  private[this] def annotateFile(implicit file: PsiFile): Unit =
    ScalaAnnotator.forProject.annotate(file, new AnnotatorHolderMock(file))

  private[this] def inspectFile(implicit file: PsiFile): Unit = {

    val tools = createInspectionTools
      .filter(_.getLanguage == ScalaLanguage.INSTANCE.getID)
      .collect {
        case toolWrapper: LocalInspectionToolWrapper => toolWrapper.getTool
      }

    val inspectionManager = InspectionManager.getInstance(file.getProject)
    tools.foreach {
      _.processFile(file, inspectionManager)
    }
  }

  private[this] def createInspectionTools(implicit project: ProjectContext): Seq[InspectionToolWrapper[_, _]] = {
    import scala.collection.JavaConversions._
    createInspectionProfile.getAllEnabledInspectionTools(project)
      .flatMap(_.getTools)
      .map(_.getTool)
  }

  private[this] def createInspectionProfile(implicit project: ProjectContext): InspectionProfile = {
    val result = new InspectionProfileImpl("test")
    result.initInspectionTools(project)

    InspectionProjectProfileManager.getInstance(project)
      .setRootProfile(result.getName)

    result
  }
}