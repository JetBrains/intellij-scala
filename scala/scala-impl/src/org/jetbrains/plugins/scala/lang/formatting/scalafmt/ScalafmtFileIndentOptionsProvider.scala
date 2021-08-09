package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings, FileIndentOptionsProvider}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity.Silent
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * Required in order "Adjust indent" action uses correct indent sizes (from scalafmt config).<br>
 * Otherwise IntelliJ settings indent will be used.<br>
 *
 * @see [[com.intellij.psi.codeStyle.CodeStyleSettings.getIndentOptionsByFile]]
 */
final class ScalafmtFileIndentOptionsProvider extends FileIndentOptionsProvider {

  override def getIndentOptions(settings: CodeStyleSettings, file: PsiFile): CommonCodeStyleSettings.IndentOptions = {
    if (!file.isInstanceOf[ScalaFile])
      return null

    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (!scalaSettings.USE_SCALAFMT_FORMATTER())
      return null

    val configOpt = ScalafmtDynamicConfigService(file.getProject).configForFile(file, Silent, resolveFast = true)
    configOpt.map { config =>
      val indents = config.indents
      val options = new CommonCodeStyleSettings.IndentOptions()
      options.INDENT_SIZE = indents.main
      options.CONTINUATION_INDENT_SIZE = indents.main
      options
    }.orNull
  }
}
