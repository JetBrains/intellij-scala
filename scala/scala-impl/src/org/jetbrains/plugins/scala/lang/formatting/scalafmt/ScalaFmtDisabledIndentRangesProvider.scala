package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import java.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.DisabledIndentRangesProvider
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ScalaFmtDisabledIndentRangesProvider extends DisabledIndentRangesProvider {

  override def getDisabledIndentRanges(element: PsiElement): util.Collection[TextRange] = {
    val project = element.getProject
    //Do not try to fix indents after formatting - ScalaFmtPreformatProcessor delegates all the work to scalafmt
    import scala.jdk.CollectionConverters._
    if (ScalaCodeStyleSettings.getInstance(project).USE_SCALAFMT_FORMATTER()) Seq(element.getTextRange).asJava
    else Seq().asJava
  }
}
