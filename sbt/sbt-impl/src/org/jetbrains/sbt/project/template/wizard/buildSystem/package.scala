package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.JavaStartersBundle.message
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.wizard.{AbstractNewProjectWizardStep, NewProjectOnboardingTips, OnboardingTipsInstallationInfo}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, inWriteAction}

import scala.jdk.CollectionConverters.MapHasAsJava
import scala.jdk.CollectionConverters.ListHasAsScala

package object buildSystem {

  type BreakportSelectorType = kotlin.jvm.functions.Function1[_ >: CharSequence, java.lang.Integer]

  private[buildSystem] def setProjectOrModuleSdk(
    project: Project,
    parentStep: AbstractNewProjectWizardStep,
    builder: ModuleBuilder,
    sdk: Option[Sdk]
  ): Unit = {
    val context = parentStep.getContext
    if (context.isCreatingNewProject) {
      // New project with a single module: set project JDK
      context.setProjectJdk(sdk.orNull)
    } else {
      // New module in an existing project: set module JDK
      val isSameSDK: Boolean = (for {
        jdk1 <- Option(ProjectRootManager.getInstance(project).getProjectSdk)
        jdk2 <- sdk
      } yield jdk1.getName == jdk2.getName).contains(true)
      builder.setModuleJdk(if (isSameSDK) null else sdk.orNull)
    }
  }

  private def templatesAndFiles(isScala3: Boolean, withOnboardingTips: Boolean, shouldRenderOnboardingTips: Boolean): Seq[(String, String, String)] =
    (isScala3, withOnboardingTips, shouldRenderOnboardingTips) match {
      case (true, true, true) =>
        Seq(("scala3-sample-code-tips-rendered.scala", "main.scala", """println(s"i = $i")"""))
      case (true, true, false) =>
        Seq(("scala3-sample-code-tips.scala", "main.scala", """println(s"i = $i")"""))
      case (true, false, _) =>
        Seq(("scala3-sample-code.scala", "main.scala", """println(s"i = $i")"""))
      case (false, true, true) =>
        Seq(("scala-sample-code-tips-rendered.scala","Main.scala", """println(s"i = $i")"""))
      case (false, true, false) =>
        Seq(("scala-sample-code-tips.scala", "Main.scala", """println(s"i = $i")"""))
      case (false, false, _) =>
        Seq(("scala-sample-code.scala", "Main.scala", """println(s"i = $i")"""))
    }

  private def onboardingTipsVariables(withOnboardingTips: Boolean, shouldRenderOnboardingTips: Boolean, packagePrefix: Option[String]): Map[String, String] = {
    if (withOnboardingTips && shouldRenderOnboardingTips) {
      def shortcut(actionId: String) = s"""<shortcut actionId="$actionId"/>"""
      def icon(allIconsId: String) = s"""<icon src="$allIconsId"/>"""
      Map(
        "RunComment1"           -> message("onboarding.run.comment.render.1", shortcut(IdeActions.ACTION_DEFAULT_RUNNER)),
        "RunComment2"           -> message("onboarding.run.comment.render.2", icon("AllIcons.Actions.Execute")),
        "ShowIntentionComment1" -> message("onboarding.show.intention.tip.comment.render.1", shortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)),
        "ShowIntentionComment2" -> message("onboarding.show.intention.tip.comment.render.2", ApplicationNamesInfo.getInstance.getFullProductName),
        "DebugComment1"         -> message("onboarding.debug.comment.render.1", shortcut(IdeActions.ACTION_DEFAULT_DEBUGGER), icon("AllIcons.Debugger.Db_set_breakpoint")),
        "DebugComment2"         -> message("onboarding.debug.comment.render.2", shortcut(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)),
      )
    } else if (withOnboardingTips) {
      val tipsContext = new KeymapTextContext {
        override def isSimplifiedMacShortcuts: Boolean = SystemInfo.isMac
      }
      Map(
        "SearchEverywhereComment1"  -> message("onboarding.search.everywhere.tip.comment.1", "Shift"),
        "SearchEverywhereComment2" -> message("onboarding.search.everywhere.tip.comment.2"),
        "ShowIntentionComment1"     -> message("onboarding.show.intention.tip.comment.1", tipsContext.getShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)),
        "ShowIntentionComment2"     -> message("onboarding.show.intention.tip.comment.2", ApplicationNamesInfo.getInstance.getFullProductName),
        "RunComment"                -> message("onboarding.run.comment", tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_RUNNER)),
        "DebugComment1"             -> message("onboarding.debug.comment.1", tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_DEBUGGER)),
        "DebugComment2"             -> message("onboarding.debug.comment.2", tipsContext.getShortcutText(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT))
      )
    } else
      Map.empty[String, String]
  } ++ packagePrefix.map(prefix => Map("PACKAGE_NAME" -> prefix)).getOrElse(Map.empty)

  private def installOnboardingTips(project: Project, sourceCode: String, fileName: String, breakpointLine: String = ""): Unit = {
    val breakpointSelector = if (breakpointLine.nonEmpty) createBreakpointSelector(breakpointLine) else null
    val onboardingInfo = new OnboardingTipsInstallationInfo(sourceCode, fileName, breakpointSelector)
    NewProjectOnboardingTips
      .EP_NAME
      .getExtensionList
      .asScala
      .foreach(_.installTips(project, onboardingInfo))
  }

  private[buildSystem]
  def addScalaSampleCode(project: Project, path: String, isScala3: Boolean, packagePrefix: Option[String], withOnboardingTips: Boolean): Seq[VirtualFile] = {
    val shouldRenderOnboardingTips: Boolean = Registry.is("doc.onboarding.tips.render")
    val manager = FileTemplateManager.getInstance(project)
    val variables = onboardingTipsVariables(withOnboardingTips, shouldRenderOnboardingTips, packagePrefix).asJava

    templatesAndFiles(isScala3, withOnboardingTips, shouldRenderOnboardingTips)
      .map { case (templateName, fileName, breakpointLine) =>
        val sourceCode = manager.getInternalTemplate(templateName).getText(variables)
        if (withOnboardingTips) installOnboardingTips(project, sourceCode, fileName, breakpointLine)
        inWriteAction {
          val fileDirectory = createDirectoryIfMissing(path)
          val file: VirtualFile = fileDirectory.findOrCreateChildData(this, fileName)
          VfsUtil.saveText(file, sourceCode)
          file
        }
      }
  }

  private def createDirectoryIfMissing(path: String): VirtualFile =
    Option(VfsUtil.createDirectoryIfMissing(path))
      .getOrElse(throw new IllegalStateException("Unable to create src directory"))

  private[buildSystem]
  def addGitIgnore(project: Project, path: String): VirtualFile = {
    val manager = FileTemplateManager.getInstance(project)
    val contents = manager.getInternalTemplate("scala-gitignore.txt").getText
    inWriteAction {
      val fileDirectory = createDirectoryIfMissing(path)
      val file = fileDirectory.findOrCreateChildData(this, ".gitignore")
      VfsUtil.saveText(file, contents)
      file
    }
  }

  private def createBreakpointSelector(line: String): kotlin.jvm.functions.Function1[_ >: CharSequence, java.lang.Integer] =
    str => {
      val index = str.indexOf(line)
      if (index >= 0) index else null
    }
}
