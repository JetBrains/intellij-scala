package org.jetbrains.plugins.scala.compiler.testUtils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{ModuleRootManager, ModuleRootModificationUtil, ProjectRootManager}
import com.intellij.testFramework.CompilerTester

import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}
import scala.util.control.NonFatal

private final class SdkStateRestoringCompilerTester(project: Project, modules: Seq[Module]) {
  import SdkStateRestoringCompilerTester._

  private val projectSdkState: SdkState = SdkState("project SDK", Option(ProjectRootManager.getInstance(project).getProjectSdk))
  private val moduleSdkStates: Seq[ModuleSdkState] = modules.map { module =>
    ModuleSdkState(module, SdkState(s"module ${module.getName} SDK", Option(ModuleRootManager.getInstance(module).getSdk)))
  }

  private val compiler = new CompilerTester(project, modules.asJava, null)

  def make(): Seq[CompilerMessage] =
    compiler.make().asScala.toSeq

  def tearDown(): Unit = {
    var tearDownFailure: Option[Throwable] = None
    try {
      compiler.tearDown()
    } catch {
      case NonFatal(exception) =>
        tearDownFailure = Some(exception)
    }

    val restoreFailure: Option[Throwable] = try {
      restoreSdkState()
      assertSdkStateRestored()
      None
    } catch {
      case NonFatal(exception) =>
        Some(exception)
    }

    (tearDownFailure, restoreFailure) match {
      case (Some(tearDownException), Some(restoreException)) =>
        tearDownException.addSuppressed(restoreException)
        throw tearDownException
      case (Some(tearDownException), None) =>
        throw tearDownException
      case (None, Some(restoreException)) =>
        throw restoreException
      case (None, None) =>
    }
  }

  private def restoreSdkState(): Unit = inWriteAction {
    restoreRegisteredSdk(projectSdkState)
    moduleSdkStates.foreach(state => restoreRegisteredSdk(state.sdkState))

    setProjectSdkIfChanged(projectSdkState)
    moduleSdkStates.foreach(setModuleSdkIfChanged)
  }

  private def restoreRegisteredSdk(state: SdkState): Unit =
    if (state.wasRegisteredBefore) {
      state.sdk.foreach { sdk =>
        if (!isRegistered(sdk)) {
          ProjectJdkTable.getInstance().addJdk(sdk, project)
        }
      }
    }

  private def setProjectSdkIfChanged(state: SdkState): Unit = {
    val currentSdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    if (!sameSdk(currentSdk, state.sdk)) {
      ProjectRootManager.getInstance(project).setProjectSdk(state.sdk.orNull)
    }
  }

  private def setModuleSdkIfChanged(state: ModuleSdkState): Unit = {
    val currentSdk = Option(ModuleRootManager.getInstance(state.module).getSdk)
    if (!sameSdk(currentSdk, state.sdkState.sdk)) {
      ModuleRootModificationUtil.setModuleSdk(state.module, state.sdkState.sdk.orNull)
    }
  }

  private def assertSdkStateRestored(): Unit = {
    val problems = sdkStateProblems()
    if (problems.nonEmpty) {
      throw new AssertionError(
        s"""Expected CompilerTester SDK state to be restored after tearDown, but found differences:
           |${problems.mkString(System.lineSeparator())}""".stripMargin
      )
    }
  }

  private def sdkStateProblems(): Seq[String] = {
    val projectProblems = sdkProblems(projectSdkState, Option(ProjectRootManager.getInstance(project).getProjectSdk))
    val moduleProblems = moduleSdkStates.flatMap { state =>
      sdkProblems(state.sdkState, Option(ModuleRootManager.getInstance(state.module).getSdk))
    }
    projectProblems ++ moduleProblems
  }

  private def sdkProblems(state: SdkState, currentSdk: Option[Sdk]): Seq[String] = {
    val sdkReferenceProblem: Option[String] =
      Option.when(!sameSdk(currentSdk, state.sdk))(
        s"${state.label}: expected ${sdkText(state.sdk)}, actual ${sdkText(currentSdk)}"
      )

    val jdkTableProblem: Option[String] =
      state.sdk.filter(_ => state.wasRegisteredBefore).flatMap { sdk =>
        Option.when(!isRegistered(sdk))(s"${state.label}: expected SDK to be registered in ProjectJdkTable: ${sdkText(sdk)}")
      }

    sdkReferenceProblem.toSeq ++ jdkTableProblem.toSeq
  }

  private def inWriteAction(body: => Unit): Unit =
    ApplicationManager.getApplication.runWriteAction(new Runnable {
      override def run(): Unit = body
    })
}

private object SdkStateRestoringCompilerTester {
  private final case class ModuleSdkState(module: Module, sdkState: SdkState)

  private final case class SdkState(label: String, sdk: Option[Sdk]) {
    val wasRegisteredBefore: Boolean = sdk.exists(isRegistered)
  }

  private def sameSdk(left: Option[Sdk], right: Option[Sdk]): Boolean =
    (left, right) match {
      case (Some(leftSdk), Some(rightSdk)) => leftSdk eq rightSdk
      case (None, None) => true
      case _ => false
    }

  private def isRegistered(sdk: Sdk): Boolean =
    ProjectJdkTable.getInstance().getAllJdks.exists(_ eq sdk)

  private def sdkText(sdk: Sdk): String =
    if (sdk == null) "<none>"
    else s"${sdk.getName} (${sdk.getSdkType.getName}, ${sdk.getHomePath})"

  private def sdkText(sdk: Option[Sdk]): String =
    sdk.fold("<none>")(sdkText)
}
