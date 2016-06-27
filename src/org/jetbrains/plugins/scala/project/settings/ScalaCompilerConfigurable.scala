package org.jetbrains.plugins.scala.project.settings

import javax.swing.JComponent

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.AbstractConfigurable

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaCompilerConfigurablbt