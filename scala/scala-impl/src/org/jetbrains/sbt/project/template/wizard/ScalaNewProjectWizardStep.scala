package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.{AbstractModuleBuilder, ModuleBuilder}
import com.intellij.ide.wizard._
import com.intellij.openapi.module.{Module, StdModuleTypes}
import com.intellij.openapi.observable.properties.{GraphPropertyImpl, PropertyGraph}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.{JdkComboBox, JdkComboBoxKt}
import com.intellij.ui.components.ComponentsKt
import com.intellij.ui.dsl.builder._
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.SbtModuleBuilder
import org.jetbrains.sbt.project.template.wizard.kotlin_interop.ComboBoxKt_Wrapper
import org.jetbrains.sbt.project.template.wizard.kotlin_interop.dsl.RowOps

//noinspection ApiStatus,UnstableApiUsage
final class ScalaNewProjectWizardStep(parentStep: NewProjectWizardLanguageStep)
  extends AbstractNewProjectWizardStep(parentStep)
    with SbtModuleStepLike {

  override protected val selections: SbtModuleStepSelections = SbtModuleStepSelections.default

  override protected lazy val availableScalaVersions: Versions = Versions.Scala.loadVersionsWithProgress()
  override protected lazy val availableSbtVersions: Versions = Versions.SBT.loadVersionsWithProgress()
  override protected lazy val availableSbtVersionsForScala3: Versions = Versions.SBT.sbtVersionsForScala3(availableSbtVersions)

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  // detect when scala language is selected in the "New Project Wizard"
  propertyGraph.afterPropagation { () =>
    if (parentStep.getLanguage == ScalaNewProjectWizard.ScalaLanguageText) {
      initSelectionsAndUi()
    }
    KUnit
  }

  private var sdkComboBox: Cell[JdkComboBox] = _

  private val sdkProperty: GraphPropertyImpl[Sdk] = new GraphPropertyImpl(propertyGraph, () => null)

  override def setupProject(project: Project): Unit = {
    val sbtModuleBuilder = new SbtModuleBuilder(this.selections)
    sbtModuleBuilder.setContentEntryPath(getContext.getProjectDirectory.toAbsolutePath.toString)

    sbtModuleBuilder.addModuleConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater() {
      override def update(module: Module, rootModel: ModifiableRootModel): Unit = {
        getContext.getProjectBuilder match {
          case parentBuilder: AbstractModuleBuilder =>
            // NOTE: module path is patched in SbtModuleBuilder.createModule, we need to sync this change to the parent builder
            // otherwise some exceptions will be thrown during project creation
            parentBuilder.setModuleFilePath(sbtModuleBuilder.getModuleFilePath)
          case _ =>
        }
      }
    })

    sbtModuleBuilder.commit(project)
  }

  /**
   * Used example [[com.intellij.ide.projectWizard.generators.JavaNewProjectWizard]] as a reference
   */
  override def setupUI(panel: Panel): Unit = {
    panel.row(JavaUiBundle.message("label.project.wizard.new.project.jdk"), (row: Row) => {
      val javaSdkFilter: kotlin.jvm.functions.Function1[SdkTypeId, java.lang.Boolean] =
        (it: SdkTypeId) => it.isInstanceOf[JavaSdkType] && !it.is[DependentSdkType]
      sdkComboBox = JdkComboBoxKt.sdkComboBox(row, getContext, sdkProperty, StdModuleTypes.JAVA.getId, javaSdkFilter, null, null, null, null)
      ComboBoxKt_Wrapper.columns(sdkComboBox, TextFieldKt.COLUMNS_MEDIUM)
      KUnit
    })

    panel.row(SbtBundle.message("sbt.settings.sbt"), (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)
      row.cell(sbtVersionComboBox).horizontalAlign(HorizontalAlign.FILL)
      row.cell(downloadSbtSourcesCheckbox)
      KUnit
    })

    panel.row(SbtBundle.message("sbt.settings.scala"), (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)
      row.cell(scalaVersionComboBox).horizontalAlign(HorizontalAlign.FILL)
      row.cell(downloadScalaSourcesCheckbox)
      KUnit
    })

    // NOTE: we set a "help" tooltip in the row label and not via UI.PanelFactory.panel.withTooltip
    // because the latter adds some strange indent to the left, which looks a little poor
    // I didn't find a nice way to workaround this.
    val packagePrefixLabel = ComponentsKt.Label(ScalaBundle.message("package.prefix.label"), null, null, false, null)
    packagePrefixLabel.setToolTipText(ScalaBundle.message("package.prefix.help"))
    panel.row(packagePrefixLabel, (row: Row) => {
      row.cell(packagePrefixField).horizontalAlign(HorizontalAlign.FILL)
      row.layout(RowLayout.INDEPENDENT)
      KUnit
    })
  }
}

