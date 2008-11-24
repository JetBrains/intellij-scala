package org.jetbrains.plugins.scala.lang.psi

import api.ScalaFile
import lexer.ScalaTokenTypes
import _root_.scala.collection.mutable.ArrayBuffer
import impl.ScalaPsiElementFactory
import api.toplevel.imports.{ScImportExpr, ScImportStmt}
import api.toplevel.packaging.{ScPackageStatement, ScPackaging}
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._
import scope._

trait ScImportsHolder extends ScalaPsiElement {

  def getImportStatements: Seq[ScImportStmt] = findChildrenByClass(classOf[ScImportStmt])

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



  private def importStatementsInHeader: Seq[ScImportStmt] = {
    val buf = new ArrayBuffer[ScImportStmt]
    for (child <- getChildren) {
      child match {
        case x: ScImportStmt => buf += x
        case _: ScTypeDefinition | _: ScPackaging => return buf.toSeq
        case _ =>
      }
    }
    return buf.toSeq
  }

  //Utility method to find first import statement, but only in element header
  private def findFirstImportStmt(ref: PsiElement): Option[PsiElement] = {
    def checkReference(imp: PsiElement): Boolean = {
      var prev: PsiElement = imp.getPrevSibling
      var par = ref
      while (par != null && par.getParent != this) par = par.getParent
      while (prev != null && prev != par) prev = prev.getPrevSibling
      prev == null
    }
    findChild(classOf[ScImportStmt]) match {
      case Some(x) => {
        if (checkReference(x)) return Some(x)
        else return None
      }
      case None => return None
    }
  }

  def addImportForClass(clazz: PsiClass): Unit = addImportForClass(clazz, null)
  def addImportForClass(clazz: PsiClass, ref: PsiElement) {
    //Create simple variant what to import
    var importSt = ScalaPsiElementFactory.createImportStatementFromClass(this, clazz, this.getManager)
    //Getting qualifier resolve, to compare with other import expressions
    val resolve = ScalaPsiElementFactory.getResolveForClassQualifier(this, clazz, getManager)
    this match {
      //If we have not Block we tring to collect same qualifiers to one import expression
      case _: ScalaFile | _: ScPackaging => {
        //looking for expresssions which collecting
        val sameExpressions: Array[ScImportExpr] = (for (importStmt <- importStatementsInHeader; importExpr <- importStmt.importExprs
          if resolve != null && importExpr.qualifier.resolve == resolve)
          yield importExpr).toArray
        if (sameExpressions.length != 0) {
          //getting collected import statement
          val stmt = ScalaPsiElementFactory.createBigImportStmt(importSt.importExprs(0), sameExpressions, this.getManager)
          //deleting collected expressions
          for (expr <- sameExpressions) expr.deleteExpr
          importSt = stmt
        }
      }
      case _ =>
    }

    //looking for td import statemnt to find place which we will use for new import statement
    findFirstImportStmt(ref) match {
      case Some(x: ScImportStmt) => {
        //now we walking throw foward siblings, and seeking appropriate place (lexicografical)
        var stmt: PsiElement = x
        //this is flag to stop walking when we add import before more big lexicografically import statement
        var added = false
        while (!added && stmt != null && (stmt.isInstanceOf[ScImportStmt]
            || stmt.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
            || stmt.getNode.getElementType == ScalaTokenTypes.tSEMICOLON)) {
          stmt match {
            case im: ScImportStmt => {
              if (importSt.getText.toLowerCase < im.getText.toLowerCase) {
                added = true
                addBeforeI(importSt, im)
                addBeforeI(ScalaPsiElementFactory.createNewLineNode(im.getManager).getPsi, im)
              }
            }
            case _ =>
          }
          stmt = stmt.getNextSibling
        }
        //if our stmt is the biggest lexicografically import statement we add this to the end
        if (!added) {
          if (stmt != null) {
            while (!stmt.isInstanceOf[ScImportStmt]) stmt = stmt.getPrevSibling
            addAfterI(importSt, stmt)
            addAfterI(ScalaPsiElementFactory.createNewLineNode(stmt.getManager).getPsi, stmt)
          }
          else {
            addAfterI(importSt, getLastChild)
            addAfterI(ScalaPsiElementFactory.createNewLineNode(getLastChild.getManager).getPsi, getLastChild)
          }
        }
      }
      case _ => {
        //we haven't td import statement, so we insert new import statement so close to element begin as possible
        findChild(classOf[ScPackageStatement]) match {
          case Some(x) => {
            addAfterI(ScalaPsiElementFactory.createNewLineNode(x.getManager).getPsi, addAfterI(importSt, x))
          }
          case None => {
            //Here we must to find left brace, if not => it's ScalaFile
            getNode.findChildByType(ScalaTokenTypes.tLBRACE) match {
              case null => {
                if (getFirstChild != null) addBeforeI(importSt, getFirstChild)
                else addI(importSt)
              }
              case node => {
                addAfterI(importSt, addAfterI(ScalaPsiElementFactory.createNewLineNode(importSt.getManager).getPsi, node.getPsi))
              }
            }

          }
        }
      }
    }
  }


  def addI(element: PsiElement): PsiElement = {
    getNode.addChild(element.getNode)
    return getNode.getLastChildNode.getPsi
  }

  def addBeforeI(element: PsiElement, anchor: PsiElement): PsiElement = {
    getNode.addChild(element.getNode, anchor.getNode)
    return anchor.getNode.getTreePrev.getPsi
  }

  def addAfterI(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor.getNode == getNode.getLastChildNode) return addI(element)
    addBeforeI(element, anchor.getNode.getTreeNext.getPsi)
  }

  def deleteImportStmt(stmt: ScImportStmt): Unit = {
    val remove = getNode.removeChild _
    val node = stmt.getNode
    val next = node.getTreeNext
    if (next == null) {
      remove(node)
    }
    else if (next.getElementType == ScalaTokenTypes.tLINE_TERMINATOR) {
      remove(next)
      remove(node)
    } else if (next.getElementType == ScalaTokenTypes.tSEMICOLON) {
      val nextnext = next.getTreeNext
      if (nextnext == null) {
        remove(next)
        remove(node)
      }
      else if (next.getElementType == ScalaTokenTypes.tLINE_TERMINATOR) {
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
}