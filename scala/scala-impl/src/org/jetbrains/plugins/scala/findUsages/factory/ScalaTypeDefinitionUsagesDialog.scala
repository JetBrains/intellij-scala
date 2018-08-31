package org.jetbrains.plugins.scala
package findUsages.factory

import javax.swing.{BoxLayout, JComponent, JPanel}
import com.intellij.find.FindBundle
import com.intellij.find.findUsages._
import com.intellij.openapi.project.Project
import com.intellij.ui.{IdeBorderFactory, StateRestoringCheckBox}
import com.intellij.util.ui.JBUI.Borders
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}

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

  private var myCbOnlyNewInstances: StateRestoringCheckBox = _

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
    if (options.isUsages) {
      if (isToChange(myCbOnlyNewInstances)) {
        options.isOnlyNewInstances = isSelected(myCbOnlyNewInstances)
      }
    }
    options.isSkipImportStatements = false
  }

  protected override def createFindWhatPanel: JPanel = {
    val findWhatPanel: JPanel = new JPanel()
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group"), true))
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS))

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), getFindUsagesOptions.isUsages, findWhatPanel, true)

    if (element.isInstanceOf[ScClass]) {
      val usageKindPanel = new JPanel()
      usageKindPanel.setBorder(Borders.empty(0, 20, 0, 0))
      usageKindPanel.setLayout(new BoxLayout(usageKindPanel, BoxLayout.Y_AXIS))

      myCbOnlyNewInstances = addCheckboxToPanel(ScalaBundle.message("find.what.new.instances.usages"), getFindUsagesOptions.isOnlyNewInstances, usageKindPanel, true)

      //this mode should be available only from dialog, so unselect it every time
      myCbOnlyNewInstances.setSelected(false)

      findWhatPanel.add(usageKindPanel)
    }

    myCbMembersUsages = addCheckboxToPanel(ScalaBundle.message("find.what.members.usages.checkbox"), getFindUsagesOptions.isMembersUsages, findWhatPanel, true)
    myCbImplementingTypeDefinitions = addCheckboxToPanel(ScalaBundle.message("find.what.implementing.type.definitions.checkbox"),
      getFindUsagesOptions.isImplementingTypeDefinitions, findWhatPanel, true)
    element.baseCompanionModule.foreach { _ =>
      myCbCompanionModule = addCheckboxToPanel(ScalaBundle.message("find.what.companion.module.checkbox"), getFindUsagesOptions.isSearchCompanionModule, findWhatPanel, true)
    }
    findWhatPanel
  }

  protected override def update() {
    val dependentCbs = Seq(
      myCbToSearchForTextOccurrences,
      myCbCompanionModule,
      myCbOnlyNewInstances
    )

    if (isSelected(myCbUsages))
      dependentCbs.foreach(makeSelectable)
    else
      dependentCbs.foreach(makeUnselectable)


    val hasSelected: Boolean =
      isSelected(myCbUsages) ||
        isSelected(myCbMembersUsages) ||
        isSelected(myCbImplementingTypeDefinitions)

    setOKActionEnabled(hasSelected)
  }

  private def makeSelectable(cb: StateRestoringCheckBox): Unit = {
    if (cb != null) cb.makeSelectable()
  }

  private def makeUnselectable(cb: StateRestoringCheckBox): Unit = {
    if (cb != null) cb.makeUnselectable(false)
  }
}
