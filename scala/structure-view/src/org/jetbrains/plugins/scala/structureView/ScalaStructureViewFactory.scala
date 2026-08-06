package org.jetbrains.plugins.scala.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

class ScalaStructureViewFactory extends PsiStructureViewFactory {
  override def getStructureViewBuilder(file: PsiFile): StructureViewBuilder = file match {
    case scalaFile: ScalaFile =>
      ScalaActionUsagesCollector.logStructureView(file.getProject)
      new ScalaStructureViewBuilder(scalaFile)
    case _ => null
  }
}
