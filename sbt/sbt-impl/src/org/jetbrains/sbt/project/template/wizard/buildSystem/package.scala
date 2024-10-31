package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.wizard.{AbstractNewProjectWizardStep, NewProjectOnboardingTips, OnboardingTipsInstallationInfo}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import kotlin.jvm.functions.Function1
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.actions.ShowTypeInfoAction
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, inWriteAction}

import java.lang.{Integer => JInt}
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava}

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

  def addScalaSampleCode(project: Project, path: String, isScala3: Boolean, packagePrefix: Option[String], withOnboardingTips: Boolean): Seq[VirtualFile] = {
    val shouldRenderOnboardingTips: Boolean = Registry.is("doc.onboarding.tips.render")
    val advancedTipsEnabled = Registry.is("scala.advanced.onboarding.tips")
    val manager = FileTemplateManager.getInstance(project)
    val variables = onboardingTipsVariables(
      withOnboardingTips = withOnboardingTips,
      shouldRenderOnboardingTips = shouldRenderOnboardingTips,
      advancedTipsEnabled = advancedTipsEnabled,
      packagePrefix = packagePrefix
    ).asJava

    val samples = templatesAndFiles(
      isScala3 = isScala3,
      withOnboardingTips = withOnboardingTips,
      shouldRenderOnboardingTips = shouldRenderOnboardingTips,
      advancedTipsEnabled = advancedTipsEnabled
    )
    val sourceAndFiles =
      samples
        .zipWithIndex // TODO: This is a hack to install onboarding tips in main.scala only; remove it when the platform allows for tips in multiple files
        .map { case (Sample(templateName, fileName, breakpoint), index) =>
          val sourceCode = manager.getInternalTemplate(templateName).getText(variables)
          if (withOnboardingTips && index == samples.size - 1) installOnboardingTips(project, sourceCode, fileName, breakpoint)
          (sourceCode, fileName)
        }

    inWriteAction {
      val fileDirectory = createDirectoryIfMissing(path)
      sourceAndFiles.map { case (sourceCode, fileName) =>
        val file: VirtualFile = fileDirectory.findOrCreateChildData(this, fileName)
        VfsUtil.saveText(file, sourceCode)
        file
      }
    }
  }

  @deprecated("Don't use this method. .gitignore is now added in the wizard via AssetsNewProjectWizardStep")
  @ApiStatus.ScheduledForRemoval(inVersion = "2025.1")
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

  private def createDirectoryIfMissing(path: String): VirtualFile =
    Option(VfsUtil.createDirectoryIfMissing(path))
      .getOrElse(throw new IllegalStateException("Unable to create src directory"))

  private def templatesAndFiles(isScala3: Boolean,
                                withOnboardingTips: Boolean,
                                shouldRenderOnboardingTips: Boolean,
                                advancedTipsEnabled: Boolean): Seq[Sample] =
    (isScala3, withOnboardingTips, shouldRenderOnboardingTips, advancedTipsEnabled) match {
      case (true, true, true, true) =>
        Seq(
          Sample("scala3-xray-tips-rendered.scala", "InlayHintsAndXRay.scala"),
          Sample("scala3-collections-tips-rendered.scala", "collections.scala"),
          Sample("scala3-main-tips-rendered.scala", "main.scala", """println(s"i = $i")"""),
        )
      case (true, true, true, false) =>
        Seq(
          Sample("scala3-main-tips-rendered.scala", "main.scala", """println(s"i = $i")"""),
        )
      case (true, true, false, true) =>
        Seq(
          Sample("scala3-xray-tips.scala", "InlayHintsAndXRay.scala"),
          Sample("scala3-collections-tips.scala", "collections.scala"),
          Sample("scala3-main-tips.scala", "main.scala", """println(s"i = $i")"""),
        )
      case (true, true, false, false) =>
        Seq(
          Sample("scala3-main-tips.scala", "main.scala", """println(s"i = $i")"""),
        )
      case (true, false, _, _) =>
        Seq(Sample("scala3-main.scala", "main.scala", """println(s"i = $i")"""))
      case (false, true, true, true) =>
        Seq(
          Sample("scala2-xray-tips-rendered.scala", "InlayHintsAndXRay.scala"),
          Sample("scala2-collections-tips-rendered.scala", "Collections.scala"),
          Sample("scala2-main-tips-rendered.scala","Main.scala", """println(s"i = $i")"""),
        )
      case (false, true, true, false) =>
        Seq(
          Sample("scala2-main-tips-rendered.scala","Main.scala", """println(s"i = $i")"""),
        )
      case (false, true, false, true) =>
        Seq(
          Sample("scala2-xray-tips.scala", "InlayHintsAndXRay.scala"),
          Sample("scala2-collections-tips.scala", "Collections.scala"),
          Sample("scala2-main-tips.scala", "Main.scala", """println(s"i = $i")"""),
        )
      case (false, true, false, false) =>
        Seq(
          Sample("scala2-main-tips.scala", "Main.scala", """println(s"i = $i")"""),
        )
      case (false, false, _, _) =>
        Seq(Sample("scala2-main.scala", "Main.scala", """println(s"i = $i")"""))
    }

  private def onboardingTipsVariables(withOnboardingTips: Boolean,
                                      shouldRenderOnboardingTips: Boolean,
                                      advancedTipsEnabled: Boolean,
                                      packagePrefix: Option[String]): Map[String, String] = {
    if (withOnboardingTips) {
      if (shouldRenderOnboardingTips) renderedVariables else unrenderedVariables
    } else Map.empty[String, String]
  } ++ packagePrefix.map(prefix => Map("PACKAGE_NAME" -> prefix, "advancedTipsEnabled" -> advancedTipsEnabled.toString)).getOrElse(Map.empty)

  private def installOnboardingTips(project: Project, sourceCode: String, fileName: String, breakpoint: Function1[_ >: CharSequence, JInt]): Unit = {
    val onboardingInfo = new OnboardingTipsInstallationInfo(sourceCode, fileName, breakpoint)
    NewProjectOnboardingTips
      .EP_NAME
      .getExtensionList
      .asScala
      .foreach(_.installTips(project, onboardingInfo))
  }

  private def shortcut(actionId: String) = s"""<shortcut actionId="$actionId"/>"""
  private def raw(key: String) = s"""<shortcut raw="$key"/>"""
  private def icon(allIconsId: String) = s"""<icon src="$allIconsId"/>"""

  private lazy val renderedVariables =
    Map(
      // actions
      "ActionTypeInfo"            -> shortcut(ShowTypeInfoAction.ActionId),
      "ActionImplicitHints"       -> shortcut("Scala.ShowImplicits"), // TODO: Put action ids in one place
      "ActionSettings"            -> shortcut(IdeActions.ACTION_SHOW_SETTINGS),
      "ActionRun"                 -> shortcut(IdeActions.ACTION_DEFAULT_RUNNER),
      "ActionShowIntention"       -> shortcut(IdeActions.ACTION_SHOW_INTENTION_ACTIONS),
      "ActionDebug"               -> shortcut(IdeActions.ACTION_DEFAULT_DEBUGGER),
      "ActionSetBreakpoint"       -> shortcut(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT),
      "ActionSelectInProjectView" -> shortcut("SelectInProjectView"),
      // icons
      "IconEye"                   -> icon("AllIcons.General.InspectionsEye"),
      "IconExecute"               -> icon("AllIcons.Actions.Execute"),
      "IconBreakpoint"            -> icon("AllIcons.Debugger.Db_set_breakpoint"),
      "IconLocate"                -> icon("AllIcons.General.Locate"),
      // others
      "META"                      -> raw("META"),
      "CONTROL"                   -> raw("CONTROL"),
      "COMMAND"                   -> raw(if (ClientSystemInfo.isMac) "META" else "CONTROL"),
      "ProductName"               -> ApplicationNamesInfo.getInstance.getFullProductName
    )

  private lazy val tipsContext = new KeymapTextContext {
    override def isSimplifiedMacShortcuts: Boolean = ClientSystemInfo.isMac
  }

  private lazy val unrenderedVariables =
    Map(
      // actions
      "ActionTypeInfo"            -> tipsContext.getShortcutText(ShowTypeInfoAction.ActionId),
      "ActionImplicitHints"       -> tipsContext.getShortcutText("Scala.ShowImplicits"),
      "ActionSettings"            -> tipsContext.getShortcutText(IdeActions.ACTION_SHOW_SETTINGS),
      "ActionRun"                 -> tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_RUNNER),
      "ActionShowIntention"       -> tipsContext.getShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS),
      "ActionDebug"               -> tipsContext.getShortcutText(IdeActions.ACTION_DEFAULT_DEBUGGER),
      "ActionSetBreakpoint"       -> tipsContext.getShortcutText(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT),
      "ActionSelectInProjectView" -> tipsContext.getShortcutText("SelectInProjectView"),
      // others
      "META"                      -> "Meta",
      "CONTROL"                   -> "Control",
      "COMMAND"                   -> (if (ClientSystemInfo.isMac) "Meta" else "Control"),
      "ProductName"               -> ApplicationNamesInfo.getInstance.getFullProductName
    )

  final private case class Sample(templateName: String, fileName: String, breakpoint: Function1[_ >: CharSequence, JInt])

  private object Sample {
    def apply(templateName: String, fileName: String, breakpointLine: String): Sample =
      new Sample(templateName, fileName, createBreakpointSelector(breakpointLine))

    def apply(templateName: String, fileName: String): Sample =
      new Sample(templateName, fileName, createBreakpointSelector())

    def apply(templateName: String, fileName: String, index: Int): Sample =
      new Sample(templateName, fileName, createBreakpointSelector(index))

    private def createBreakpointSelector(line: String): Function1[_ >: CharSequence, JInt] =
      str => {
        val index = str.indexOf(line)
        if (index >= 0) index else null
      }

    private def createBreakpointSelector(): Function1[_ >: CharSequence, JInt] =
      _ =>  null

    private def createBreakpointSelector(index: Int): Function1[_ >: CharSequence, JInt] =
      _ => index
  }
}
