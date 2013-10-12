package org.jetbrains.plugins.scala
package lang
package psi

import api.base.ScReferenceElement
import api.statements.ScTypeAliasDefinition
import api.toplevel.imports.{ScImportSelector, ScImportSelectors, ScImportExpr, ScImportStmt}
import api.toplevel.templates.ScTemplateBody
import api.{ScalaRecursiveElementVisitor, ScalaFile}
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import impl.{ScPackageImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import api.toplevel.imports.usages.{ImportSelectorUsed, ImportExprUsed, ImportWildcardSelectorUsed, ImportUsed}
import com.intellij.codeInsight.hint.HintManager
import lexer.ScalaTokenTypes
import api.toplevel.packaging.ScPackaging
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._
import psi.impl.toplevel.synthetic.ScSyntheticPackage
import refactoring.util.ScalaNamesUtil
import scope._
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.processor.{CompletionProcessor, ResolveProcessor}
import com.intellij.psi.util.PsiTreeUtil
import java.lang.ThreadLocal
import com.intellij.openapi.util.{Trinity, RecursionManager}
import types.result.TypingContext
import types.ScDesignatorType
import extensions.{toPsiMemberExt, toPsiNamedElementExt, toPsiClassExt}
import settings._
import lang.resolve.{ScalaResolveResult, StdKinds}
import annotation.tailrec
import collection.mutable.ArrayBuffer
import com.intellij.psi.stubs.StubElement
import collection.mutable

trait ScImportsHolder extends ScalaPsiElement {

  def getImportStatements: Seq[ScImportStmt] = {
    this match {
      case s: ScalaStubBasedElementImpl[_] =>
        val stub: StubElement[_] = s.getStub
        if (stub != null) {
          return stub.getChildrenByType(ScalaElementTypes.IMPORT_STMT, JavaArrayFactoryUtil.ScImportStmtFactory).toSeq
        }
      case _ =>
    }
    collection.immutable.Seq(findChildrenByClassScala(classOf[ScImportStmt]).toSeq: _*)
  }
  
  @volatile
  private var modCount: Long = 0L

  private val updating: ThreadLocal[Boolean] = new ThreadLocal[Boolean]() {
    override def initialValue(): Boolean = false
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    if (lastParent != null) {
      var run = ScalaPsiUtil.getPrevStubOrPsiElement(lastParent)
//      updateResolveCaches()
      while (run != null) {
        ProgressManager.checkCanceled()
        if (run.isInstanceOf[ScImportStmt] &&
            !run.processDeclarations(processor, state, lastParent, place)) return false
        run = ScalaPsiUtil.getPrevStubOrPsiElement(run)
      }
    }
    true
  }

  /**
   * This method is important to avoid SOE for long import lists.
   */
  //todo it became unused >1year ago
  private def updateResolveCaches() {
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (curModCount == modCount || updating.get()) return
    var updateModCount = true
    updating.set(true)
    try {
      var child = ScalaPsiUtil.getFirstStubOrPsiElement(this)
      while (child != null && updateModCount) {
        child match {
          case i: ScImportStmt =>
            for (expr <- i.importExprs) {
              expr.reference match {
                case Some(ref) =>
                  var qual = ref
                  while (qual.qualifier != None) {
                    qual = qual.qualifier.get
                  }
                  if (updateModCount && !ScImportsHolder.resolveCache.currentStack().contains(Trinity.create(ref, false, true)) &&
                    !ScImportsHolder.resolveCache.currentStack().contains(Trinity.create(qual, false, true))) {
                    ref.multiResolve(false) //fill resolve cache to avoid SOE
                  } else updateModCount = false
                case _ =>
              }
            }
          case _ =>
        }
        child = ScalaPsiUtil.getNextStubOrPsiElement(child)
      }
    } finally updating.set(false)
    if (updateModCount)
      modCount = curModCount
  }

  def getImportsForLastParent(lastParent: PsiElement): Seq[ScImportStmt] = {
    val buffer: ArrayBuffer[ScImportStmt] = new ArrayBuffer[ScImportStmt]()
    if (lastParent != null) {
      var run = ScalaPsiUtil.getPrevStubOrPsiElement(lastParent)
      while (run != null) {
        ProgressManager.checkCanceled()
        run match {
          case importStmt: ScImportStmt => buffer += importStmt
          case _ =>
        }
        run = ScalaPsiUtil.getPrevStubOrPsiElement(run)
      }
    }
    buffer.toSeq
  }

  def getAllImportUsed: mutable.Set[ImportUsed] = {
    val res: mutable.Set[ImportUsed] = new mutable.HashSet[ImportUsed]
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
        ref.bind() match {
          case Some(ScalaResolveResult(t: ScTypeAliasDefinition, subst)) if t.typeParameters.isEmpty =>
            for (tp <- t.aliasedType(TypingContext.empty)) {
              tp match {
                case ScDesignatorType(c: PsiClass) if c == clazz => return
                case _ =>
              }
            }
          case _ =>
        }
      }
      case _ =>
    }
    addImportForPath(clazz.qualifiedName, ref)
  }

  def addImportForPsiNamedElement(elem: PsiNamedElement, ref: PsiElement, cClass: Option[PsiClass] = None) {
    ScalaPsiUtil.nameContext(elem) match {
      case memb: PsiMember =>
        val containingClass = cClass.getOrElse(memb.containingClass)
        if (containingClass != null && containingClass.qualifiedName != null) {
          ref match {
            case ref: ScReferenceElement =>
              if (!ref.isValid || ref.isReferenceTo(elem)) return
            case _ =>
          }
          val qual = Seq(containingClass.qualifiedName, elem.name).filter(_ != "").mkString(".")
          addImportForPath(qual)
        }
      case _ =>
    }
  }

  def addImportForPath(path: String, ref: PsiElement = null, explicitly: Boolean = false) {
    val selectors = new ArrayBuffer[String]
    val renamedSelectors = new ArrayBuffer[String]()

    val qualifiedName = path
    val index = qualifiedName.lastIndexOf('.')
    if (index == -1) return  //cannot import anything
    var classPackageQualifier = qualifiedName.substring(0, index)

    //collecting selectors to add into new import statement
    var firstPossibleGoodPlace: Option[ScImportExpr] = None
    val toDelete: ArrayBuffer[ScImportExpr] = new ArrayBuffer[ScImportExpr]()
    for (imp <- importStatementsInHeader if !explicitly) {
      for (expr: ScImportExpr <- imp.importExprs) {
        val qualifier = expr.qualifier
        if (qualifier != null) { //in case "import scala" it can be null
          val qn = qualifier.resolve() match {
            case pack: PsiPackage => pack.getQualifiedName
            case clazz: PsiClass => clazz.qualifiedName
            case _ => ""
          }
          if (qn == classPackageQualifier) {
            expr.getLastChild match {
              case s: ScImportSelectors =>
                for (selector <- s.selectors) {
                  if (selector.importedName != selector.reference.refName) {
                    renamedSelectors += selector.getText
                  } else {
                    selectors += selector.getText
                  }
                }
                if (s.hasWildcard) selectors += "_"
              case _ => selectors ++= expr.getNames
            }
            firstPossibleGoodPlace match {
              case Some(_) =>
                toDelete += expr
              case _ =>
                firstPossibleGoodPlace = Some(expr)
            }
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

    val wildcardImport: Boolean = selectors.exists(_ == "_") ||
      selectors.length >= ScalaProjectSettings.getInstance(getProject).getClassCountToUseImportOnDemand
    if (wildcardImport) {
      selectors.clear()
      selectors += "_"
      isPlaceHolderImport = true
    }

    val place = getLastChild

    @tailrec
    def treeWalkUp(completionProcessor: CompletionProcessor, p: PsiElement, lastParent: PsiElement) {
      p match {
        case null =>
        case _ => {
          if (!p.processDeclarations(completionProcessor, ResolveState.initial, lastParent, place)) return
          treeWalkUp(completionProcessor, p.getContext, p)
        }
      }
    }

    var everythingProcessor  = new CompletionProcessor(StdKinds.stableImportSelector, place, includePrefixImports = false)
    treeWalkUp(everythingProcessor, this, place)
    val candidatesBefore: mutable.HashMap[String, collection.immutable.HashSet[PsiNamedElement]] = new mutable.HashMap
    for (candidate <- everythingProcessor.candidates) {
      val set: collection.immutable.HashSet[PsiNamedElement] =
        candidatesBefore.getOrElse(candidate.name, collection.immutable.HashSet.empty[PsiNamedElement])
      candidatesBefore.update(candidate.name, set + candidate.getElement)
    }
    val usedNames = new mutable.HashSet[String]()

    this.accept(new ScalaRecursiveElementVisitor {
      override def visitReference(reference: ScReferenceElement) {
        if (reference == ref) {
          super.visitReference(reference)
          return
        }
        reference.qualifier match {
          case None if !reference.getContext.isInstanceOf[ScImportSelector] => usedNames += reference.refName
          case _ =>
        }
        super.visitReference(reference)
      }
    })

    for (expr <- toDelete) {
      expr.deleteExpr()
    }


    var importString = (renamedSelectors ++ selectors).distinct match {
      case Seq(s) => s
      case ss => ss.mkString("{", ", ", "}")
    }

    val completionProcessor = new CompletionProcessor(StdKinds.packageRef, place, includePrefixImports = false)
    treeWalkUp(completionProcessor, this, place)
    val names: mutable.HashSet[String] = new mutable.HashSet
    val packs: ArrayBuffer[PsiPackage] = new ArrayBuffer
    for (candidate <- completionProcessor.candidatesS) {
      candidate match {
        case ScalaResolveResult(pack: PsiPackage, _) => {
          if (names.contains(pack.name)) {
            var index = packs.indexWhere(_.name == pack.name)
            while(index != -1) {
              packs.remove(index)
              index = packs.indexWhere(_.name == pack.name)
            }
          } else {
            names += pack.name
            packs += pack
          }
        }
        case _ =>
      }

    }
    val packages = packs.map(_.getQualifiedName)
    val packagesName = packs.map(_.name)

    var importSt: ScImportStmt = null

    while (importSt == null) {
      val (pre, last) = getSplitQualifierElement(classPackageQualifier)
      if (ScalaNamesUtil.isKeyword(last)) importString = "`" + last + "`" + "." + importString
      else importString = last + "." + importString
      if ((!ScalaProjectSettings.getInstance(getProject).isAddFullQualifiedImports ||
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
        syntheticPackage.getSubPackages(getResolveScope)
      else {
        val psiPack = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(getSplitQualifierElement(qualifiedName)._1))
        if (psiPack != null) psiPack.getSubPackages(getResolveScope)
        else Array[PsiPackage]()
      }
      def checkImports(element: PsiElement) {
        element match {
          case expr: ScImportExpr => {
            def iterateExpr() {
              val qualifier = expr.qualifier
              var firstQualifier = qualifier
              if (firstQualifier == null || firstQualifier.getText == "_root_") return
              while (firstQualifier.qualifier != None) firstQualifier = firstQualifier.qualifier.get
              if (subPackages.map(_.name).contains(firstQualifier.getText)) {
                var classPackageQualifier = getSplitQualifierElement(firstQualifier.resolve() match {
                  case pack: PsiPackage => pack.getQualifiedName
                  case cl: PsiClass => cl.qualifiedName
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

    def tail() {
      if (!explicitly) {
        everythingProcessor = new CompletionProcessor(StdKinds.stableImportSelector, getLastChild, includePrefixImports = false)
        treeWalkUp(everythingProcessor, this, getLastChild)
        val candidatesAfter: mutable.HashMap[String, collection.immutable.HashSet[PsiNamedElement]] = new mutable.HashMap
        for (candidate <- everythingProcessor.candidates) {
          val set: collection.immutable.HashSet[PsiNamedElement] =
            candidatesAfter.getOrElse(candidate.name, collection.immutable.HashSet.empty[PsiNamedElement])
          candidatesAfter.update(candidate.name, set + candidate.getElement)
        }

        def checkName(s: String) {
          if (candidatesBefore.get(s) != candidatesAfter.get(s)) {
            val pathes = new mutable.HashSet[String]()
            //let's try to fix it by adding all before imports explicitly
            candidatesBefore.get(s).getOrElse(collection.immutable.HashSet.empty[PsiNamedElement]).foreach {
              case c: PsiClass => pathes += c.qualifiedName
              case c: PsiNamedElement =>
                ScalaPsiUtil.nameContext(c) match {
                  case memb: PsiMember =>
                    val containingClass = memb.containingClass
                    if (containingClass != null && containingClass.qualifiedName != null) {
                      val qual = Seq(containingClass.qualifiedName, c.name).filter(_ != "").mkString(".")
                      pathes += qual
                    }
                  case _ =>
                }
            }
            for (path <- pathes) {
              addImportForPath(path, ref, explicitly = true)
            }
          }
        }

        if (wildcardImport) {
          //check all names
          for (name <- usedNames) checkName(name)
        } else {
          //check only newly imported name
          val name = path.split('.').last
          if (usedNames.contains(name)) {
            checkName(name)
          }
        }
      }

      HintManager.getInstance.hideAllHints()
    }

    firstPossibleGoodPlace match {
      case Some(expr) if ref == null || expr.getTextOffset < ref.getTextOffset =>
        expr.replace(importSt.importExprs(0))
        tail()
        return
      case _ =>
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
            insertFirstImport(importSt, first)
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

    tail()
  }

  protected def insertFirstImport(importSt: ScImportStmt, first: PsiElement): PsiElement = addBefore(importSt, first)

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
  
  def plainDeleteImport(stmt: ScImportExpr) {
    stmt.deleteExpr()
  }
  
  def plainDeleteSelector(sel: ScImportSelector) {
    sel.deleteSelector()
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

object ScImportsHolder {
  private val resolveCache = RecursionManager.createGuard("resolveCache")
}
