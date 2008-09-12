package org.jetbrains.plugins.scala.lang.psi

import _root_.scala.collection.mutable.ArrayBuffer
import impl.ScalaPsiElementFactory
import api.toplevel.imports.{ScImportExpr, ScImportStmt}
import api.toplevel.packaging.{ScPackageStatement, ScPackaging}
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._
import scope._

trait ScImportsHolder extends ScalaPsiElement {
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent != null) {
      var run = lastParent.getPrevSibling
      while (run != null) {
        if (run.isInstanceOf[ScImportStmt] &&
            !run.processDeclarations(processor, state, lastParent, place)) return false
        run = run.getPrevSibling
      }
    }
    true
  }

  //todo[Sasha] rewrite using ScalaElementTypes
  def deleteImportStmt(stmt: ScImportStmt): Unit = {
    val remove = getNode.removeChild _
    val node = stmt.getNode
    val next = node.getTreeNext
    if (next == null) {
      remove(node)
    }
    else if (next.getText.indexOf("\n") != -1) {
      remove(next)
      remove(node)
    } else if (next.getText.charAt(0) == ';') {
      val nextnext = next.getTreeNext
      if (nextnext == null) {
        remove(next)
        remove(node)
      }
      else if (next.getText.indexOf("\n") != -1) {
        remove(nextnext)
        remove(next)
        remove(node)
      } else {
        remove(node)
        remove(next)
      }
    }
    else {
      remove(node)
    }
  }

  private def importStatementsInHeader: Seq[ScImportStmt] = {
    var end = false
    val buf = new ArrayBuffer[ScImportStmt]
    for (child <- getChildren if !end) {
      child match {
        case x: ScImportStmt => buf += x
        case _: ScTypeDefinition | _: ScPackaging => end = true
        case _ =>
      }
    }
    return buf.toSeq
  }

  def addImportForClass(clazz: PsiClass) {
    val newImport = ScalaPsiElementFactory.createImportStatementFromClass(this, clazz, this.getManager)
    val resolve = ScalaPsiElementFactory.getResolveForClassQualifier(this, clazz, getManager)
    val sameExpressions: Array[ScImportExpr] = (for (importStmt <- importStatementsInHeader; importExpr <- importStmt.importExprs
      if resolve != null && importExpr.qualifier.resolve == resolve)
      yield importExpr).toArray
    val importSt = if (sameExpressions.length == 0) newImport
                   else {
                     val stmt = ScalaPsiElementFactory.createBigImportStmt(newImport.importExprs(0), sameExpressions, this.getManager)
                     for (expr <- sameExpressions) expr.deleteExpr
                     stmt
                   }
    def tryImport(imp: PsiElement): Boolean = {
      var prev: PsiElement = imp.getPrevSibling
      prev match {
        case null => return true
        case _: ScTypeDefinition => return false
        case _: ScPackaging => return false
        case _ => return tryImport(prev)
      }
    }
    def lessTo(left: ScImportStmt, right: ScImportStmt): Boolean = left.getText.toLowerCase < right.getText.toLowerCase
    def isLT(s: String): Boolean = s.toCharArray.filter((c: Char) => c match {case ' ' | '\n' => false case _ => true}).length == 0
    findChild(classOf[ScImportStmt]) match {
      case Some(x) if tryImport(x) => {
        var stmt: PsiElement = x
        var added = false
        while (!added && stmt != null && (stmt.isInstanceOf[ScImportStmt]) || isLT(stmt.getText) || stmt.getText == ";") {
          stmt match {
            case im: ScImportStmt => {
              if (lessTo(importSt, im)) {
                added = true
                addBefore(importSt, im)
              }
            }
            case _ =>
          }
          stmt = stmt.getNextSibling
        }
        if (!added) {
          if (stmt != null) {
            while (!stmt.isInstanceOf[ScImportStmt]) stmt = stmt.getPrevSibling
            addAfter(importSt, stmt)
          }
          else addAfter(importSt, getLastChild)
        }
      }
      case _ => {
        findChild(classOf[ScPackageStatement]) match {
          case Some(x) => {
            addAfter(importSt, x)
          }
          case None => {
            if (getFirstChild != null) addBefore(importSt, getFirstChild)
            else add(importSt)
          }
        }
      }
    }
  }
}