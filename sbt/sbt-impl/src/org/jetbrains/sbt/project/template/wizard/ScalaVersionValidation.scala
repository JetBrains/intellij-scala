package org.jetbrains.sbt.project.template.wizard

import com.intellij.openapi.ui.ValidationInfo
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.SbtBundle

import javax.swing.JComponent

object ScalaVersionValidation {
  def showScala38Warning(scalaLanguageLevel: ScalaLanguageLevel, comboBoxComponent: JComponent): Option[ValidationInfo] =
    if (scalaLanguageLevel >= ScalaLanguageLevel.Scala_3_8) {
      val validationInfo = new ValidationInfo(
        SbtBundle.message("scala.3.8.selected.npw.validation.warning"),
        comboBoxComponent
      ).asWarning()
      Some(validationInfo)
    } else None
}
