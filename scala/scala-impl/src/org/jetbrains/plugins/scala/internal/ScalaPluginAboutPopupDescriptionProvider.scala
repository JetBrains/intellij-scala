package org.jetbrains.plugins.scala.internal

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.scala.internal.ScalaPluginAboutPopupDescriptionProvider._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaCompileServerSettings, ScalaProjectSettings}
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * Adds additional details to display in the `Help | About` dialog (and add to "Copy and Close" action)
 *
 * The current implementation adds some Application and Project level settings when they are different from the default values.
 *
 * @note It doesn't handle all the settings exhaustively.
 *       Ideally, there should be a unified mechanism in IntelliJ to collect all non-default settings.
 *       However, it's missing at the moment (see IJPL-163081)
 * @note there is also a separate action [[org.jetbrains.plugins.scala.internal.ScalaGeneralTroubleInfoCollector]]
 *       but it's rarely used compared to "About".
 */
@Internal
class ScalaPluginAboutPopupDescriptionProvider extends AboutPopupDescriptionProvider {

  override def getDescription: String = null

  /**
   * Details that are added to the content of the "Copy and Close" action in the "About" dialog.
   *
   * Example output: {{{
   * Scala plugin:
   *   === group name 1 ===
   *     a.b.c=true
   *     d.e.g=false
   *   === group name 2 ===
   *     x.y.z=true
   * }}}
   */
  override def getExtendedDescription: String = {
    val ideDetails = collectApplicationLevelDetails()

    val activeProject = if (ApplicationManager.getApplication.isUnitTestMode)
      ProjectManager.getInstance().getOpenProjects.find(p => !p.isDisposed)
    else
      Option(ProjectUtil.getActiveProject)
    val activeProjectDetails = activeProject.toSeq.flatMap(collectProjectLevelDetails)

    val allDetails = ideDetails ++ activeProjectDetails
    val hasNonDefaultSettings = allDetails.exists(_.nonDefaultSettings.nonEmpty)
    if (hasNonDefaultSettings) {
      val detailsText = buildGroupsText(allDetails)
      s"""Scala plugin:
         |${detailsText.indented(1)}""".stripMargin
    }
    else null
  }

  private def collectApplicationLevelDetails(): Seq[NonDefaultSettingsGroup] = {
    val compileServerSettings = ScalaCompileServerSettings.getInstance
    val compileServerSettingsMappings = buildSettingsMappings(compileServerSettings, DefaultSettings.ScalaCompileServerSettings)(
      SettingLabels.CompileServerEnabled -> (_.COMPILE_SERVER_ENABLED),
      SettingLabels.UseProjectHomeAsWorkingDir -> (_.USE_PROJECT_HOME_AS_WORKING_DIR),
    )

    val scalaApplicationSettings = ScalaApplicationSettings.getInstance
    val smartKeysSettingsMappings = buildSettingsMappings(scalaApplicationSettings, DefaultSettings.SmartKeysSettings)(
      SettingLabels.IndentPastedLinesAtCaret -> (_.INDENT_PASTED_LINES_AT_CARET),
      SettingLabels.InsertMultilineQuotes -> (_.INSERT_MULTILINE_QUOTES),
      SettingLabels.UpgradeToInterpolated -> (_.UPGRADE_TO_INTERPOLATED),
      SettingLabels.WrapSingleExpressionBody -> (_.WRAP_SINGLE_EXPRESSION_BODY),
      SettingLabels.DeleteClosingBrace -> (_.DELETE_CLOSING_BRACE),
      SettingLabels.HandleBlockBracesInsertionAutomatically -> (_.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY),
      SettingLabels.HandleBlockBracesRemovalAutomatically -> (_.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY),
    )

    val compileServerSettingsGroup = collectNonDefaultSettingsGroup(SettingGroupNames.CompileServerSettings, compileServerSettingsMappings)
    val smartKeysSettingsGroup = collectNonDefaultSettingsGroup(SettingGroupNames.SmartKeysSettings, smartKeysSettingsMappings)
    Seq(
      compileServerSettingsGroup,
      smartKeysSettingsGroup
    )
  }

