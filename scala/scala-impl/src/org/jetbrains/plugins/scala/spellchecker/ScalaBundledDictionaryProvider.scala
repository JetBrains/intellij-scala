package org.jetbrains.plugins.scala.spellchecker

import com.intellij.spellchecker.BundledDictionaryProvider

class ScalaBundledDictionaryProvider extends BundledDictionaryProvider {
  override def getBundledDictionaries: Array[String] = {
    Array(
      "scala.dic",
      "awesome-scala-lib-name-parts.dic",
      "awesome-scala-org-name-parts.dic",
    )
  }
}