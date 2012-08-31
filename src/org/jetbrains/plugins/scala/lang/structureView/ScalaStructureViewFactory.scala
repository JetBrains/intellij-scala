package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import com.intellij.ide.structureView.StructureViewBuilder
import psi.api.ScalaFile
import console.{ScalaConsoleInfo, ScalaLanguageConsoleView}
import psi.impl.ScalaPsiElementFactory

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaStructureViewFactory extends PsiStructureViewFactory {
  def getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = psiFile match {
    case sf: ScalaFile => {
      if (psiFile.getName == ScalaLanguageConsoleView.SCALA_CONSOLE) {
        val buffer = new StringBuilder
        val console = ScalaConsoleInfo.getConsole
        if (console != null) buffer.append(console.getHistory)
        buffer.append(sf.getText)
        val newFile = ScalaPsiElementFactory.createScalaFile(buffer.toString(), psiFile.getManager)
        new ScalaStructureViewBuilder(newFile)
      } else {
        new ScalaStructureViewBuilder(sf)
      }
    }
    case _ => null
  }
}