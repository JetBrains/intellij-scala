package org.jetbrains.plugins.scala.codeInsight.hints

import java.{util => ju}
import com.intellij.codeInsight.hints.settings.{InlayProviderSettingsModel, InlaySettingsProvider}
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.ScalaMethodChainInlayHintsSettingsModel
import org.jetbrains.plugins.scala.codeInsight.hints.rangeHints.{ExclusiveRangeHintSettingsModel, RangeHintsForToAndUntilSettingsModel}

import scala.collection.immutable.ArraySeq

class ScalaInlayHintsSettingsProvider extends InlaySettingsProvider {
  override def createModels(project: Project, language: Language): ju.List[InlayProviderSettingsModel] =
    if (language == ScalaLanguage.INSTANCE) {
      ju.Arrays.asList(
        new ScalaParameterHintsSettingsModel(project),
        new ScalaTypeHintsSettingsModel(project),
        new ScalaMethodChainInlayHintsSettingsModel(project),
        new RangeHintsForToAndUntilSettingsModel(project),
        new ExclusiveRangeHintSettingsModel(project),
      )
    }
    else ju.Collections.emptyList()

  ArraySeq.newBuilder

  override def getSupportedLanguages(project: Project): ju.Collection[Language] =
    ju.Collections.singletonList(ScalaLanguage.INSTANCE)
}
