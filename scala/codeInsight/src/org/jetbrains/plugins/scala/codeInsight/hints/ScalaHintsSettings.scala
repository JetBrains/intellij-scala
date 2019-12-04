package org.jetbrains.plugins.scala.codeInsight
package hints

trait ScalaHintsSettings {
  def showMethodResultType: Boolean
  def showMemberVariableType: Boolean
  def showLocalVariableType: Boolean
  def showMethodChainInlayHints: Boolean
  def alignMethodChainInlayHints: Boolean
  def uniqueTypesToShowMethodChains: Int
  def presentationLength: Int
  def showObviousType: Boolean
  def preserveIndents: Boolean
}

object ScalaHintsSettings {
  class Defaults extends ScalaHintsSettings {
    override def showMethodResultType: Boolean = ScalaCodeInsightSettings.SHOW_METHOD_RESULT_TYPE_DEFAULT
    override def showMemberVariableType: Boolean = ScalaCodeInsightSettings.SHOW_MEMBER_VARIABLE_TYPE_DEFAULT
    override def showLocalVariableType: Boolean = ScalaCodeInsightSettings.SHOW_LOCAL_VARIABLE_TYPE_DEFAULT
    override def showMethodChainInlayHints: Boolean = ScalaCodeInsightSettings.SHOW_METHOD_CHAIN_INLAY_HINTS_DEFAULT
    override def alignMethodChainInlayHints: Boolean = ScalaCodeInsightSettings.ALIGN_METHOD_CHAIN_INLAY_HINTS_DEFAULT
    override def uniqueTypesToShowMethodChains: Int = ScalaCodeInsightSettings.UNIQUE_TYPES_TO_SHOW_METHOD_CHAINS_DEFAULT
    override def presentationLength: Int = ScalaCodeInsightSettings.PRESENTATION_LENGTH_DEFAULT
    override def showObviousType: Boolean = ScalaCodeInsightSettings.SHOW_OBVIOUS_TYPE_DEFAULT
    override def preserveIndents: Boolean = ScalaCodeInsightSettings.PRESERVE_INDENTS_DEFAULT
  }

  class CodeInsightSettingsAdapter extends ScalaHintsSettings {
    private val settings = ScalaCodeInsightSettings.getInstance()

    override def showMethodResultType: Boolean = settings.showTypeHints && settings.showFunctionReturnType
    override def showMemberVariableType: Boolean = settings.showTypeHints && settings.showPropertyType
    override def showLocalVariableType: Boolean = settings.showTypeHints && settings.showLocalVariableType
    override def showMethodChainInlayHints: Boolean = settings.showMethodChainInlayHints
    override def alignMethodChainInlayHints: Boolean = settings.alignMethodChainInlayHints
    override def uniqueTypesToShowMethodChains: Int = settings.uniqueTypesToShowMethodChains
    override def presentationLength: Int = settings.presentationLength
    override def showObviousType: Boolean = settings.showObviousType
    override def preserveIndents: Boolean = settings.preserveIndents
  }
}
