package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.base.SdkFileSetTestBase
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

import java.nio.file.Path

class IntroduceVariableTest extends IntroduceVariableTestBase {
  override protected def relativeTestDataPath: Path = Path.of("refactoring", "introduceVariable", "data")
}

class IntroduceVariableScala3Test extends IntroduceVariableTestBase {
  override protected def relativeTestDataPath: Path = Path.of("refactoring", "introduceVariable", "data3")

  override protected def language: Language = Scala3Language.INSTANCE
}

abstract class IntroduceVariableTestBase extends SdkFileSetTestBase with ActionTestBase {
  private var fixture: ScalaIntroduceVariableTestFixture = _

  override protected def setUp(): Unit = {
    super.setUp()

    val alwaysAddTypeSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(project))
    fixture = new ScalaIntroduceVariableTestFixture(project, Some(alwaysAddTypeSettings), language)
    fixture.setUp()
  }

  override protected def tearDown(): Unit = {
    fixture.tearDown()
    super.tearDown()
  }

  override protected def transform(testName: String, fileText: String): String = {
    val (extractedText, options) = IntroduceVariableUtils.extractNameFromLeadingComment(fileText)
    val optionsAdjusted = options.copy(definitionName = options.definitionName.orElse(Some("value")))
    fixture.configureFromText(extractedText)
    fixture.invokeIntroduceVariableActionAndGetResult(optionsAdjusted) match {
      case Left(error) => error
      case Right(fileTextNew) => fileTextNew
    }
  }
}
