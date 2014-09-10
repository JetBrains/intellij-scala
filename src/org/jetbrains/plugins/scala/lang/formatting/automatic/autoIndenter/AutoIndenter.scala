package org.jetbrains.plugins.scala.lang.formatting.automatic.autoIndenter

import org.jetbrains.plugins.scala.lang.formatting.automatic.ScalaAutoFormatter
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.ScalaFormattingRuleMatcher
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.FormattingSettings
import com.intellij.openapi.vfs.VirtualFile
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.openapi.project.Project

/**
 * Created by Roman.Shein on 19.08.2014.
 */
object AutoIndenter {
  private val formatter: ScalaAutoFormatter = new ScalaAutoFormatter(ScalaFormattingRuleMatcher.createDefaultMatcher())

  private val fileToSettingsMap = mutable.Map[VirtualFile, FormattingSettings]()

  def getNormalIndentSize(file: VirtualFile): Option[Int] =
    fileToSettingsMap.get(file).map(_.normalIndentSize).flatten

  def getContinuationIndentSize(file: VirtualFile): Option[Int] =
    fileToSettingsMap.get(file).map(_.continuationIndentSize).flatten

  def prepareAutoIndenter(block: ScalaBlock, project: Project, file: VirtualFile) {
    formatter.runMatcher(block)
    formatter.educateAutoIndent(project)
    if (formatter.getSettings != null) {
      fileToSettingsMap.put(file, formatter.getSettings)
    }
    formatter.resetMatcher
  }
}
 
