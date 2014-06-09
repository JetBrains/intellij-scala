package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import java.util
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberSelectionPanelBase

/**
 * Nikolay.Tropin
 * 2014-05-28
 */
class ScalaMemberSelectionPanel(title: String,
                                memberInfo: util.List[ScalaMemberInfo],
                                abstractColumnHeader: String)
        extends ScalaMemberSelectionPanelBase[ScNamedElement, ScalaMemberInfo](title, memberInfo, abstractColumnHeader) {

  override def createMemberSelectionTable(memberInfos: util.List[ScalaMemberInfo], abstractColumnHeader: String) =
    new ScalaMemberSelectionTable(memberInfos, null, abstractColumnHeader)

}
