package org.jetbrains.plugins.scala
package editor.importOptimizer


import codeInspection.ScalaRecursiveElementVisitor
import collection.mutable.HashSet
import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.ScImportStmt
import lang.psi.api.toplevel.imports.usages.{ImportUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportExprUsed}
import lang.resolve.ScalaResolveResult
/**
 * User: Alexander Podkhalyuzin
 * Date: 16.06.2009
 */

class ScalaImportOptimizer extends ImportOptimizer {
  def processFile(file: PsiFile): Runnable = {
    if (file.isInstanceOf[ScalaFile]) {
      val scalaFile: ScalaFile = file.asInstanceOf[ScalaFile]
      def getUnusedImports: HashSet[ImportUsed] = {
        val usedImports = new HashSet[ImportUsed]
        file.accept(new ScalaRecursiveElementVisitor {
          override def visitReference(ref: ScReferenceElement) = {
            if (PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) == null) {
              ref.multiResolve(false) foreach {
                case scalaResult: ScalaResolveResult =>
                  usedImports ++= scalaResult.importsUsed
                  //println(ref.getElement.getText + " -- " + scalaResult.importsUsed + " -- " + scalaResult.element)
                case _ =>
              }
            }
            super.visitReference(ref)
          }
        })
        val unusedImports = new HashSet[ImportUsed]
        unusedImports ++= scalaFile.getAllImportUsed
        unusedImports --= usedImports
        unusedImports
      }
      new Runnable {
        def run: Unit = {
          //remove unnecessary imports
          val _unusedImports = getUnusedImports
          val unusedImports = new HashSet[ImportUsed]
          for (importUsed <- _unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) => {
                val toDelete = expr.reference match {
                  case Some(ref: ScReferenceElement) => {
                    ref.multiResolve(false).length > 0
                  }
                  case _ => {
                    !PsiTreeUtil.hasErrorElements(expr)
                  }
                }
                if (toDelete) {
                  unusedImports += importUsed
                }
              }
              case ImportWildcardSelectorUsed(expr) => {
                unusedImports += importUsed
              }
              case ImportSelectorUsed(sel) => {
                if (sel.reference.getText == sel.importedName && sel.reference.resolve != null) {
                  unusedImports += importUsed
                }
              }
            }
          }
          for (importUsed <- unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) => {
                expr.deleteExpr
              }
              case ImportWildcardSelectorUsed(expr) => {
                expr.wildcardElement match {
                  case Some(element: PsiElement) => {
                    if (expr.selectors.length == 0) {
                      expr.deleteExpr
                    } else {
                      var node = element.getNode
                      var prev = node.getTreePrev
                      var t = node.getElementType
                      do {
                        t = node.getElementType
                        node.getTreeParent.removeChild(node)
                        node = prev
                        if (node != null) prev = node.getTreePrev
                      } while (node != null && t != ScalaTokenTypes.tCOMMA)
                    }
                  }
                  case _ =>
                }
              }
              case ImportSelectorUsed(sel) => {
                sel.deleteSelector
              }
            }
          }
          val documentManager = PsiDocumentManager.getInstance(scalaFile.getProject)
          documentManager.commitDocument(documentManager.getDocument(scalaFile))
          //todo: add deleting unnecessary braces
          //todo: add removing blank lines (last)
          //todo: add other optimizing
        }
      }
    } else {
      EmptyRunnable.getInstance
    }
  }

  def supports(file: PsiFile): Boolean = {
    true
  }
}