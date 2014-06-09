package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.refactoring.classMembers.MemberInfoModel
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberSelectionTableBase


class ScalaMemberSelectionTable(memberInfos: java.util.Collection[ScalaMemberInfo],
                                memberInfoModel: MemberInfoModel[ScNamedElement, ScalaMemberInfo],
                                abstractColumnHeader: String)
        extends ScalaMemberSelectionTableBase[ScNamedElement, ScalaMemberInfo](memberInfos, memberInfoModel, abstractColumnHeader)
