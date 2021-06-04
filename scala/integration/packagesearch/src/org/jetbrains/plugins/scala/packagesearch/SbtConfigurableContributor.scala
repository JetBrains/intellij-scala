package org.jetbrains.plugins.scala.packagesearch

import com.intellij.openapi.project.Project
import com.intellij.ui.{DocumentAdapter, RelativeFont, TitledSeparator}
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ConfigurableContributor, ConfigurableContributorDriver}

import javax.swing.{JLabel, JTextField}
import javax.swing.event.DocumentEvent
import org.jetbrains.plugins.scala.packagesearch.configuration.{PackageSearchSbtConfiguration, packageSearchSbtConfigurationForProject}
import org.jetbrains.sbt.language.utils.SbtCommon

class SbtConfigurableContributor(project: Project) extends ConfigurableContributor {
  override def createDriver(): ConfigurableContributorDriver = new SbtConfigurableContributorDriver(project)
}

class SbtConfigurableContributorDriver(project: Project) extends ConfigurableContributorDriver {
  var modified: Boolean = false
  var configuration: PackageSearchSbtConfiguration = packageSearchSbtConfigurationForProject.getService(project)
  val textFieldChangeListener: DocumentAdapter = new DocumentAdapter() {
    override protected def textChanged(e: DocumentEvent): Unit = {
      modified = true
    }
  }

  val sbtScopeEditor: JTextField = new JTextField() {
    getDocument.addDocumentListener(textFieldChangeListener)
  }

  override def apply(): Unit = configuration.defaultSbtScope = sbtScopeEditor.getText

  override def contributeUserInterface(formBuilder: FormBuilder): Unit = {
    formBuilder.addComponent(
      new TitledSeparator(PackageSearchSbtBundle.message("packagesearch.configuration.sbt.title")),
      0
    )

    formBuilder.addLabeledComponent(PackageSearchSbtBundle.message("packagesearch.configuration.sbt.scopes.default.string"), sbtScopeEditor)

    val label = new JLabel(s"${PackageSearchSbtBundle.message("packagesearch.configuration.sbt.scopes.string")} ${SbtCommon.libScopes.replace(",", ", ")}")
    formBuilder.addComponentToRightColumn(RelativeFont.TINY.install(RelativeFont.ITALIC.install(label)))
  }

  override def isModified: Boolean = modified

  override def reset(): Unit = {
    sbtScopeEditor.setText(configuration.determineDefaultSbtConfiguration)
    modified = false
  }

  override def restoreDefaults(): Unit = {
    sbtScopeEditor.setText(SbtCommon.defaultLibScope)
    modified = true
  }
}