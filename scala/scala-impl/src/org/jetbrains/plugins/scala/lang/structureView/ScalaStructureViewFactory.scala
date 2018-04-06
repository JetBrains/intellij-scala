package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.console.{ScalaConsoleInfo, ScalaLanguageConsoleView}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
class ScalaStructureViewFactory extends PsiStructureViewFactory {
  override def getStructureViewBuilder(file: PsiFile): StructureViewBuilder = file match {
    case scalaFile: ScalaFile =>
      Stats.trigger(FeatureKey.structureView)

      if (scalaFile.getName == ScalaLanguageConsoleView.SCALA_CONSOLE) {
        new ScalaStructureViewBuilder(scalaFile, Some(ScalaConsoleInfo.getConsole(scalaFile)))
      } else {
        new ScalaStructureViewBuilder(scalaFile)
      }
    case _ => null
  }
}