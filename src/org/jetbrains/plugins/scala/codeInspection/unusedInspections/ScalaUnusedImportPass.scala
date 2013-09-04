package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import annotator.importsTracker.{ScalaRefCountHolder, ImportTracker}
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, AnnotationHolderImpl}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import lang.psi.api.toplevel.imports.usages.ImportUsed
import lang.psi.api.ScalaFile
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightingLevelManager, HighlightLevelUtil}
import java.util.Collections
import java.util
import com.intellij.codeInsight.intention.IntentionAction

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */

class ScalaUnusedImportPass(file: PsiFile, editor: Editor)
  extends TextEditorHighlightingPass(file.getProject, editor.getDocument) with ScalaUnusedImportPassBase {

  protected val myEditor: Editor = editor
  protected val myFile: PsiFile = file
  
  def doCollectInformation(progress: ProgressIndicator) {
  }

  def doApplyInformationToEditor() {
    file match {
      case scalaFile: ScalaFile if HighlightingLevelManager.getInstance(file.getProject) shouldInspect file =>
        val unusedImports: Array[ImportUsed] = ImportTracker getInstance file.getProject getUnusedImport scalaFile
        val annotations = collectAnnotations(unusedImports, new AnnotationHolderImpl(new AnnotationSession(file)))

        val list = new util.ArrayList[HighlightInfo](annotations.length)
        annotations foreach (annotation => list add (HighlightInfo fromAnnotation annotation) )
        
        highlightAll(list)
      case _: ScalaFile => highlightAll(Collections.emptyList[HighlightInfo]())
      case _ => 
    }
  }

  protected def getOptimizeFix: IntentionAction = new ScalaOptimizeImportsFix //todo it is stateless => shouldn't it be singleton?
}
