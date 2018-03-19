package org.jetbrains.plugins.scala.util

import com.intellij.application.options.CodeStyleSchemesConfigurable
import com.intellij.application.options.codeStyle.CodeStyleMainPanel
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.{Configurable, ConfigurableGroup, ShowSettingsUtil}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.ui.HyperlinkLabel
import javax.swing.event.{HyperlinkEvent, HyperlinkListener}
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOrRemoveStrategy
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaTabbedCodeStylePanel
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.ReturnTypeLevel.{ADD, BY_CODE_STYLE, REMOVE}
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.{ScalaBundle, extensions}

/**
  * Created by kate on 7/14/16.
  */
// TODO more refactoring needed
object TypeAnnotationUtil {
  // We shouldn't convert _typed pattern_ to _variable pattern_, because typed pattern differs from type annotation:
  // val v: String = new Object() - type mismatch
  // val Some(v: String) = Some(new Object()) - compiles OK
  // Thus, the presence of type affects the outcome, and such a type is not optional.
  private def getTypeElement(element: ScalaPsiElement): Option[ScTypeElement] = element match {
    case function: ScFunction => function.returnTypeElement
    case pattern: ScPatternDefinition => pattern.typeElement
    case variable: ScVariableDefinition => variable.typeElement
    case _ => None
  }

  def removeTypeAnnotationIfNeeded(element: ScalaPsiElement,
                                   state: ScalaApplicationSettings.ReturnTypeLevel = ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE): Unit = {
    state match {
      case ADD => //nothing
      case REMOVE | BY_CODE_STYLE =>
        getTypeElement(element) match {
          case Some(typeElement)
            if (state == REMOVE) ||
              ((state == BY_CODE_STYLE) &&
                !ScalaTypeAnnotationSettings(element.getProject).isTypeAnnotationRequiredFor(Declaration(element), Location(element), Some(Definition(element)))) =>
            AddOrRemoveStrategy.removeTypeAnnotation(typeElement)
          case _ =>
        }
    }
  }

  def removeAllTypeAnnotationsIfNeeded(elements: Seq[PsiElement],
                                       state: ScalaApplicationSettings.ReturnTypeLevel = ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE): Unit = {
    elements.foreach(_.depthFirst().foreach {
      case scalaPsiElement: ScalaPsiElement => removeTypeAnnotationIfNeeded(scalaPsiElement, state)
      case _ =>
    })
  }

  // TODO It's better to avoid global variables
  private var requestCountsToShow: Int = 0

  def showTypeAnnotationsSettings(project: Project): Unit = {
    requestCountsToShow += 1
    showWindowInvokeLater(project)
  }

  private def showWindowInvokeLater(project: Project): Unit = {
    extensions.invokeLater {
      val groups: Array[ConfigurableGroup] = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
      val visitor = new ConfigurableVisitor.ByID("preferences.sourceCode.Scala")
      val configurable: Configurable = visitor.find(groups: _*)

      assert(configurable != null, "Cannot find configurable: " + classOf[CodeStyleSchemesConfigurable].getName)

      if (requestCountsToShow > 0) { // show window only for the first request
        ShowSettingsUtil.getInstance.editConfigurable(project, configurable, new Runnable() {
          requestCountsToShow += 1

          def run() {
            val codeStyleMainPanel: CodeStyleMainPanel = configurable.createComponent.asInstanceOf[CodeStyleMainPanel]
            assert(codeStyleMainPanel != null, "Cannot find Code Style main panel")

            codeStyleMainPanel.getPanels.headOption.foreach { panel =>
              val selectedPanel = panel.getSelectedPanel
              assert(selectedPanel != null)
              selectedPanel match {
                case tab: ScalaTabbedCodeStylePanel => tab.changeTab("Type Annotations")
                case _ =>
              }
            }
            requestCountsToShow = 0
          }
        })
      }
    }
  }

  def createTypeAnnotationsHLink(project: Project, msg: String): HyperlinkLabel = {
    val typeAnnotationsSettings: HyperlinkLabel = new HyperlinkLabel(msg)
    typeAnnotationsSettings.addHyperlinkListener(new HyperlinkListener() {
      def hyperlinkUpdate(e: HyperlinkEvent) {
        if (e.getEventType eq HyperlinkEvent.EventType.ACTIVATED) {
          showTypeAnnotationsSettings(project)
        }
      }
    })

    typeAnnotationsSettings.setToolTipText(ScalaBundle.message("default.ta.tooltip"))
    typeAnnotationsSettings
  }
}
