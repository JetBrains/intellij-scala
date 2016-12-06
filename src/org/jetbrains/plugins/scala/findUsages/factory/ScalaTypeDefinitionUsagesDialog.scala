package org.jetbrains.plugins.scala
package findUsages.factory

import javax.swing.{BoxLayout, JComponent, JPanel}

import com.intellij.find.FindBundle
import com.intellij.find.findUsages._
import com.intellij.openapi.project.Project
import com.intellij.ui.{IdeBorderFactory, StateRestoringCheckBox}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Alefas
 * @since 15.12.12
 */
class ScalaTypeDefinitionUsagesDialog(element: ScTypeDefinition, project: Project, findUsagesOptions: FindUsagesOptions,
                                      toShowInNewTab: Boolean, mustOpenInNewTab: Boolean, isSingleFile: Boolean,
                                      handler: FindUsagesHandler)
  extends JavaFindUsagesDialog[ScalaTypeDefinitionFindUsagesOptions](element, project, findUsagesOptions, toShowInNewTab,
    mustOpenInNewTab, isSingleFile, handler) {
  private var myCbUsages: StateRestoringCheckBox = _
  private var myCbMembersUsages: StateRestoringCheckBox = _
  private var myCbImplementingTypeDefinitions: StateRestoringCheckBox = _
  private var myCbCompanionModule: StateRestoringCheckBox = _

  override def getPreferredFocusedControl: JComponent = {
    myCbUsages
  }

  import com.intellij.find.findUsages.AbstractFindUsagesDialog.{isSelected, isToChange}

  override def calcFindUsagesOptions(options: ScalaTypeDefinitionFindUsagesOptions) {
    super.calcFindUsagesOptions(options)
    if (isToChange(myCbUsages)) {
      options.isUsages = isSelected(myCbUsages)
    }
    if (isToChange(myCbMembersUsages)) {
      options.isMembersUsages = isSelected(myCbMembersUsages)
    }
    if (isToChange(myCbImplementingTypeDefinitions)) {
      options.isImplementingTypeDefinitions = isSelected(myCbImplementingTypeDefinitions)
    }
    if (isToChange(myCbCompanionModule)) {
      options.isSearchCompanionModule = isSelected(myCbCompanionModule)
    }
    options.isSkipImportStatements = false
  }

  protected override def createFindWhatPanel: JPanel = {
    val findWhatPanel: JPanel = new JPanel
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group"), true))
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS))
    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), getFindUsagesOptions.isUsages, findWhatPanel, true)
    myCbMembersUsages = addCheckboxToPanel(ScalaBundle.message("find.what.members.usages.checkbox"), getFindUsagesOptions.isMembersUsages, findWhatPanel, true)
    myCbImplementingTypeDefinitions = addCheckboxToPanel(ScalaBundle.message("find.what.implementing.type.definitions.checkbox"),
      getFindUsagesOptions.isImplementingTypeDefinitions, findWhatPanel, true)
    element.baseCompanionModule.foreach { _ =>
      myCbCompanionModule = addCheckboxToPanel(ScalaBundle.message("find.what.companion.module.checkbox"), getFindUsagesOptions.isSearchCompanionModule, findWhatPanel, true)
    }
    findWhatPanel
  }

  protected override def update() {
    if (myCbToSearchForTextOccurrences != null) {
      if (isSelected(myCbUsages)) {
        myCbToSearchForTextOccurrences.makeSelectable()
      } else {
        myCbToSearchForTextOccurrences.makeUnselectable(false)
      }
    }
    if (myCbCompanionModule != null) {
      if (isSelected(myCbUsages)) {
        myCbCompanionModule.makeSelectable()
      } else {
        myCbCompanionModule.makeUnselectable(false)
      }
    }
    val hasSelected: Boolean = isSelected(myCbUsages) || isSelected(myCbMembersUsages) ||
      isSelected(myCbImplementingTypeDefinitions)
    setOKActionEnabled(hasSelected)
  }
}
