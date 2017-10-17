package org.jetbrains.plugins.scala.findUsages.factory

import java.awt.event.{ItemEvent, ItemListener}
import javax.swing._

import com.intellij.find.findUsages._
import com.intellij.openapi.module.{ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.ui.IdeBorderFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}

/**
 * @author Ignat Loskutov
 */
class ScalaImplicitDefinitionUsagesDialog(element: ScReferencePattern,
                                          project: Project,
                                          findUsagesOptions: FindUsagesOptions,
                                          toShowInNewTab: Boolean,
                                          mustOpenInNewTab: Boolean,
                                          isSingleFile: Boolean,
                                          handler: FindUsagesHandler)
    extends JavaFindUsagesDialog[ScalaImplicitDefinitionFindUsagesOptions](
      element,
      project,
      findUsagesOptions,
      toShowInNewTab,
      mustOpenInNewTab,
      isSingleFile,
      handler
    ) {

  protected override def createFindWhatPanel: JPanel =
    if (!isLocalOrPrivate(getParentOfType(element, classOf[ScMember]))) {
      val whatPanel = new JPanel()
      val label = new MultiLineLabel(
        """You are willing to search for uses of a non-local implicit definition.
          |It may take a long time on large projects, so please choose a narrow scope below.""".stripMargin
      )
      whatPanel.add(label)
      whatPanel
    } else super.createFindWhatPanel()

}
