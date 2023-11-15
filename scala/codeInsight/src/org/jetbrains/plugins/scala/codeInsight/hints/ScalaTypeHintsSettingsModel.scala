package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.codeInsight.hints.settings.{InlayProviderSettingsModel, InlaySettingsConfigurable, InlaySettingsConfigurableKt}
import com.intellij.ide.DataManager
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaTypeHintsSettingsModel.Case
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHints, ImplicitHintsPass}
import org.jetbrains.plugins.scala.extensions.{NullSafe, ObjectExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.util
import javax.swing.JComponent
import scala.jdk.CollectionConverters.SeqHasAsJava

class ScalaTypeHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(
  true,
  "Scala.ScalaTypeHintsSettingsModel",
  ScalaLanguage.INSTANCE
)  {
  private lazy val insightSettings = ScalaCodeInsightSettings.getInstance()

  override def getName: String = "Type Hints"
  override def getGroup: InlayGroup = InlayGroup.TYPES_GROUP

  private lazy val generalSettingsPanel = new GeneralSettingsPanel(
    () => insightSettings.showObviousType, insightSettings.showObviousType = _,
    () => insightSettings.preserveIndents, insightSettings.preserveIndents = _,
    () => insightSettings.presentationLength, insightSettings.presentationLength = _
  )

  override def getComponent: JComponent = generalSettingsPanel.getPanel

  override def getDescription: String = ScalaCodeInsightBundle.message("type.hints.description", ScalaCodeInsightBundle.message("xray.mode.tip", ScalaHintsSettings.xRayModeShortcut))

  override def getMainCheckBoxLabel: String = getName

  override def getPreviewText: String = {
    if (project.isDefault)
      return null

    """
      |class Person {
      |  val birthYear = 5 + 5
      |
      |  def ageInYear(year: Int) = {
      |    val diff = year - birthYear
      |    math.max(0, diff)
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator.trim
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    val settings = new ScalaHintsSettings.Defaults {
      override def showMethodResultType: Boolean = ShowMethodResultTypeCase.currentValue
      override def showMemberVariableType: Boolean = ShowMemberVariableTypeCase.currentValue
      override def showLocalVariableType: Boolean = ShowLocalVariableTypeCase.currentValue
      override def showObviousType: Boolean = generalSettingsPanel.showObviousTypes
      override def preserveIndents: Boolean = generalSettingsPanel.getPreserveIntends
      override def presentationLength: Int = generalSettingsPanel.getPresentationLength
    }
    val previewPass = new ImplicitHintsPass(editor, psiFile.asInstanceOf[ScalaFile], settings, isPreviewPass = true)
    previewPass.doCollectInformation(DumbProgressIndicator.INSTANCE)
    previewPass.doApplyInformationToEditor()
  }


  override def isModified: Boolean =
    generalSettingsPanel.isModified ||
      cases.values.exists(_.isModified)

  override def apply(): Unit = {
    generalSettingsPanel.saveSettings()
    cases.values.foreach(_.save())
    ImplicitHints.updateInAllEditors()
  }

  override def reset(): Unit = {
    generalSettingsPanel.reset()
    cases.values.foreach(_.reset())
  }


  override def getCases: util.List[ImmediateConfigurable.Case] =
    cases.keysIterator.toSeq.asJava

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String =
    cases.get(aCase).map(_.description).orNull

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String =
    cases.get(aCase).map(_.preview).orNull

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language =
    ScalaLanguage.INSTANCE

  private val cases =
    Seq(ShowMemberVariableTypeCase, ShowLocalVariableTypeCase, ShowMethodResultTypeCase)
      .map(c => c.configCase -> c)
      .toMap

  private lazy val ShowMethodResultTypeCase = new Case {
    override def name: String = ScalaCodeInsightBundle.message("method.results")
    override def id: String = "Scala.ScalaTypeHintsSettingsModel.showMethodResultType"
    override def loadSetting(): Boolean = insightSettings.showFunctionReturnType
    override def saveSetting(value: Boolean): Unit = insightSettings.showFunctionReturnType = value
    override def description: String = ScalaCodeInsightBundle.message("method.results.description", ScalaCodeInsightBundle.message("xray.mode.tip", ScalaHintsSettings.xRayModeShortcut))
    override def preview: String = getPreviewText
  }

  private lazy val ShowLocalVariableTypeCase = new Case {
    override def name: String = ScalaCodeInsightBundle.message("local.variables")
    override def id: String = "Scala.ScalaTypeHintsSettingsModel.showLocalVariableType"
    override def loadSetting(): Boolean = insightSettings.showLocalVariableType
    override def saveSetting(value: Boolean): Unit = insightSettings.showLocalVariableType = value
    override def description: String = ScalaCodeInsightBundle.message("local.variables.description", ScalaCodeInsightBundle.message("xray.mode.tip", ScalaHintsSettings.xRayModeShortcut))
    override def preview: String = getPreviewText
  }

  private lazy val ShowMemberVariableTypeCase = new Case {
    override def name: String = ScalaCodeInsightBundle.message("member.variables")
    override def id: String = "Scala.ScalaTypeHintsSettingsModel.showMemberVariableType"
    override def loadSetting(): Boolean = insightSettings.showPropertyType
    override def saveSetting(value: Boolean): Unit = insightSettings.showPropertyType = value
    override def description: String = ScalaCodeInsightBundle.message("member.variables.description", ScalaCodeInsightBundle.message("xray.mode.tip", ScalaHintsSettings.xRayModeShortcut))
    override def preview: String = getPreviewText
  }
}

object ScalaTypeHintsSettingsModel {
  abstract class Case {
    @Nls
    def name: String
    def id: String
    @Nls
    def description: String
    def preview: String = null

    protected def loadSetting(): Boolean
    protected def saveSetting(value: Boolean): Unit

    def isModified: Boolean = currentValue != loadSetting()
    def save(): Unit = saveSetting(currentValue)
    def reset(): Unit = currentValue = loadSetting()

    var currentValue: Boolean = loadSetting()

    final lazy val configCase: ImmediateConfigurable.Case = new ImmediateConfigurable.Case(
      name, id,
      () => currentValue,
      (newValue) => {
        currentValue = newValue
        kotlin.Unit.INSTANCE
      },
      description
    )
  }

  def navigateTo(project: Project): Unit = navigateToInlaySettings[ScalaTypeHintsSettingsModel](project)
}