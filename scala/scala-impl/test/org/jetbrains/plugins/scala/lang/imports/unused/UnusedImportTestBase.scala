package org.jetbrains.plugins.scala.lang.imports.unused

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

import scala.collection.JavaConverters._

/**
  * Created by Svyatoslav Ilinskiy on 24.07.16.
  */
abstract class UnusedImportTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  def messages(text: String,
               fileType: LanguageFileType = ScalaFileType.INSTANCE): Seq[HighlightMessage] = {
    myFixture.configureByText(fileType, text)
    val infos = myFixture.doHighlighting().asScala
      .toList
      .filterNot(_.getDescription == null)
      .map(HighlightMessage.apply)
    infos
  }
}

case class HighlightMessage(element: String, description: String)

object HighlightMessage {
  def apply(info: HighlightInfo): HighlightMessage = HighlightMessage(info.getText, info.getDescription)
}
