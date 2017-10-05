package org.jetbrains.plugins.scala.lang.scaladoc.generate

import javax.swing.Icon

import com.intellij.analysis.AnalysisScope
import com.intellij.execution.Executor
import com.intellij.execution.configurations.{ModuleRunProfile, RunProfileState}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * User: Dmitry Naidanov
 * Date: 12.10.11
 */
class ScaladocConfiguration(private val form: ScaladocConsoleRunConfigurationForm, private val project: Project,
                            private val scope: AnalysisScope) extends ModuleRunProfile {
  override def getModules: Array[Module] = {
    Module.EMPTY_ARRAY
  }

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val state: ScaladocCommandLineState = new ScaladocCommandLineState(env, project)
    state.setAdditionalScaladocFlags(form.getAdditionalFlags)
    state.setScope(scope)
    state.setVerbose(form.isVerbose)
    state.setDocTitle(form.getDocTitle)
    state.setMaxHeapSize(form.getMaxHeapSize)
    state.setShowInBrowser(form.isShowInBrowser)
    state.setOutputDir(form.getOutputDir)

    state
  }

  override def getName: String = "Generate Scaladoc"

  override def getIcon: Icon = null

}