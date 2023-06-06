package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.impl.{TemplateImpl, TemplateManagerImpl, TemplateSettings}
import com.intellij.codeInsight.template.{TemplateActionContext, TemplateManager}
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert._

import scala.jdk.CollectionConverters._

abstract class ScalaLiveTemplateTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override def setUp(): Unit = {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable)
  }

  protected def templateName: String

  protected def templateGroup: String = "scala"

  final protected def templateId = s"`$templateGroup/$templateName`"

  protected def fileExtension: String = "scala"

  protected def doTest(@Language("Scala") before: String,
                       @Language("Scala") after: String): Unit =
    doTest(before, after, Map(), templateName, templateGroup, fileExtension)

  protected def doTest(@Language("Scala") before: String,
                       @Language("Scala") after: String,
                       predefinedVarValues: Map[String, String]): Unit =
    doTest(before, after, predefinedVarValues, templateName, templateGroup, fileExtension)

  protected def doTest(@Language("Scala") before: String,
                       @Language("Scala") after: String,
                       predefinedVarValues: Map[String, String],
                       templateName: String,
                       templateGroup: String,
                       fileExtension: String): Unit = {
    myFixture.configureByText(s"a.$fileExtension", before)

    val template = findTemplate(templateName, templateGroup)
    assertIsApplicable(template, Some(before))

    val templateManager = TemplateManager.getInstance(getProject)

    val varValuesJava = Option(predefinedVarValues).filter(_.nonEmpty).map(_.asJava).orNull
    templateManager.startTemplate(getEditor, template, true, varValuesJava, null)
    templateManager.finishTemplate(getEditor)

    // TODO: for now caret position in the end of template editing is not tested properly
    //  Now caret is set to the end of the FIRST template variable
    //  We should find the way to apply default variable values/expressions in tests
    //  and test that caret is set to the end of the LAST template variable

    UIUtil.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    myFixture.checkResult(after.replaceAll("\r", ""))
  }

  protected def findTemplate(templateName: String, templateGroup: String): TemplateImpl = {
    val template = TemplateSettings.getInstance.getTemplate(templateName, templateGroup)
    assertNotNull(s"template $templateId not found", template)
    template
  }

  private def isApplicable(template: TemplateImpl): Boolean = {
    val context = TemplateActionContext.expanding(myFixture.getFile, myFixture.getCaretOffset)
    TemplateManagerImpl.isApplicable(template, context)
  }

  protected def assertIsApplicable(code: String): Unit =
    assertIsApplicable(code, fileExtension)

  protected def assertIsNotApplicable(code: String): Unit =
    assertIsNotApplicable(code, fileExtension)

  protected def assertIsApplicable(code: String, fileExtension: String): Unit = {
    myFixture.configureByText(s"a.$fileExtension", code)
    val template = findTemplate(templateName, templateGroup)
    assertIsApplicable(template)
  }

  protected def assertIsNotApplicable(code: String, fileExtension: String): Unit = {
    myFixture.configureByText(s"a.$fileExtension", code)
    val template = findTemplate(templateName, templateGroup)
    assertIsNotApplicable(template)
  }

  protected def assertIsApplicable(template: TemplateImpl, fileText: Option[String] = None): Unit =
    assertTrue(s"template $templateId should be applicable${fileText.fold("")(s => s" in file:\n$s")}", isApplicable(template))

  protected def assertIsNotApplicable(template: TemplateImpl): Unit =
    assertFalse(s"template $templateId should not be applicable", isApplicable(template))
}
