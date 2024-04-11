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

  private[buildSystem]
  def addScalaSampleCode(project: Project, path: String, isScala3: Boolean, packagePrefix: Option[String], withOnboardingTips: Boolean): VirtualFile = {
    val manager = FileTemplateManager.getInstance(project)
    val shouldRenderOnboardingTips: Boolean = Registry.is("doc.onboarding.tips.render")
    val (templateName, fileName) = (isScala3, withOnboardingTips, shouldRenderOnboardingTips) match {
      case (true, true, true)    => ("scala3-sample-code-tips-rendered.scala", "main.scala")
      case (true, true, false)   => ("scala3-sample-code-tips.scala",          "main.scala")
      case (true, false, _)      => ("scala3-sample-code.scala",               "main.scala")
      case (false, true, true)   => ("scala-sample-code-tips-rendered.scala",  "Main.scala")
      case (false, true, false)  => ("scala-sample-code-tips.scala",           "Main.scala")
      case (false, false, _)     => ("scala-sample-code.scala",                "Main.scala")
    }

    val variables = {
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

    val sourceCode = manager.getInternalTemplate(templateName).getText(variables.asJava)

    if (withOnboardingTips) {
      val onboardingInfo = new OnboardingTipsInstallationInfo(sourceCode, fileName, breakpointSelector)
      NewProjectOnboardingTips
        .EP_NAME
        .getExtensionList
        .asScala
        .foreach(_.installTips(project, onboardingInfo))
    }

    inWriteAction {
      val fileDirectory = createDirectoryIfMissing(path)
      val file: VirtualFile = fileDirectory.findOrCreateChildData(this, fileName)
      VfsUtil.saveText(file, sourceCode)
      file
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

  private val breakpointSelector: kotlin.jvm.functions.Function1[_ >: CharSequence, java.lang.Integer] =
    str => {
      val index = str.indexOf("""println(s"i = $i")""")
      if (index >= 0) index else null
    }
}
