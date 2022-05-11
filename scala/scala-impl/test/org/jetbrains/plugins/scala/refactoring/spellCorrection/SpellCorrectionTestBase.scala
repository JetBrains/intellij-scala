package org.jetbrains.plugins.scala.refactoring.spellCorrection

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupManager}
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.spellchecker.quickfixes.RenameTo
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

abstract class SpellCorrectionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  val NAME = "/*NAME*/"

  override def setUp(): Unit = {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable)
  }

  def doTest(context: String, originalWord: String, fileExt: String = "scala")(expectedWords: String*): Unit = {

    val original = context.replace(NAME, originalWord.head.toString + CARET + originalWord.tail)
    val expected = context.replace(NAME, expectedWords.head)

    myFixture.configureByText("dummy." + fileExt, original)
    myFixture.enableInspections(classOf[SpellCheckingInspection])
    val fix = myFixture.findSingleIntention(RenameTo.getFixName);
    myFixture.launchAction(fix)
    selectAndCheckLookupElements(expectedWords)
    myFixture.checkResult(expected)
  }

  private def selectAndCheckLookupElements(expectedWords: Seq[String]): Unit = {
    val elements = myFixture.getLookupElements
    assert(elements != null)

    val lookup = LookupManager.getInstance(getProject).getActiveLookup
    assert(lookup != null)

    expectedWords.foreach{ expectedWord =>
      assert(elements.exists(_.getLookupString == expectedWord), s"Expected '$expectedWord' but only found: ${elements.map(_.getLookupString).mkString(", ")}")
    }

    lookup.setCurrentItem(elements.find(_.getLookupString == expectedWords.head).get)

    lookup.asInstanceOf[LookupImpl].finishLookup(Lookup.NORMAL_SELECT_CHAR)
  }
}
