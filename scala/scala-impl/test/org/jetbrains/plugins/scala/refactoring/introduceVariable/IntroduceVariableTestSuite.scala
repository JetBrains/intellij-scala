package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

final class IntroduceVariableTestSuite(path: String) extends ActionTestBase(path) {
  override protected def needsSdk: Boolean = true

  private var myFixture: ScalaIntroduceVariableTestFixture = _

  override protected def setUp(project: Project): Unit = {
    super.setUp(project)

    val alwaysAddTypeSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(project))
    myFixture = new ScalaIntroduceVariableTestFixture(project, Some(alwaysAddTypeSettings))
    myFixture.setUp()
  }

  override protected def tearDown(project: Project): Unit = {
    myFixture.tearDown()
    super.tearDown(project)
  }

  override protected def transform(testName: String, testFileText: String, project: Project): String = {
    val (fileText, options) = IntroduceVariableUtils.extractNameFromLeadingComment(testFileText)
    val optionsAdjusted = options.copy(definitionName = options.definitionName.orElse(Some("value")))
    myFixture.configureFromText(fileText)
    myFixture.invokeIntroduceVariableActionAndGetResult(optionsAdjusted) match {
      case Left(error) => error
      case Right(fileTextNew) => fileTextNew
    }
  }
}