package org.jetbrains.plugins.scala.highlighter

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  override def getSyntaxHighlighter(project: Project, vFile: VirtualFile): ScalaSyntaxHighlighter = new ScalaSyntaxHighlighter
}