  private def collectProjectLevelDetails(project: Project): Seq[NonDefaultSettingsGroup] = {
    // Do not add Scala specific information if there is no Scala SDK in the project
    if (!project.hasScala)
      return Nil

    val sbtProjectSettings = SbtProjectSettings.forProject(project)
    val sbtSettingsMappings: Seq[SettingsMapping[_, _]] = sbtProjectSettings.toSeq.flatMap { settings =>
      buildSettingsMappings(settings, DefaultSettings.SbtProjectSettings)(
        SettingLabels.ResolveClassifiers -> (_.resolveClassifiers),
        SettingLabels.ResolveSbtClassifiers -> (_.resolveSbtClassifiers),

        SettingLabels.SeparateProdAndTestSources -> (_.separateProdAndTestSources),
        SettingLabels.UseSeparateCompilerOutputPaths -> (_.useSeparateCompilerOutputPaths),
        SettingLabels.OpenCrossCompiledScala3AsScala2 -> (_.preferScala2),

        SettingLabels.UseSbtShellForImport -> (_.useSbtShellForImport),
        SettingLabels.UseSbtShellForBuild -> (_.useSbtShellForBuild),
        SettingLabels.EnableDebugSbtShell -> (_.enableDebugSbtShell),
      )
    }

    val scalaProjectSettings = ScalaProjectSettings.getInstance(project)
    val scalaProjectSettingsMappings: Seq[SettingsMapping[_, _]] =
      buildSettingsMappings(scalaProjectSettings, DefaultSettings.ScalaProjectSettings)(
        SettingLabels.CompilerHighlightingScala2 -> (_.isCompilerHighlightingScala2),
        SettingLabels.CompilerHighlightingScala3 -> (_.isCompilerHighlightingScala3),
        SettingLabels.CompilerHighlightingUseCompilerRanges -> (_.isUseCompilerRanges),
        SettingLabels.CompilerHighlightingUseCompilerTypes -> (_.isUseCompilerTypes),
        SettingLabels.TypeAwareHighlighting -> (_.isTypeAwareHighlightingEnabled),
        SettingLabels.IncrementalHighlighting -> (_.isIncrementalHighlighting),
      )

    val scalaCompilerSettingsMappings: Seq[SettingsMapping[_, _]] =
      buildSettingsMappings(ScalaCompilerConfiguration.instanceIn(project), DefaultSettings.ScalaCompilerSettings)(
        SettingLabels.IncrementalityType -> (_.incrementalityType),
      )

    val scalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
    val formatterSettingsMappings = collectFormatterSettings(scalaCodeStyleSettings)

    val scalaSettingsGroup = collectNonDefaultSettingsGroup(SettingGroupNames.ScalaSettingsForActiveProject, scalaProjectSettingsMappings)
    val sbtSettingsGroup = collectNonDefaultSettingsGroup(SettingGroupNames.SbtSettingsForActiveProject, sbtSettingsMappings)
    val scalaCompilerSettingsGroup = collectNonDefaultSettingsGroup(SettingGroupNames.ScalaCompilerSettings, scalaCompilerSettingsMappings)
    val formatterSettingsGroup = collectNonDefaultSettingsGroup(SettingGroupNames.FormatterSettings, formatterSettingsMappings)

    Seq(
      scalaSettingsGroup,
      sbtSettingsGroup,
      scalaCompilerSettingsGroup,
      formatterSettingsGroup,
    )
  }

  private def buildGroupsText(groups: Seq[NonDefaultSettingsGroup]): String = {
    val groupsTexts = groups.filter(_.nonDefaultSettings.nonEmpty).map(buildGroupText)
    groupsTexts.mkString("\n")
  }

  private def buildGroupText(group: NonDefaultSettingsGroup): String = {
    val groupInnerText = group.nonDefaultSettings.map(buildSettingValueTxt).mkString("\n")
    s"""=== ${group.groupName} ===
       |${groupInnerText.indented(1)}""".stripMargin
  }

  private def buildSettingValueTxt(settingValue: SettingValue): String =
    s"${settingValue.label}=${settingValue.value}"

  private implicit class StringOps(private val text: String) {
    def indented(level: Int): String = {
      val indent = "  " * level
      text.linesIterator.map(indent + _).mkString("\n")
    }
  }
}

object ScalaPluginAboutPopupDescriptionProvider {

