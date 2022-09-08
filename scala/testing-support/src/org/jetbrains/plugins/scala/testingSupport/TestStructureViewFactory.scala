package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.console.{ScalaConsoleInfo, ScalaLanguageConsole}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

class TestStructureViewFactory extends PsiStructureViewFactory {
  override def getStructureViewBuilder(file: PsiFile): StructureViewBuilder = file match {
    case scalaFile: ScalaFile =>
      Stats.trigger(FeatureKey.structureView)

      if (ScalaLanguageConsole.isScalaConsoleFile(scalaFile)) {
        new TestStructureViewBuilder(scalaFile, Some(ScalaConsoleInfo.getConsole(scalaFile)))
      } else {
        new TestStructureViewBuilder(scalaFile)
      }
    case _ => null
  }
}
