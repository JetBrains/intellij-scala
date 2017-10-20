package org.jetbrains.plugins.scala.components.libinjection.ui

import java.awt.Component
import javax.swing.JLabel

import org.jetbrains.plugins.scala.components.libinjection.JarManifest
import org.jetbrains.plugins.scala.lang.refactoring.util.DefaultListCellRendererAdapter
import org.jetbrains.plugins.scala.util.JListCompatibility.JListContainer

/**
  * @author mucianm 
  * @since 15.04.16.
  */
class JarCacheRenderer extends DefaultListCellRendererAdapter {
  override def getListCellRendererComponentAdapter(container: JListContainer, value: scala.Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
    val jarManifest: JarManifest = value.asInstanceOf[JarManifest]
    val text = if (jarManifest.isBlackListed)
      s"<html><strike>${jarManifest.jarPath}</strike><html>"
    else if (jarManifest.isLoaded)
      s"*${jarManifest.jarPath}"
    else
      jarManifest.jarPath
    val rendererComponent: JLabel = getSuperListCellRendererComponent(container.getList, text, index, isSelected, cellHasFocus).asInstanceOf[JLabel]
    rendererComponent.setToolTipText(
      s"""
        |Blacklisted - ${jarManifest.isBlackListed}
        |Loaded - ${jarManifest.isLoaded}
        |Injectors:
        |${jarManifest.pluginDescriptors.map(p => s"Plugin(${p.since} - ${p.until}):\n ${p.injectors.map(i => s"iface: ${i.iface}\nimpl: ${i.impl}").mkString("\n")}").mkString("\n")}
      """.stripMargin.trim)
    rendererComponent
  }
}
