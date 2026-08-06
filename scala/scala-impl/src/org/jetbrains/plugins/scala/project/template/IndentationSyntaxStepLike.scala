package org.jetbrains.plugins.scala.project.template

import com.intellij.application.options.CodeStyle
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.{AtomicProperty, GraphProperty}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.dsl.builder.ButtonKt.bindSelected
import com.intellij.ui.dsl.builder.{AlignX, Panel, Row}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

trait IndentationSyntaxStepLike { self: NewProjectWizardStep =>

  private val showIndentationSyntaxCheckBox: AtomicProperty[java.lang.Boolean] =
    new AtomicProperty(java.lang.Boolean.FALSE)

  final protected def setShowIndentationSyntaxCheckBox(show: Boolean): Unit = {
    showIndentationSyntaxCheckBox.set(show)
  }

  private val useIndentationBasedSyntaxProperty: GraphProperty[java.lang.Boolean] = {
    val prop = getPropertyGraph.property(java.lang.Boolean.FALSE)
    BindUtil.bindBooleanStorage(prop, IndentationSyntaxStepLike.IndentationSyntaxPropertyName)
  }

  @TestOnly
  final protected[project] def setUseIndentationBasedSyntaxProperty(use: Boolean): Unit = {
    useIndentationBasedSyntaxProperty.set(use)
  }

  final protected def setupIndentationSyntaxUI(panel: Panel): Unit = {
    // Passing an empty string is a trick to indent the checkbox under the setting above.
    //noinspection ScalaExtractStringToBundle
    panel.row("", (row: Row) => {
      val cell = row
        .checkBox(ScalaBundle.message("use.indentation.based.syntax.checkbox.label"))
        .align(AlignX.LEFT.INSTANCE)
        .visibleIf(showIndentationSyntaxCheckBox)
      bindSelected(cell, useIndentationBasedSyntaxProperty)
      kotlin.Unit.INSTANCE
    })
  }

  final protected def setupUseIndentationBasedSyntaxInProject(project: Project): Unit = {
    val manager = CodeStyleSettingsManager.getInstance(project)
    var mainProjectCodeStyle = manager.getMainProjectCodeStyle
    if (mainProjectCodeStyle eq null) {
      val settings = CodeStyle.getSettings(project)
      mainProjectCodeStyle = manager.cloneSettings(settings)
    }
    val scalaCodeStyleSettings = mainProjectCodeStyle.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = useIndentationSyntaxInProject
    CodeStyle.setMainProjectSettings(project, mainProjectCodeStyle)
  }

  private def useIndentationSyntaxInProject: Boolean =
    showIndentationSyntaxCheckBox.get() && useIndentationBasedSyntaxProperty.get()
}

object IndentationSyntaxStepLike {
  final val IndentationSyntaxPropertyName = "scala.IndentationSyntaxStepLike.useIndentationSyntax"
}
