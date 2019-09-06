package org.jetbrains.plugins.scala
package highlighter

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

final class ScalaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  override def getSyntaxHighlighter(project: Project, file: VirtualFile) = new ScalaSyntaxHighlighter
}