package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import api.base.ScReferenceElement
import api.toplevel.imports.usages.{ImportSelectorUsed, ImportExprUsed, ImportWildcardSelectorUsed, ImportUsed}
import collection.mutable.{HashSet, ArrayBuffer}
import com.intellij.codeInsight.hint.HintManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import formatting.settings.ScalaCodeStyleSettings
import lang.resolve.{ResolveProcessor, CompletionProcessor, ScalaResolveResult, StdKinds}
import lexer.ScalaTokenTypes
import impl.ScalaPsiElementFactory
import api.toplevel.imports.{ScImportExpr, ScImportStmt}
import api.toplevel.packaging.ScPackaging
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._
import psi.impl.toplevel.synthetic.ScSyntheticPackage
import collection.mutable.Set
import refactoring.util.ScalaNamesUtil
import scope._

trait ScImportsHolder extends ScalaPsiElement {

  def getImportStatements: Seq[ScImportStmt] = Seq(findChildrenByClass(classOf[ScImportStmt]): _*)

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    if (lastParent != null) {
      var run = ScalaPsiUtil.getPrevStubOrPsiElement(lastParent)
      while (run != null) {
        if (run.isInstanceOf[ScImportStmt] &&
            !run.processDeclarations(processor, state, lastParent, place)) return false
        run = ScalaPsiUtil.getPrevStubOrPsiElement(run)
      }
    }
    true
  }

  def getAllImportUsed: Set[ImportUsed] = {
    val res: Set[ImportUsed] = new HashSet[ImportUsed]
    def processChild(element: PsiElement) {
      for (child <- element.getChildren) {
        child match {
          case imp: ScImportExpr => {
            if (/*!imp.singleWildcard && */imp.selectorSet == None) {
              res += ImportExprUsed(imp)
            }
            else if (imp.singleWildcard) {
              res += ImportWildcardSelectorUsed(imp)
            }
            for (selector <- imp.selectors) {
              res += ImportSelectorUsed(selector)
            }
          }
          case _ => processChild(child)
        }
      }
    }
    processChild(this)
    return res
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
    ref match {
      case ref: ScReferenceElement => {
        if (ref.resolve == clazz) {
          return
        }
      }
      case _ =>
    }
    val selectors = new ArrayBuffer[String]

    val qualifiedName = clazz.getQualifiedName
    val index = qualifiedName.lastIndexOf('.')
    if (index == -1) return  //cannot import anything
    var classPackageQualifier = qualifiedName.substring(0, index)

    //collecting selectors to add into new import statement
    for (imp <- importStatementsInHeader) {
      for (expr: ScImportExpr <- imp.importExprs) {
        val qual = expr.qualifier
        if (qual != null) { //in case "import scala" it can be null
          val qn = qual.resolve match {
            case pack: PsiPackage => pack.getQualifiedName
            case clazz: PsiClass => clazz.getQualifiedName
            case _ => ""
          }
          if (qn == classPackageQualifier) {
            selectors ++= expr.getNames
            expr.deleteExpr
          }
        }
      }
    }

    def getSplitQualifierElement(s: String) = {
      val index = s.lastIndexOf('.')
      if (index == -1) ("", s)
      else (s.substring(0, index), s.substring(index + 1))
    }

    //creating selectors string (after last '.' in import expression)
    var isPlaceHolderImport = false
    clazz.getName +: selectors
    if (selectors.exists(_ == "_") ||
            selectors.length >= CodeStyleSettingsManager.getSettings(getProject).
            getCustomSettings(classOf[ScalaCodeStyleSettings]).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) {
      selectors.clear
      selectors += "_"
      isPlaceHolderImport = true
    }
    var importString =
      if (selectors.length == 1) selectors(0)
      else {
        selectors.mkString("{", ", ", "}")
      }


    val completionProcessor = new CompletionProcessor(StdKinds.packageRef)
    this.processDeclarations(completionProcessor, ResolveState.initial, getLastChild, getLastChild)
    val packages = completionProcessor.candidates.map((result: ScalaResolveResult) => result match {
      case ScalaResolveResult(pack: PsiPackage, _) => pack.getQualifiedName
      case _ => ""
    })

    var importSt: ScImportStmt = null
    while (importSt == null) {
      val (pre, last) = getSplitQualifierElement(classPackageQualifier)
      if (ScalaNamesUtil.isKeyword(last)) importString = "`" + last + "`" + "." + importString
      else importString = last + "." + importString
      if (packages.contains(classPackageQualifier)) {
        importSt = ScalaPsiElementFactory.createImportFromText("import " + importString, getManager)
      } else {
        classPackageQualifier = pre
        if (classPackageQualifier == "") {
          importString = "_root_." + importString
          importSt = ScalaPsiElementFactory.createImportFromText("import " + importString, getManager)
        }
      }
    }

    //cheek all imports under new import to fix problems
    if (isPlaceHolderImport) {
      val syntheticPackage = ScSyntheticPackage.get(getSplitQualifierElement(qualifiedName)._1, getProject)

      val subPackages = if (syntheticPackage != null)
        syntheticPackage.getSubPackages
      else {
        val psiPack = JavaPsiFacade.getInstance(getProject).findPackage(getSplitQualifierElement(qualifiedName)._1)
        if (psiPack != null) psiPack.getSubPackages
        else Array[PsiPackage]()
      }
      def checkImports(element: PsiElement) {
        element match {
          case expr: ScImportExpr => {
            def iterateExpr {
              val qualifier = expr.qualifier
              var firstQualifier = qualifier
              if (firstQualifier.getText == "_root_") return
              while (firstQualifier.qualifier != None) firstQualifier = firstQualifier.qualifier match {case Some(e) => e}
              if (subPackages.map(_.getName).contains(firstQualifier.getText)) {
                var classPackageQual = getSplitQualifierElement(firstQualifier.resolve match {
                  case pack: PsiPackage => pack.getQualifiedName
                  case cl: PsiClass => cl.getQualifiedName
                  case _ => return
                })._1
                var importString = qualifier.getText
                var break = true
                while (break) {
                  val (pre, last) = getSplitQualifierElement(classPackageQual)
                  if (last != "") importString = last + "." + importString
                  if (packages.contains(classPackageQual)) {
                    break = false
                  } else {
                    classPackageQual = pre
                    if (classPackageQual == "") {
                      importString = "_root_." + importString
                      break = false
                    }
                  }
                }
                val newQualifier = ScalaPsiElementFactory.createReferenceFromText(importString, getManager)
                qualifier.replace(newQualifier)
                iterateExpr
              }
            }
            iterateExpr
          }
          case _ => for (child <- element.getChildren) checkImports(child)
        }
      }
      if (subPackages.length > 0) {
        checkImports(this)
      }
    }

    //looking for td import statement to find place which we will use for new import statement
    findFirstImportStmt(ref) match {
      case Some(x: ScImportStmt) => {
        //now we walking throw forward siblings, and seeking appropriate place (lexicographical)
        var stmt: PsiElement = x
        //this is flag to stop walking when we add import before more big lexicographically import statement
        var added = false
        while (!added && stmt != null && (stmt.isInstanceOf[ScImportStmt]
            || stmt.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
            || stmt.getNode.getElementType == ScalaTokenTypes.tSEMICOLON)) {
          stmt match {
            case im: ScImportStmt => {
              def processPackage(elem: PsiElement): Boolean = {
                if (classPackageQualifier == "") return true
                val completionProcessor = new ResolveProcessor(StdKinds.packageRef, getSplitQualifierElement(classPackageQualifier)._2)
                elem.getContainingFile.processDeclarations(completionProcessor, ResolveState.initial, elem, elem)
                completionProcessor.candidates.length > 0
              }
              if (importSt.getText.toLowerCase < im.getText.toLowerCase && processPackage(im)) {
                added = true
                addImportBefore(importSt, im)
              }
            }
            case _ =>
          }
          stmt = stmt.getNextSibling
        }
        //if our stmt is the biggest lexicographically import statement we add this to the end
        if (!added) {
          if (stmt != null) {
            while (!stmt.isInstanceOf[ScImportStmt]) stmt = stmt.getPrevSibling
            addImportAfter(importSt, stmt)
          }
          else {
            addImportAfter(importSt, getLastChild)
          }
        }
      }
      case _ => {
        def updateFirst {
          val first = getFirstChild
          if (first != null) {
            /*val manager = first.getManager
            if (first.getText.trim.length > 0) {
              val n2 = ScalaPsiElementFactory.createNewLineNode(manager, "\n\n").getPsi
              addBefore(importSt, first)
              addBefore(n2, first)
            }
            else */addBefore(importSt, first)
          }
          else addImport(importSt)
        }

        getNode.findChildByType(ScalaTokenTypes.tLBRACE) match {
          case null if this.isInstanceOf[ScalaFile] => {
            updateFirst
          }
          case null => {
            val x = getNode.findChildByType(ScalaElementTypes.REFERENCE).getPsi
            if (x != null) {
              val next = x.getNextSibling
              /*if (next != null && !next.getText.contains("\n")) {
                val nl = ScalaPsiElementFactory.createNewLineNode(x.getManager, "\n\n").getPsi
                addImportBefore(importSt, next)
                addImportBefore(nl, next)
              }
              else {*/
                //unnecessary nl will be removed by formatter
//                val nl1 = ScalaPsiElementFactory.createNewLineNode(x.getManager, "\n\n").getPsi
//                val nl2 = ScalaPsiElementFactory.createNewLineNode(x.getManager, "\n\n").getPsi
//                addImportAfter(nl1, x)
                addImportAfter(importSt, x)
//                addImportAfter(nl2, importSt)
//              }
            } else {
              updateFirst
            }
          }
          case node => {
//            val manager = node.getPsi.getManager
//            val n1 = ScalaPsiElementFactory.createNewLineNode(manager, "\n").getPsi
//            val n2 = ScalaPsiElementFactory.createNewLineNode(manager, "\n").getPsi
//            addImportAfter(n1, node.getPsi)
            addImportAfter(importSt, node.getPsi)
//            addImportAfter(n2, importSt)
          }
        }
      }
    }
    HintManager.getInstance.hideAllHints
  }


  def addImport(element: PsiElement): PsiElement = {
    CodeEditUtil.addChildren(getNode, element.getNode, element.getNode, null)
    return getNode.getLastChildNode.getPsi
  }

  def addImportBefore(element: PsiElement, anchor: PsiElement): PsiElement = {
    CodeEditUtil.addChildren(getNode, element.getNode, element.getNode, anchor.getNode)
    return anchor.getNode.getTreePrev.getPsi
  }

  def addImportAfter(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor.getNode == getNode.getLastChildNode) return addImport(element)
    addImportBefore(element, anchor.getNode.getTreeNext.getPsi)
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
    } else if (next.getPsi.isInstanceOf[PsiWhiteSpace]) {
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