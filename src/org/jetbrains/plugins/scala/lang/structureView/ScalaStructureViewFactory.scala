package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import com.intellij.ide.structureView.StructureViewBuilder
import psi.api.ScalaFile
import console.{ScalaConsoleInfo, ScalaLanguageConsoleView}

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaStructureViewFactory extends PsiStructureViewFactory {
  def getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = psiFile match {
    case sf: ScalaFile => {
      if (sf.getName == ScalaLanguageConsoleView.SCALA_CONSOLE) {
        val console = ScalaConsoleInfo.getConsole(sf.getProject)
        new ScalaStructureViewBuilder(sf, console)
      } else {
        new ScalaStructureViewBuilder(sf)
      }
    }
    case _ => null
  }
}