  /**
   * This class represents a setting that is potentially displayed in the "About" extended description.
   * It represents the label used to display the setting, the setting value, it's default value
   * and also how to display the value in the description
   *
   * @param label           the label that will be represented the setting in the extended description
   * @param settings        instance that represents current settings
   * @param defaultSettings instance that represents the default settings
   * @param accessor        function that returns value of the setting from the setting instance
   * @param renderer        function that returns string representation of the setting value that is shown in the extended description.<br>
   *                        NOTE: It uses `toString` method by default. This should be enough for simple cases
   *                        (primitive types, enums) but would be not OK if we add settings with other types
   * @tparam T type of the settings
   */
  private case class SettingsMapping[T, V](
    label: String,
    settings: T,
    defaultSettings: T,
    accessor: T => V,
    renderer: V => String = (v: V) => v.toString
  )

  private case class NonDefaultSettingsGroup(groupName: String, nonDefaultSettings: Seq[SettingValue])
  /**
   * @param label setting label as it is displayed in the help details
   * @param value current setting value string representation
   */
  private case class SettingValue(label: String, value: String)

  private def collectFormatterSettings(scalaCodeStyleSettings: ScalaCodeStyleSettings): Seq[SettingsMapping[_, _]] = {
    // Create a custom mapping for formatter type, use the string representation instead of Int (ScalaCodeStyleSettings.FORMATTER)
    def renderFormatterType(formatterType: Int): String = formatterType match {
      case ScalaCodeStyleSettings.SCALAFMT_FORMATTER => "scalafmt"
      case ScalaCodeStyleSettings.INTELLIJ_FORMATTER => "intellij"
      case _ => s"<unsupported formatter type: $formatterType>" // this branch is unexpected in reality
    }

    val formatterSettingsMappings = Seq(SettingsMapping[ScalaCodeStyleSettings, Int](
      SettingLabels.Formatter, scalaCodeStyleSettings, DefaultSettings.ScalaCodeStyleSettings, _.FORMATTER, renderer = renderFormatterType
    ))

    // If ScalaFmt is enabled, check for non-default scalafmt-specific settings
    val scalafmtSpecificSettingsMappings = if (scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER())
      buildSettingsMappings(scalaCodeStyleSettings, DefaultSettings.ScalaCodeStyleSettings)(
        SettingLabels.ScalafmtShowInvalidCodeWarnings -> (_.SCALAFMT_SHOW_INVALID_CODE_WARNINGS),
        SettingLabels.ScalafmtUseIntellijFormatterForRangeFormat -> (_.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT),
        SettingLabels.ScalafmtReformatOnFilesSave -> (_.SCALAFMT_REFORMAT_ON_FILES_SAVE),
        SettingLabels.ScalafmtFallbackToDefaultSettings -> (_.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS),
      )
    else Nil
    formatterSettingsMappings ++ scalafmtSpecificSettingsMappings
  }

  private def buildSettingsMappings[T](settings: T, defaultSettings: T)(items: (String, T => Any)*): Seq[SettingsMapping[T, Any]] = {
    items.map { case (label, accessor) =>
      SettingsMapping(label, settings, defaultSettings, accessor)
    }
  }

  private def collectNonDefaultSettingsGroup(groupName: String, mappings: Seq[SettingsMapping[_, _]]): NonDefaultSettingsGroup = {
    val nonDefaultLabels = collectNonDefaultSettingsLabels(mappings)
    NonDefaultSettingsGroup(groupName, nonDefaultLabels)
  }

  /**
   * @return list of items in format `setting.label=non-default-value`
   *         for all settings that are different from the default value
   */
  private def collectNonDefaultSettingsLabels(mappings: Seq[SettingsMapping[_, _]]): Seq[SettingValue] =
    mappings.flatMap { case SettingsMapping(label, settings, defaultSettings, accessor, renderer) =>
      val currentValue = accessor(settings)
      val defaultValue = accessor(defaultSettings)
      if (currentValue != defaultValue) {
        val currentValueStr = renderer(currentValue)
        Some(SettingValue(label, currentValueStr))
      }
      else
        None
    }

