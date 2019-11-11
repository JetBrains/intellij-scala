package org.jetbrains.plugins.scala.codeInsight

import java.lang

import com.intellij.openapi.util.{Getter, Setter}

trait InlayHintsSettingsTestHelper {

  class Setting[T] private (getter: Getter[T], setter: Setter[T], value: T) {
    private var saved: Option[T] = None
    private[InlayHintsSettingsTestHelper] def apply(): Unit = {
      assert(saved.isEmpty)
      saved = Some(getter.get())
      setter.set(value)
    }
    private[InlayHintsSettingsTestHelper] def restore(): Unit = setter.set(saved.get)
  }
  object Setting {
    def apply[T](getter: => Getter[T], setter: => Setter[T]): T => Setting[T] =
      value => new Setting(getter, setter, value)
  }

  import ScalaCodeInsightSettings.{getInstance => codeInsightSettings}
  val showMethodChainInlayHintsSetting: lang.Boolean => Setting[lang.Boolean] =
    Setting(codeInsightSettings.showMethodChainInlayHintsGetter(), codeInsightSettings.showMethodChainInlayHintsSetter())
  val showObviousTypeSetting: lang.Boolean => Setting[lang.Boolean] =
    Setting(codeInsightSettings.showObviousTypeGetter(), codeInsightSettings.showObviousTypeSetter())
  val alignMethodChainInlayHints: lang.Boolean => Setting[lang.Boolean] =
    Setting(codeInsightSettings.alignMethodChainInlayHintsGetter(), codeInsightSettings.alignMethodChainInlayHintsSetter())
  val uniqueTypesToShowMethodChains: lang.Integer => Setting[lang.Integer] =
    Setting(codeInsightSettings.uniqueTypesToShowMethodChainsGetter(), codeInsightSettings.uniqueTypesToShowMethodChainsSetter())

  final def withSettings(settings: Seq[Setting[_]])(body: => Unit): Unit = settings match {
    case head +: rest =>
      head.apply()
      try withSettings(rest)(body)
      finally head.restore()
    case _ => body
  }
}

object InlayHintsSettingsTestHelper extends InlayHintsSettingsTestHelper
