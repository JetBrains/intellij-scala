package org.jetbrains.plugins.scala.spellchecker

import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.testFramework.LightPlatformTestCase

// We use LightPlatformTestCase, becasue it already knows how to create test project, create all services.
// Project is required to create spellchecker instance.
class FilterValidWorksWithSpellChecker extends LightPlatformTestCase {

  def testMain(): Unit = {
    val spellCheckerManager = SpellCheckerManager.getInstance(getProject)
    val checker = spellCheckerManager.getSpellChecker

    WordsConcatenated.linesIterator
      .filterNot(checker.isCorrect)
      .foreach(System.err.println)
  }

  // 1. PASTE CONCATENATED WORDS HERE
  // 2. COPY STD OUT TO `*.dic` files
  private val WordsConcatenated: String =
    """""".stripMargin
}
