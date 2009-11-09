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
import lang.psi.types.result.{TypingContext, TypeResult, Success}
import lang.psi.api.base.types.ScTypeElement
import collection.Set
import lang.psi.api.expr.{ScBlockExpr, ScReturnStmt, ScExpression}
import annotator.ScalaAnnotator
import lang.psi.types.{ScType, Unit}
import lang.psi.api.statements.{ScVariableDefinition, ScFunction, ScPatternDefinition}

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

          override def visitElement(element: PsiElement) = {
            // todo Duplication between these checks and ScalaAnnotator.
            /*val imports = element match {
              case ret: ScReturnStmt => {
                checkExplicitTypeForReturnStatement(ret)
              }
              case value: ScPatternDefinition => {
                checkDefinitionType(value)
              }
              case value: ScVariableDefinition => {
                checkDefinitionType(value)
              }
              case expr: ScExpression if (Option(PsiTreeUtil.getParentOfType(expr, classOf[ScFunction])).exists(_.getNode.getLastChildNode == expr.getNode)) => {
                checkExplicitTypeForReturnExpression(expr)
              }
              case _ => ScalaImportOptimizer.NO_IMPORT_USED
            }
            usedImports ++= imports*/
            super.visitElement(element)
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

  private def checkExplicitTypeForReturnStatement(ret: ScReturnStmt): Set[ImportUsed] = {
    var fun: ScFunction = PsiTreeUtil.getParentOfType(ret, classOf[ScFunction])
    fun match {
      case null => {
        ScalaImportOptimizer.NO_IMPORT_USED
      }
      case _ if !fun.hasAssign || fun.returnType.exists(_ == Unit) => {
        ScalaImportOptimizer.NO_IMPORT_USED
      }
      case _ => fun.returnTypeElement match {
        case Some(x: ScTypeElement) => {
          import org.jetbrains.plugins.scala.lang.psi.types._
          val funType = fun.returnType
          val exprType: TypeResult[ScType] = ret.expr match {
            case Some(e: ScExpression) => e.getType(TypingContext.empty)
            case None => Success(Unit, None)
          }
          ScalaAnnotator.smartCheckConformance(funType, exprType, () => {
            ret.expr match {
              case Some(e: ScExpression) => e.allTypesAndImports
              case _ => List()
            }
          })._2
        }
        case _ => ScalaImportOptimizer.NO_IMPORT_USED
      }
    }
  }

  private def checkExplicitTypeForReturnExpression(expr: ScExpression): Set[ImportUsed] = {
    var fun: ScFunction = PsiTreeUtil.getParentOfType(expr, classOf[ScFunction])
    fun match {
      case _ if !fun.hasAssign || fun.returnType.exists(_ == Unit) => {
        ScalaImportOptimizer.NO_IMPORT_USED
      }
      case _ => fun.returnTypeElement match {
        case Some(x: ScTypeElement) => {
          import org.jetbrains.plugins.scala.lang.psi.types._
          val funType = fun.returnType
          val exprType: TypeResult[ScType] = expr.getType(TypingContext.empty)
          ScalaAnnotator.smartCheckConformance(funType, exprType, () => expr.allTypesAndImports)._2
        }
        case _ => {
          ScalaImportOptimizer.NO_IMPORT_USED
        }
      }
    }
  }

  /*private def checkDefinitionType(value: ScalaAnnotator.TypedExpression): Set[ImportUsed] = {
    value.typeElement match {
      case Some(te: ScTypeElement) => {
        val valueType: TypeResult[ScType] = te.getType(TypingContext.empty)
        val exprType = value.expr.getType(TypingContext.empty)
        ScalaAnnotator.smartCheckConformance(valueType, exprType, () => {value.expr.allTypesAndImports})._2
      }
      case _ => ScalaImportOptimizer.NO_IMPORT_USED
    }
  }*/


  def supports(file: PsiFile): Boolean = {
    true
  }
}

object ScalaImportOptimizer {
  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty
}