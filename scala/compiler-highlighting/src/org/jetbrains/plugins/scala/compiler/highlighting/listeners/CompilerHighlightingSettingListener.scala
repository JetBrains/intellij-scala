package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, FileHighlightingSettingListener}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.SettingsTrigger

class CompilerHighlightingSettingListener(project: Project) extends FileHighlightingSettingListener {
  override def settingChanged(root: PsiElement, setting: FileHighlightingSetting): Unit = {
    SettingsTrigger.trigger(project, root, setting)
  }
}
