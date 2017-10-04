package org.jetbrains.plugins.scala.codeInsight.generation.ui

import java.awt.BorderLayout
import java.util
import javax.swing.JCheckBox

import org.jetbrains.plugins.scala.ScalaBundle

/**
 *
 * @author Rado Buransky (buransky.com)
 */
class ScalaToStringMemberSelectionPanel(title: String,
                                        memberInfo: util.List[ScalaMemberInfo],
                                        abstractColumnHeader: String)
        extends ScalaMemberSelectionPanel(title, memberInfo, abstractColumnHeader) {
  val checkBox = new JCheckBox(
    ScalaBundle.message("org.jetbrains.plugins.scala.codeInsight.generation.ui.toString.withFieldNames"))
  add(checkBox, BorderLayout.SOUTH)
}