  /**
   * Contains labels of the settings in the way how they are presented in the "About" extended description.
   *
   * Note: In theory, we could reuse some other existing representation of the strings. For example from FUS
   * (using field names, access via reflection) or directly from the UI (reading i18 bundle for English version).
   * But I did not want to reuse any existing string literals intentionally for several reasons:
   *  1. I use dots to be "visually-unified" with registry values in the "About" details
   *  2. Simplicity of first implementation: not relying on other modules with message bundles (e.t. sbt-api or compiler)
   */
  private object SettingLabels {
    //compiler-server settings (Settings | Build, Execution, Deployment | Compiler | Scala Compiler | Scala Compile Server)
    val CompileServerEnabled = "compile.server.enabled"
    val UseProjectHomeAsWorkingDir = "use.project.home.as.working.dir"

    //sbt project settings (Settings | Build, Execution, Deployment | Build Tools | sbt)
    val OpenCrossCompiledScala3AsScala2 = "open.cross.compiled.scala3.as.scala2"
    val SeparateProdAndTestSources = "separate.prod.and.test.sources"
    val UseSeparateCompilerOutputPaths = "use.separate.compiler.output.paths"
    val ResolveClassifiers = "resolve.classifiers"
    val ResolveSbtClassifiers = "resolve.sbt.classifiers"
    val UseSbtShellForImport = "use.sbt.shell.for.import"
    val UseSbtShellForBuild = "use.sbt.shell.for.build"
    val EnableDebugSbtShell = "enable.debug.sbt.shell"

    //scala project settings (Settings | Languages & Frameworks | Scala | Editor)
    val TypeAwareHighlighting = "type.aware.highlighting.enabled"
    val CompilerHighlightingScala2 = "compiler.highlighting.scala2.enabled"
    val CompilerHighlightingScala3 = "compiler.highlighting.scala3.enabled"
    val CompilerHighlightingUseCompilerRanges = "compiler.highlighting.use.compiler.ranges"
    val CompilerHighlightingUseCompilerTypes = "compiler.highlighting.use.compiler.types"
    val IncrementalHighlighting = "incremental.highlighting.enabled"

    //scala compiler settings (Settings | Build, Execution, Deployment | Compiler | Scala Compiler)
    val IncrementalityType = "incrementality.type"

    //formatter settings (Settings | Editor | Code Style | Scala)
    val Formatter = "formatter"
    val ScalafmtShowInvalidCodeWarnings = "scalafmt.show.invalid.code.warnings"
    val ScalafmtUseIntellijFormatterForRangeFormat = "scalafmt.use.intellij.formatter.for.range.format"
    val ScalafmtReformatOnFilesSave = "scalafmt.reformat.on.files.save"
    val ScalafmtFallbackToDefaultSettings = "scalafmt.fallback.to.default.settings"

    //smart keys settings (Settings | Editor | General | Smart Keys | Scala)
    val IndentPastedLinesAtCaret = "indent.pasted.lines.at.caret"
    val InsertMultilineQuotes = "insert.multiline.quotes"
    val UpgradeToInterpolated = "upgrade.to.interpolated"
    val WrapSingleExpressionBody = "wrap.single.expression.body"
    val DeleteClosingBrace = "delete.closing.brace"
    val HandleBlockBracesInsertionAutomatically = "handle.block.braces.insertion.automatically"
    val HandleBlockBracesRemovalAutomatically = "handle.block.braces.removal.automatically"
  }

  private object SettingGroupNames {
    val CompileServerSettings = "compile server settings"
    val SmartKeysSettings = "smart keys settings"
    val ScalaCompilerSettings = "compiler settings for active project"
    val SbtSettingsForActiveProject = "sbt settings for active project"
    val ScalaSettingsForActiveProject = "scala settings for active project"
    val FormatterSettings = "formatter settings for active project"
  }

  //noinspection TypeAnnotation
  private object DefaultSettings {
    val ScalaCompileServerSettings = new org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
    val ScalaCompilerSettings = new org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration(null)
    val SbtProjectSettings = new org.jetbrains.sbt.project.settings.SbtProjectSettings
    val ScalaProjectSettings = new org.jetbrains.plugins.scala.settings.ScalaProjectSettings(null)
    val ScalaCodeStyleSettings = CodeStyleSettings.getDefaults.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val SmartKeysSettings = new org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
  }
}
