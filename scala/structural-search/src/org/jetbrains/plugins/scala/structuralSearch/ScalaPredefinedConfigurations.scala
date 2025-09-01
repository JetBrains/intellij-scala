package org.jetbrains.plugins.scala.structuralSearchimport

import com.intellij.structuralsearch.plugin.ui.Configuration
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.structuralsearch.PredefinedConfigurationUtil.createConfiguration

object ScalaPredefinedConfigurations {
  def createPredefinedTemplated(): Array[Configuration] = {
    Seq(
      createConfiguration("Simple", "another simple",
        "'_Instance?.'MethodCall('_Parameter*)",
        getExpressionType, ScalaFileType.INSTANCE)
    ).toArray
  }

  val getExpressionType = "Scala/Expressions"
}
