package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import com.intellij.codeHighlighting.{TextEditorHighlightingPass, TextEditorHighlightingPassFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */

class ScalaUnusedImportsPassFactory extends TextEditorHighlightingPassFactory {
  def projectClosed: Unit = {}

  def projectOpened: Unit = {}

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    new ScalaUnusedImportPass(file, editor)
  }

  def initComponent: Unit = {}

  def disposeComponent: Unit = {}

  def getComponentName: String = "Scala Unused import pass factory"
}