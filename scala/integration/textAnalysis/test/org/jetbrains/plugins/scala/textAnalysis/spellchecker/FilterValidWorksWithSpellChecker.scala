package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.testFramework.LightPlatformTestCase

import scala.annotation.nowarn

// We use LightPlatformTestCase, because it already knows how to create test project, create all services.
// Project is required to create spellchecker instance.
@nowarn("cat=deprecation") // LightPlatformTestCase is deprecated in favor of JUnit 5 (IJPL-233558)
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
