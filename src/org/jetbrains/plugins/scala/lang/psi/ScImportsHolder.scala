package org.jetbrains.plugins.scala
package lang
package psi

import api.toplevel.templates.ScTemplateBody
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import impl.{ScPackageImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import api.base.ScReferenceElement
import api.toplevel.imports.usages.{ImportSelectorUsed, ImportExprUsed, ImportWildcardSelectorUsed, ImportUsed}
import collection.mutable.{HashSet, ArrayBuffer}
import com.intellij.codeInsight.hint.HintManager
import lexer.ScalaTokenTypes
import api.toplevel.imports.{ScImportExpr, ScImportStmt}
import api.toplevel.packaging.ScPackaging
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._
import psi.impl.toplevel.synthetic.ScSyntheticPackage
import collection.mutable.Set
import refactoring.util.ScalaNamesUtil
import scope._
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.processor.{CompletionProcessor, ResolveProcessor}
import lang.resolve.{ScalaResolveResult, StdKinds}
import com.intellij.psi.util.PsiTreeUtil

trait ScImportsHolder extends ScalaPsiElement {

  def getImportStatements: Seq[ScImportStmt] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScImportStmt]).toSeq: _*)

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    if (lastParent != null) {
      var run = ScalaPsiUtil.getPrevStubOrPsiElement(lastParent)
      while (run != null) {
        ProgressManager.checkCanceled()
        if (run.isInstanceOf[ScImportStmt] &&
            !run.processDeclarations(processor, state, lastParent, place)) return false
        run = ScalaPsiUtil.getPrevStubOrPsiElement(run)
      }
    }
    true
  }

  def getImportsForLastParent(lastParent: PsiElement): Seq[ScImportStmt] = {
    val buffer: ArrayBuffer[ScImportStmt] = new ArrayBuffer[ScImportStmt]()
    if (lastParent != null) {
      var run = ScalaPsiUtil.getPrevStubOrPsiElement(lastParent)
      while (run != null) {
        ProgressManager.checkCanceled()
        if (run.isInstanceOf[ScImportStmt]) buffer += run.asInstanceOf[ScImportStmt]
        run = ScalaPsiUtil.getPrevStubOrPsiElement(run)
      }
    }
    buffer.toSeq
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
    res
  }


  def importStatementsInHeader: Seq[ScImportStmt] = {
    val buf = new ArrayBuffer[ScImportStmt]
    for (child <- getChildren) {
      child match {
        case x: ScImportStmt => buf += x
        case p: ScPackaging if !p.isExplicit && buf.length == 0 => return p.importStatementsInHeader
        case _: ScTypeDefinition | _: ScPackaging => return buf.toSeq
        case _ =>
      }
    }
    buf.toSeq
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
        if (checkReference(x)) Some(x)
        else None
      }
      case None => None
    }
  }

  def addImportForClass(clazz: PsiClass, ref: PsiElement = null) {
    ref match {
      case ref: ScReferenceElement => {
        if (!ref.isValid || ref.isReferenceTo(clazz)) return
      }
      case _ =>
    }
    addImportForPath(clazz.getQualifiedName, ref)
  }

  def addImportForPsiNamedElement(elem: PsiNamedElement, ref: PsiElement) {
    ScalaPsiUtil.nameContext(elem) match {
      case memb: PsiMember =>
        val containingClass = memb.getContainingClass
        if (containingClass != null && containingClass.getQualifiedName != null) {
          ref match {
            case ref: ScReferenceElement =>
              if (!ref.isValid || ref.isReferenceTo(elem)) return
            case _ =>
          }
          val qual = Seq(containingClass.getQualifiedName, elem.getName).filter(_ != "").mkString(".")
          addImportForPath(qual)
        }
      case _ =>
    }
  }

  def addImportForPath(path: String, ref: PsiElement = null) {
    val selectors = new ArrayBuffer[String]

    val qualifiedName = path
    val index = qualifiedName.lastIndexOf('.')
    if (index == -1) return  //cannot import anything
    var classPackageQualifier = qualifiedName.substring(0, index)
    var hasRenamedImport = false

    //collecting selectors to add into new import statement
    for (imp <- importStatementsInHeader) {
      for (expr: ScImportExpr <- imp.importExprs) {
        val qualifier = expr.qualifier
        if (qualifier != null) { //in case "import scala" it can be null
          val qn = qualifier.resolve() match {
            case pack: PsiPackage => pack.getQualifiedName
            case clazz: PsiClass => clazz.getQualifiedName
            case _ => ""
          }
          if (qn == classPackageQualifier) {
            hasRenamedImport ||= expr.selectors.exists(s => s.reference.refName != s.importedName)
            selectors ++= expr.getNames
            expr.deleteExpr()
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
    val simpleName = path.substring(path.lastIndexOf('.') + 1)
    simpleName +=: selectors
    
    if (!hasRenamedImport &&
            (selectors.exists(_ == "_") || selectors.length >= ScalaPsiUtil.getSettings(getProject).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND)) {
      selectors.clear()
      selectors += "_"
      isPlaceHolderImport = true
    }

    var importString = selectors.distinct match {
      case Seq(s) => s
      case ss => ss.mkString("{", ", ", "}")
    }

    val completionProcessor = new CompletionProcessor(StdKinds.packageRef)
    val place = getLastChild
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      place match {
        case null =>
        case p => {
          if (!p.processDeclarations(completionProcessor, ResolveState.initial, lastParent, place)) return
          treeWalkUp(place.getContext, place)
        }
      }
    }
    treeWalkUp(this, place)
    val names: HashSet[String] = new HashSet
    val packs: ArrayBuffer[PsiPackage] = new ArrayBuffer
    for (candidate <- completionProcessor.candidatesS) {
      candidate match {
        case ScalaResolveResult(pack: PsiPackage, _) => {
          if (names.contains(pack.getName)) {
            var index = packs.findIndexOf(_.getName == pack.getName)
            while(index != -1) {
              packs.remove(index)
              index = packs.findIndexOf(_.getName == pack.getName)
            }
          } else {
            names += pack.getName
            packs += pack
          }
        }
        case _ =>
      }

    }
    val packages = packs.map(_.getQualifiedName)
    val packagesName = packs.map(_.getName)

    var importSt: ScImportStmt = null

    while (importSt == null) {
      val (pre, last) = getSplitQualifierElement(classPackageQualifier)
      if (ScalaNamesUtil.isKeyword(last)) importString = "`" + last + "`" + "." + importString
      else importString = last + "." + importString
      if ((!ScalaPsiUtil.getSettings(getProject).ADD_FULL_QUALIFIED_IMPORTS ||
              classPackageQualifier.indexOf(".") == -1) &&
              packages.contains(classPackageQualifier)) {
        importSt = ScalaPsiElementFactory.createImportFromText("import " + importString, getManager)
      } else {
        if (pre == "") {
          if (ScSyntheticPackage.get(classPackageQualifier, getProject) == null ||
            packagesName.contains(classPackageQualifier))
            importString = "_root_." + importString
          importSt = ScalaPsiElementFactory.createImportFromText("import " + importString, getManager)
        }
        classPackageQualifier = pre
      }
    }

    //cheek all imports under new import to fix problems
    if (isPlaceHolderImport) {
      val syntheticPackage = ScSyntheticPackage.get(getSplitQualifierElement(qualifiedName)._1, getProject)

      val subPackages = if (syntheticPackage != null)
        syntheticPackage.getSubPackages
      else {
        val psiPack = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(getSplitQualifierElement(qualifiedName)._1))
        if (psiPack != null) psiPack.getSubPackages
        else Array[PsiPackage]()
      }
      def checkImports(element: PsiElement) {
        element match {
          case expr: ScImportExpr => {
            def iterateExpr() {
              val qualifier = expr.qualifier
              var firstQualifier = qualifier
              if (firstQualifier.getText == "_root_") return
              while (firstQualifier.qualifier != None) firstQualifier = firstQualifier.qualifier.get
              if (subPackages.map(_.getName).contains(firstQualifier.getText)) {
                var classPackageQualifier = getSplitQualifierElement(firstQualifier.resolve() match {
                  case pack: PsiPackage => pack.getQualifiedName
                  case cl: PsiClass => cl.getQualifiedName
                  case _ => return
                })._1
                var importString = qualifier.getText
                var break = true
                while (break) {
                  val (pre, last) = getSplitQualifierElement(classPackageQualifier)
                  if (last != "") importString = last + "." + importString
                  if (packages.contains(classPackageQualifier)) {
                    break = false
                  } else {
                    classPackageQualifier = pre
                    if (classPackageQualifier == "") {
                      importString = "_root_." + importString
                      break = false
                    }
                  }
                }
                val newQualifier = ScalaPsiElementFactory.createReferenceFromText(importString, getManager)
                qualifier.replace(newQualifier)
                iterateExpr()
              }
            }
            iterateExpr()
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
            || stmt.isInstanceOf[PsiWhiteSpace]
            || stmt.getNode.getElementType == ScalaTokenTypes.tSEMICOLON)) {
          stmt match {
            case im: ScImportStmt => {
              def processPackage(elem: PsiElement): Boolean = {
                if (classPackageQualifier == "") return true
                val completionProcessor = new ResolveProcessor(StdKinds.packageRef, elem,
                  getSplitQualifierElement(classPackageQualifier)._2)
                this.processDeclarations(completionProcessor, ResolveState.initial, elem, elem)
                completionProcessor.candidatesS.size > 0
              }
              val nextImportContainsRef =
                if (ref != null) PsiTreeUtil.isAncestor(im, ref, false) // See SCL-2925
                else false
              val cond2 = importSt.getText.toLowerCase < im.getText.toLowerCase && processPackage(im)
              if (nextImportContainsRef || cond2) {
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
        def updateFirst() {
          val first = getFirstChild
          if (first != null) {
            addBefore(importSt, first)
          }
          else addImport(importSt)
        }

        getNode.findChildByType(ScalaTokenTypes.tLBRACE) match {
          case null if this.isInstanceOf[ScalaFile] => {
            updateFirst()
          }
          case null => {
            val reference = getNode.findChildByType(ScalaElementTypes.REFERENCE)
            if (reference != null) {
              reference.getPsi.getNextSibling
                addImportAfter(importSt, reference.getPsi)
            } else {
              updateFirst()
            }
          }
          case node => {
            this match {
              case tb: ScTemplateBody => tb.selfTypeElement match {
                case Some(te) => addImportAfter(importSt, te)
                case _ =>
                  addImportAfter(importSt, node.getPsi)
              }
              case _ =>
                addImportAfter(importSt, node.getPsi)
            }
          }
        }
      }
    }
    HintManager.getInstance.hideAllHints()
  }


  def addImport(element: PsiElement): PsiElement = {
    CodeEditUtil.addChildren(getNode, element.getNode, element.getNode, null).getPsi
  }

  def addImportBefore(element: PsiElement, anchor: PsiElement): PsiElement = {
    val anchorNode = anchor.getNode
    CodeEditUtil.addChildren(getNode, element.getNode, element.getNode, anchorNode).getPsi
  }

  def addImportAfter(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor.getNode == getNode.getLastChildNode) return addImport(element)
    addImportBefore(element, anchor.getNode.getTreeNext.getPsi)
  }

  def deleteImportStmt(stmt: ScImportStmt) {
    val remove = getNode.removeChild _
    val node = stmt.getNode
    val next = node.getTreeNext
    if (next == null) {
      remove(node)
    } else if (next.getPsi.isInstanceOf[PsiWhiteSpace]) {
      if (next.getText.count(_ == '\n') < 2)
        remove(next)
      else {
        val nl = ScalaPsiElementFactory.createNewLine(getManager, next.getText.replaceFirst("[\n]", ""))
        getNode.replaceChild(next, nl.getNode)
      }
      remove(node)
    } else if (next.getElementType == ScalaTokenTypes.tSEMICOLON) {
      val nextnext = next.getTreeNext
      if (nextnext == null) {
        remove(next)
        remove(node)
      }
      else if (next.isInstanceOf[PsiWhiteSpace] && next.getText.contains("\n")) {
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
