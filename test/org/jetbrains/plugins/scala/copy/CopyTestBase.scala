package org.jetbrains.plugins.scala.copy

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
  * Created by Kate Ustyuzhanina on 12/28/16.
  */
abstract class CopyTestBase(fromLang: Lang, toLang: Lang) extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaLightCodeInsightFixtureTestAdapter._

  var oldSettings: ScalaCodeStyleSettings = _

  override protected def setUp(): Unit = {
    super.setUp()

    oldSettings = ScalaCodeStyleSettings.getInstance(getProject).clone().asInstanceOf[ScalaCodeStyleSettings]
    TypeAnnotationSettings.set(getProject, TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)))
  }

  def doTest(fromText: String, toText: String, expectedText: String): Unit = {
    myFixture.configureByText("from" + langToName(fromLang), normalize(fromText))
    myFixture.performEditorAction(IdeActions.ACTION_COPY)
    myFixture.configureByText("to" + langToName(toLang), normalize(toText))
    myFixture.performEditorAction(IdeActions.ACTION_PASTE)
    myFixture.checkResult(expectedText)
  }

  def doTestEmptyToFile(fromText: String, expectedText: String): Unit ={
    doTest(fromText, emptyFileText, expectedText)
  }

  override def tearDown(): Unit = {
    TypeAnnotationSettings.set(getProject, oldSettings)

    super.tearDown()
  }

  val emptyFileText = "<caret>"

  private def langToName(lang: Lang): String = {
    lang match {
      case Scala() => "Dummy.scala"
      case Java() => "Dummy.java"
      case Text() => "Dummy.txt"
    }
  }
}

sealed abstract class Lang
case class Scala() extends Lang
case class Java() extends Lang
case class Text() extends Lang