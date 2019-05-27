package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.{TemplateImpl, TemplateManagerImpl, TemplateSettings}
import com.intellij.testFramework.{EditorTestUtil, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.Assert._

abstract class ScalaLiveTemplateTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  protected def templateName: String

  protected def templateGroup: String = "scala"

  override protected def setUp(): Unit = {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable)
  }

  protected def doTest(@Language("Scala") before: String,
                       @Language("Scala") after: String): Unit = {
    doTest(before, after, templateName, templateGroup)
  }

  protected def doTest(@Language("Scala") before: String,
                       @Language("Scala") after: String,
                       templateName: String): Unit = {
    doTest(before, after, templateName, templateGroup)
  }

  protected def doTest(@Language("Scala") before: String,
                       @Language("Scala") after: String,
                       templateName: String,
                       templateGroup: String): Unit = {
    myFixture.configureByText(ScalaFileType.INSTANCE, before)

    val template: TemplateImpl = TemplateSettings.getInstance.getTemplate(templateName, templateGroup)
    val templateId = s"`$templateGroup/$templateName`"
    assertNotNull(s"template $templateId not found", template)
    assertTrue(s"template $templateId should be applicable", isApplicable(template))

    val templateManager = TemplateManager.getInstance(getProject)
    templateManager.startTemplate(getEditor, template)
    // TODO: for now caret position in the end of template editing is not tested properly
    //  Now caret is set to the end of the FIRST template variable
    //  We should find the way to apply default variable values/expressions in tests
    //  and test that caret is set to the end of the LAST template variable
    templateManager.finishTemplate(getEditor)

    UIUtil.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    myFixture.checkResult(after.replaceAll("\r", ""))
  }

  private def isApplicable(template: TemplateImpl): Boolean = {
    TemplateManagerImpl.isApplicable(myFixture.getFile, getEditor.getCaretModel.getOffset, template)
  }

}
