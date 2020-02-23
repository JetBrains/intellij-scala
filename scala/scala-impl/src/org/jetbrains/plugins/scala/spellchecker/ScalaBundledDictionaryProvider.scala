package org.jetbrains.plugins.scala.spellchecker

import com.intellij.spellchecker.BundledDictionaryProvider

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.12
 */

class ScalaBundledDictionaryProvider extends BundledDictionaryProvider {
  override def getBundledDictionaries: Array[String] = {
    Array(
      "scala.dic"
    )
  }
}