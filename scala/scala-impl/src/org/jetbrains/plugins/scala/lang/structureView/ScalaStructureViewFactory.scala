package org.jetbrains.plugins.scala
package lang
package structureView

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
  def getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = psiFile match {
    case sf: ScalaFile =>
      Stats.trigger(FeatureKey.structureView)
      if (sf.getName == ScalaLanguageConsoleView.SCALA_CONSOLE) {
        val console = ScalaConsoleInfo.getConsole(sf)
        new ScalaStructureViewBuilder(sf, console)
      } else {
        new ScalaStructureViewBuilder(sf)
      }
    case _ => null
  }
}