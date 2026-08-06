package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage.{INSTANCE => JavaLanguage}

import java.util
import java.util.Collections

/** This class is used directly rather than via plugin.xml */
private object ScalaInlayParameterHintsProvider extends InlayParameterHintsProvider {
  override def getDefaultBlackList: util.Set[String] = Collections.singleton("scala.*")

  override def getBlackListDependencyLanguage: Language = JavaLanguage
}
