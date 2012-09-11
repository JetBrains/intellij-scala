package org.jetbrains.plugins.scala
package editor.importOptimizer


import collection.mutable.HashSet
import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.toplevel.imports.usages.{ImportUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportExprUsed}
import lang.resolve.ScalaResolveResult
import collection.Set
import lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.api.expr.{ScMethodCall, ScForStatement, ScExpression}
import lang.psi.{ScImportsHolder, ScalaPsiUtil, ScalaPsiElement}
import lang.psi.api.toplevel.packaging.ScPackaging
import scala.Some
import scala.collection.JavaConversions._
import lang.formatting.settings.ScalaCodeStyleSettings
import settings.ScalaProjectSettings
import lang.psi.api.toplevel.typedef.ScObject

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
          override def visitReference(ref: ScReferenceElement) {
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

          override def visitElement(element: ScalaPsiElement) {
            val imports = element match {
              case expression: ScExpression => {
                checkTypeForExpression(expression)
              }
              case _ => ScalaImportOptimizer.NO_IMPORT_USED
            }
            usedImports ++= imports
            super.visitElement(element)
          }
        })
        val unusedImports = new HashSet[ImportUsed]
        unusedImports ++= scalaFile.getAllImportUsed
        unusedImports --= usedImports
        unusedImports
      }
      new Runnable {
        def run() {
          val documentManager = PsiDocumentManager.getInstance(scalaFile.getProject)
          documentManager.commitDocument(documentManager.getDocument(scalaFile)) //before doing changes let's commit document
          //remove unnecessary imports
          val _unusedImports = getUnusedImports
          val unusedImports = new HashSet[ImportUsed]
          for (importUsed <- _unusedImports) {
            def isLanguageFeatureImport(expr: ScImportExpr): Boolean = {
              if (expr == null) return false
              if (expr.qualifier == null) return false
              expr.qualifier.resolve() match {
                case o: ScObject =>
                  o.qualifiedName.startsWith("scala.language") || o.qualifiedName.startsWith("scala.languageFeature")
                case _ => false
              }
            }
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
                  if (!isLanguageFeatureImport(expr))
                    unusedImports += importUsed
                }
              }
              case ImportWildcardSelectorUsed(expr) => {
                if (!isLanguageFeatureImport(expr))
                  unusedImports += importUsed
              }
              case ImportSelectorUsed(sel) => {
                if (sel.reference.getText == sel.importedName && sel.reference.multiResolve(false).length > 0 &&
                  !isLanguageFeatureImport(PsiTreeUtil.getParentOfType(sel, classOf[ScImportExpr]))) {
                  unusedImports += importUsed
                }
              }
            }
          }
          for (importUsed <- unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) => {
                expr.deleteExpr()
              }
              case ImportWildcardSelectorUsed(expr) => {
                expr.wildcardElement match {
                  case Some(element: PsiElement) => {
                    if (expr.selectors.length == 0) {
                      expr.deleteExpr()
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
                sel.deleteSelector()
              }
            }
          }
          documentManager.commitDocument(documentManager.getDocument(scalaFile))

          file.accept(new ScalaRecursiveElementVisitor {
            override def visitImportExpr(expr: ScImportExpr) {
              expr.selectorSet match {
                case Some(selectors) if selectors.selectors.length == 1 - (if (selectors.hasWildcard) 1 else 0) => {
                  if (selectors.hasWildcard) {
                    val newImportExpr = ScalaPsiElementFactory.createImportExprFromText(expr.reference match {
                      case Some(ref) => ref.getText + "._"
                      case _ => "_"
                    }, expr.getManager)
                    expr.replace(newImportExpr)
                  } else {
                    val selector = selectors.selectors.apply(0)
                    if (selector.reference.refName == selector.importedName) {
                      val newImportExpr = ScalaPsiElementFactory.createImportExprFromText(expr.reference match {
                        case Some(ref) => ref.getText + "." + selector.importedName
                        case _ => selector.importedName
                      }, expr.getManager)
                      expr.replace(newImportExpr)
                    }
                  }
                }
                case _ =>
              }
            }
          })
          documentManager.commitDocument(documentManager.getDocument(scalaFile))

          // Sort all import sections in the file lexagraphically
          if (ScalaProjectSettings.getInstance(file.getProject).isSortImports) {
            val fileHolder = Seq(scalaFile.getContainingFile.asInstanceOf[ScImportsHolder])
            (PsiTreeUtil.collectElementsOfType(scalaFile, classOf[ScImportsHolder]) match {
              case null => fileHolder
              case array => fileHolder ++ array.toSeq
            }) foreach { importHolder =>
              // Get the import statements in the imports holder
              val importsList = importHolder.getImportStatements
              // Sort and make copies of them
              val importsSorted = importsList sortBy { imp =>
                imp.getText
              } map { _.copy() }
              // If the list isn't empty, add the copies in order and delete the originals
              if (!importsList.isEmpty) {
                val first = importsList(0)
                importsSorted foreach { imp =>
                  importHolder.addImportBefore(imp, first)
                }
                importsList foreach { imp =>
                  importHolder.deleteImportStmt(imp)
                }
              }
            }
            documentManager.commitDocument(documentManager.getDocument(scalaFile))
          }
          //todo: add other optimizing
          //todo: add removing blank lines (last)
        }
      }
    } else {
      EmptyRunnable.getInstance
    }
  }

  private def checkTypeForExpression(expr: ScExpression): Set[ImportUsed] = {
    var res: collection.mutable.HashSet[ImportUsed] =
    collection.mutable.HashSet(expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType()).
      importsUsed.toSeq : _*)
    expr match {
      case call: ScMethodCall =>
        res ++= call.getImportsUsed
      case _ =>
    }
    expr.findImplicitParameters match {
      case Some(seq) => {
        for (rr <- seq if rr != null) {
          res ++= rr.importsUsed
        }
      }
      case _ =>
    }
    expr match {
      case f: ScForStatement => res ++= ScalaPsiUtil.getExprImports(f)
      case _ =>
    }
    res
  }


  def supports(file: PsiFile): Boolean = {
    true
  }
}

object ScalaImportOptimizer {
  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty
}