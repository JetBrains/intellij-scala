package org.jetbrains.plugins.scala.codeInsight
package hints

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, XRayUtils}

trait ScalaHintsSettings {
  def showParameters: Boolean
  def showMethodResultType: Boolean
  def showMemberVariableType: Boolean
  def showLocalVariableType: Boolean
  def showMethodChainInlayHints: Boolean
  def alignMethodChainInlayHints: Boolean
  def uniqueTypesToShowMethodChains: Int
  def presentationLength: Int
  def showObviousType: Boolean
  def preserveIndents: Boolean
  def showRangeHintsForToAndUntil: Boolean
  def showExclusiveRangeHint: Boolean
}

object ScalaHintsSettings {
  var xRayMode = false

  var xRayModePinned = false

  /**
   * Returns html with a gray tip text advertising usage of X-Ray mode.
   * It's shown, for example, in `Settings | Editor | Inlay Hints | Types | Scala`
   * A paragraph is added in order the tip is shown on a new line, below the main setting title.
   */
  @Nls //noinspection ScalaExtractStringToBundle
  private[hints] def xRayModeTipHtmlForSettingsDescription: String = {
    val innerText = xRayModeTipText
    s"""<p style="font-size: 90%; margin-top: 5px; color: gray;">$innerText</p>"""
  }

  @Nls
  private[hints] def xRayModeTipText: String =
    ScalaCodeInsightBundle.message("xray.mode.tip", xRayModeShortcut)

  @Nls def xRayModeShortcut: String = {
    val keyText = XRayUtils.xRayActionKeyText
    if (ScalaApplicationSettings.getInstance.XRAY_DOUBLE_PRESS_AND_HOLD)
      ScalaBundle.message("scala.project.settings.form.xray.double.press.and.hold", keyText)
    else if (ScalaApplicationSettings.getInstance.XRAY_PRESS_AND_HOLD)
      ScalaBundle.message("scala.project.settings.form.xray.press.and.hold", keyText)
    else
      ScalaCodeInsightBundle.message("enable.xray.settings.path")
  }

  class Defaults extends ScalaHintsSettings {
    override def showParameters: Boolean = ScalaCodeInsightSettings.SHOW_PARAMETER_NAMES_DEFAULT
    override def showMethodResultType: Boolean = ScalaCodeInsightSettings.SHOW_METHOD_RESULT_TYPE_DEFAULT
    override def showMemberVariableType: Boolean = ScalaCodeInsightSettings.SHOW_MEMBER_VARIABLE_TYPE_DEFAULT
    override def showLocalVariableType: Boolean = ScalaCodeInsightSettings.SHOW_LOCAL_VARIABLE_TYPE_DEFAULT
    override def showMethodChainInlayHints: Boolean = ScalaCodeInsightSettings.SHOW_METHOD_CHAIN_INLAY_HINTS_DEFAULT
    override def alignMethodChainInlayHints: Boolean = ScalaCodeInsightSettings.ALIGN_METHOD_CHAIN_INLAY_HINTS_DEFAULT
    override def uniqueTypesToShowMethodChains: Int = ScalaCodeInsightSettings.UNIQUE_TYPES_TO_SHOW_METHOD_CHAINS_DEFAULT
    override def presentationLength: Int = ScalaCodeInsightSettings.PRESENTATION_LENGTH_DEFAULT
    override def showObviousType: Boolean = ScalaCodeInsightSettings.SHOW_OBVIOUS_TYPE_DEFAULT
    override def preserveIndents: Boolean = ScalaCodeInsightSettings.PRESERVE_INDENTS_DEFAULT
    override def showRangeHintsForToAndUntil: Boolean = ScalaCodeInsightSettings.SHOW_RANGE_HINTS_FOR_TO_AND_UNTIL_DEFAULT
    override def showExclusiveRangeHint: Boolean = ScalaCodeInsightSettings.SHOW_EXCLUSIVE_RANGE_HINT_DEFAULT
  }

  class CodeInsightSettingsAdapter extends ScalaHintsSettings {
    private val settings = ScalaCodeInsightSettings.getInstance()
    private val applicationSettings = ScalaApplicationSettings.getInstance

    override def showParameters: Boolean = (xRayMode && applicationSettings.XRAY_SHOW_PARAMETER_HINTS) || settings.showParameterNames
    override def showMethodResultType: Boolean = (xRayMode && applicationSettings.XRAY_SHOW_TYPE_HINTS && applicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS) || settings.showFunctionReturnType
    override def showMemberVariableType: Boolean = (xRayMode && applicationSettings.XRAY_SHOW_TYPE_HINTS && applicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS) || settings.showPropertyType
    override def showLocalVariableType: Boolean = (xRayMode && applicationSettings.XRAY_SHOW_TYPE_HINTS && applicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS) || settings.showLocalVariableType
    override def showMethodChainInlayHints: Boolean = (xRayMode && applicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS) || settings.showMethodChainInlayHints
    override def alignMethodChainInlayHints: Boolean = settings.alignMethodChainInlayHints
    override def uniqueTypesToShowMethodChains: Int = if (xRayMode) 1 else settings.uniqueTypesToShowMethodChains
    override def presentationLength: Int = settings.presentationLength
    override def showObviousType: Boolean = xRayMode || settings.showObviousType
    override def preserveIndents: Boolean = settings.preserveIndents
    override def showRangeHintsForToAndUntil: Boolean = xRayMode || settings.showRangeHintsForToAndUntil
    override def showExclusiveRangeHint: Boolean = xRayMode || settings.showExclusiveRangeHint
  }
}
