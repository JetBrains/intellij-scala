package org.jetbrains.plugins.scala
package editor.importOptimizer


import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.toplevel.imports.usages.{ImportUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportExprUsed}
import lang.resolve.ScalaResolveResult
import collection.{mutable, Set}
import lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.api.expr.{ScMethodCall, ScForStatement, ScExpression}
import lang.psi.{ScImportsHolder, ScalaPsiUtil, ScalaPsiElement}
import scala.Some
import scala.collection.JavaConversions._
import settings.ScalaProjectSettings
import lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 16.06.2009
 */

class ScalaImportOptimizer extends ImportOptimizer {
  import ScalaImportOptimizer.isLanguageFeatureImport

  def processFile(file: PsiFile): Runnable = processFile(file, deleteOnlyWrongImorts = false)

  def processFile(file: PsiFile, deleteOnlyWrongImorts: Boolean): Runnable = {
    val scalaFile = file match {
      case scFile: ScalaFile => scFile
      case multiRootFile: PsiFile if multiRootFile.getViewProvider.getLanguages contains ScalaFileType.SCALA_LANGUAGE =>
        multiRootFile.getViewProvider.getPsi(ScalaFileType.SCALA_LANGUAGE).asInstanceOf[ScalaFile]
      case _ => return EmptyRunnable.getInstance() 
    }
    
    def getUnusedImports: mutable.HashSet[ImportUsed] = {
        val usedImports = new mutable.HashSet[ImportUsed]
        scalaFile.accept(new ScalaRecursiveElementVisitor {
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

          override def visitSimpleTypeElement(simple: ScSimpleTypeElement) {
            simple.findImplicitParameters match {
              case Some(parameters) =>
                parameters.foreach {
                  case r: ScalaResolveResult => usedImports ++= r.importsUsed
                  case _ =>
                }
              case _ =>
            }
            super.visitSimpleTypeElement(simple)
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
        val unusedImports = new mutable.HashSet[ImportUsed]
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
          val unusedImports = new mutable.HashSet[ImportUsed]
          for (importUsed <- _unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) => {
                val toDelete = expr.reference match {
                  case Some(ref: ScReferenceElement) =>
                    if (deleteOnlyWrongImorts) ref.multiResolve(true).isEmpty
                    else true
                  case _ =>
                    if (deleteOnlyWrongImorts) false
                    else !PsiTreeUtil.hasErrorElements(expr)
                }
                if (toDelete) {
                  if (!isLanguageFeatureImport(expr))
                    unusedImports += importUsed
                }
              }
              case ImportWildcardSelectorUsed(expr) => {
                if (expr.reference.isDefined && expr.reference.get.multiResolve(false).isEmpty) unusedImports += importUsed
                else if (!deleteOnlyWrongImorts && !isLanguageFeatureImport(expr)) {
                  unusedImports += importUsed
                }
              }
              case ImportSelectorUsed(sel) => {
                if (sel.reference.multiResolve(false).isEmpty) unusedImports += importUsed
                else if (!deleteOnlyWrongImorts && sel.reference.getText == sel.importedName &&
                  !isLanguageFeatureImport(PsiTreeUtil.getParentOfType(sel, classOf[ScImportExpr]))) {
                  unusedImports += importUsed
                }
              }
            }
          }
          
          def plainDeleteUnused(imp: ScImportExpr) {
            imp.getContainingFile match {
              case scalaFile: ScalaFile => scalaFile plainDeleteImport imp
              case _ => imp.deleteExpr()
            }
          }
          
          for (importUsed <- unusedImports) {
            importUsed match {
              case ImportExprUsed(expr) =>
                plainDeleteUnused(expr)
              case ImportWildcardSelectorUsed(expr) => {
                expr.wildcardElement match {
                  case Some(element: PsiElement) => {
                    if (expr.selectors.length == 0) {
                      plainDeleteUnused(expr)
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
              case ImportSelectorUsed(sel) => sel.getContainingFile match {
                case scalaFile: ScalaFile => scalaFile plainDeleteSelector sel
                case _ => sel.deleteSelector()
              }
            }
          }
          documentManager.commitDocument(documentManager.getDocument(scalaFile))

          scalaFile.accept(new ScalaRecursiveElementVisitor {
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
          if (ScalaProjectSettings.getInstance(scalaFile.getProject).isSortImports) {
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
            documentManager.commitDocument(documentManager.getDocument(file))
          }
          //todo: add other optimizing
          //todo: add removing blank lines (last)
        }
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
      case Some(seq) =>
        for (rr <- seq if rr != null) {
          res ++= rr.importsUsed
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

  def isLanguageFeatureImport(expr: ScImportExpr): Boolean = {
    if (expr == null) return false
    if (expr.qualifier == null) return false
    expr.qualifier.resolve() match {
      case o: ScObject =>
        o.qualifiedName.startsWith("scala.language") || o.qualifiedName.startsWith("scala.languageFeature")
      case _ => false
    }
  }
}