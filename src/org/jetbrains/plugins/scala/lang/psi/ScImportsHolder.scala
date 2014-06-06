package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.codeInsight.hint.HintManager
import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.{RecursionManager, Trinity}
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.scope._
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.extensions.{toPsiClassExt, toPsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
   * // 
   * todo it became unused >1year ago   
   */
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
          case imp: ScImportExpr =>
            if (/*!imp.singleWildcard && */imp.selectorSet == None) {
              res += ImportExprUsed(imp)
            }
            else if (imp.singleWildcard) {
              res += ImportWildcardSelectorUsed(imp)
            }
            for (selector <- imp.selectors) {
              res += ImportSelectorUsed(selector)
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
      case Some(x) =>
        if (checkReference(x)) Some(x)
        else None
      case None => None
    }
  }

  def addImportForClass(clazz: PsiClass, ref: PsiElement = null) {
    ref match {
      case ref: ScReferenceElement =>
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
      case _ =>
    }
    addImportForPath(clazz.qualifiedName, ref)
  }

  def addImportForPsiNamedElement(elem: PsiNamedElement, ref: PsiElement, cClass: Option[PsiClass] = None) {
    def needImport = ref match {
      case null => true
      case ref: ScReferenceElement => ref.isValid && !ref.isReferenceTo(elem)
      case _ => false
    }
    ScalaNamesUtil.qualifiedName(elem) match {
      case Some(qual) if needImport => addImportForPath(qual)
      case _ =>
    }
  }

  //todo: Code now looks overcomplicated and logic is separated from ScalaImportOptimizer, rewrite?
  def addImportForPath(path: String, ref: PsiElement = null, explicitly: Boolean = false) {
    val selectors = new ArrayBuffer[String]
    val renamedSelectors = new ArrayBuffer[String]()

    val qualifiedName = path
    val index = qualifiedName.lastIndexOf('.')
    if (index == -1) return  //cannot import anything
    var classPackageQualifier = qualifiedName.substring(0, index)
    val pathQualifier = classPackageQualifier

    //collecting selectors to add into new import statement
    var firstPossibleGoodPlace: Option[ScImportExpr] = None
    val toDelete: ArrayBuffer[ScImportExpr] = new ArrayBuffer[ScImportExpr]()
    for (imp <- importStatementsInHeader if !explicitly) {
      for (expr: ScImportExpr <- imp.importExprs) {
        val qualifier = expr.qualifier
        if (qualifier != null) { //in case "import scala" it can be null
          val qn = qualifier.resolve() match {
            case named: PsiNamedElement => ScalaNamesUtil.qualifiedName(named).getOrElse("")
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

    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(getProject)
    if (!settings.isCollectImports &&
        selectors.length < settings.getClassCountToUseImportOnDemand - 1) {
      toDelete.clear()
      firstPossibleGoodPlace = None
      selectors.clear()
    }

    //creating selectors string (after last '.' in import expression)
    var isPlaceHolderImport = false
    val simpleName = path.substring(path.lastIndexOf('.') + 1)
    simpleName +=: selectors

    val wildcardImport: Boolean = selectors.contains("_") ||
      selectors.length >= settings.getClassCountToUseImportOnDemand
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
        case _ =>
          if (!p.processDeclarations(completionProcessor, ResolveState.initial, lastParent, place)) return
          treeWalkUp(completionProcessor, p.getContext, p)
      }
    }

    def collectAllCandidates(): mutable.HashMap[String, HashSet[PsiNamedElement]] = {
      val candidates = new mutable.HashMap[String, HashSet[PsiNamedElement]]
      val everythingProcessor  = new CompletionProcessor(StdKinds.stableImportSelector, getLastChild, includePrefixImports = false)
      treeWalkUp(everythingProcessor, this, place)
      for (candidate <- everythingProcessor.candidates) {
        val set = candidates.getOrElse(candidate.name, HashSet.empty[PsiNamedElement])
        candidates.update(candidate.name, set + candidate.getElement)
      }
      candidates
    }

    val candidatesBefore = collectAllCandidates()

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
    val renamedPackages: mutable.HashMap[PsiPackage, String] = new mutable.HashMap[PsiPackage, String]()
    for (candidate <- completionProcessor.candidatesS) {
      candidate match {
        case r@ScalaResolveResult(pack: PsiPackage, _) =>
          if (names.contains(pack.name)) {
            var index = packs.indexWhere(_.name == pack.name)
            while(index != -1) {
              packs.remove(index)
              index = packs.indexWhere(_.name == pack.name)
            }
          } else {
            names += pack.name
            packs += pack
            r.isRenamed match {
              case Some(otherName) => renamedPackages += ((pack, otherName))
              case _ =>
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
      def updateImportStringWith(s: String) {
        if (ScalaNamesUtil.isKeyword(s)) importString = "`" + s + "`" + "." + importString
        else importString = s + "." + importString
      }
      if ((!settings.isAddFullQualifiedImports ||
              classPackageQualifier.indexOf(".") == -1) &&
              packages.contains(classPackageQualifier)) {
        val s = packs.find(_.getQualifiedName == classPackageQualifier) match {
          case Some(qual) => renamedPackages.get(qual) match {
            case Some(r) => r
            case _ => last
          }
          case _ => last
        }
        updateImportStringWith(s)
        importSt = ScalaPsiElementFactory.createImportFromText("import " + importString, getManager)
      } else {
        updateImportStringWith(last)
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
        val psiPack = ScPackageImpl.findPackage(getProject, getSplitQualifierElement(qualifiedName)._1)
        if (psiPack != null) psiPack.getSubPackages(getResolveScope)
        else Array[PsiPackage]()
      }
      def checkImports(element: PsiElement) {
        element match {
          case expr: ScImportExpr =>
            @tailrec
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
          case _ => for (child <- element.getChildren) checkImports(child)
        }
      }
      if (subPackages.length > 0) {
        checkImports(this)
      }
    }

    def tail() {
      if (!explicitly) {
        val candidatesAfter = collectAllCandidates()

        def checkName(s: String) {
          if (candidatesBefore.getOrElse(s, HashSet.empty).size < candidatesAfter.getOrElse(s, HashSet.empty).size) {
            val pathes = new mutable.HashSet[String]()
            //let's try to fix it by adding all before imports explicitly
            candidatesBefore.getOrElse(s, HashSet.empty[PsiNamedElement]).foreach {
              case c: PsiClass => pathes += c.qualifiedName
              case c: PsiNamedElement => pathes ++= ScalaNamesUtil.qualifiedName(c)
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
      case Some(x: ScImportStmt) =>
        //now we walking throw forward siblings, and seeking appropriate place (lexicographical)
        var stmt: PsiElement = x
        var prevStmt: ScImportStmt = null

        def addImportAfterPrevStmt(ourIndex: Int, prevIndex: Int) {
          val before = addImportAfter(importSt, prevStmt)

          if (ourIndex > prevIndex) {
            var blankLines = ""
            var currentGroupIndex = prevIndex
            val groups = ScalaCodeStyleSettings.getInstance(getProject).getImportLayout
            def iteration() {
              currentGroupIndex += 1
              while (groups(currentGroupIndex) == ScalaCodeStyleSettings.BLANK_LINE) {
                blankLines += "\n"
                currentGroupIndex += 1
              }
            }
            while (currentGroupIndex != -1 && blankLines.isEmpty && currentGroupIndex < ourIndex) iteration()
            if (!blankLines.isEmpty) {
              val newline = ScalaPsiElementFactory.createNewLineNode(getManager, blankLines)
              before.getParent.getNode.addChild(newline, before.getNode)
            }
          }
        }

        //this is flag to stop walking when we add import before more big lexicographically import statement
        var added = false

        def getImportPrefixQualifier(stmt: ScImportStmt): String = {
          val importExpr: ScImportExpr = stmt.importExprs.headOption.getOrElse(return "")
          ScalaImportOptimizer.getImportInfo(importExpr, _ => true).
            fold(Option(importExpr.qualifier).fold("")(_.getText))(_.prefixQualifier)
        }

        while (!added && stmt != null && (stmt.isInstanceOf[ScImportStmt]
            || stmt.isInstanceOf[PsiWhiteSpace]
            || stmt.getNode.getElementType == ScalaTokenTypes.tSEMICOLON)) {
          stmt match {
            case im: ScImportStmt =>
              def processPackage(elem: PsiElement): Boolean = {
                if (classPackageQualifier == "") return true
                val completionProcessor = new ResolveProcessor(StdKinds.packageRef, elem,
                  getSplitQualifierElement(classPackageQualifier)._2)
                val place = getLastChild
                @tailrec
                def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
                  place match {
                    case null =>
                    case p =>
                      if (!p.processDeclarations(completionProcessor,
                        ResolveState.initial,
                        lastParent, place)) return
                      treeWalkUp(place.getContext, place)
                  }
                }
                treeWalkUp(this, place)
                completionProcessor.candidatesS.size > 0
              }
              val nextImportContainsRef =
                if (ref != null) PsiTreeUtil.isAncestor(im, ref, false) // See SCL-2925
                else false
              def compare: Boolean = {
                val l: String = getImportPrefixQualifier(im)
                ScalaImportOptimizer.greater(l, pathQualifier, im.getText, importSt.getText, getProject)
              }
              val cond2 = compare && processPackage(im)
              if (nextImportContainsRef || cond2) {
                added = true
                val ourIndex = ScalaImportOptimizer.findGroupIndex(pathQualifier, getProject)
                val imIndex = ScalaImportOptimizer.findGroupIndex(getImportPrefixQualifier(im), getProject)
                val prevIndex =
                  if (prevStmt == null) -1
                  else ScalaImportOptimizer.findGroupIndex(getImportPrefixQualifier(prevStmt), getProject)
                if (prevIndex != ourIndex) {
                  addImportBefore(importSt, im)
                  if (ourIndex < imIndex) {
                    var blankLines = ""
                    var currentGroupIndex = ourIndex
                    val groups = ScalaCodeStyleSettings.getInstance(getProject).getImportLayout
                    def iteration() {
                      currentGroupIndex += 1
                      while (groups(currentGroupIndex) == ScalaCodeStyleSettings.BLANK_LINE) {
                        blankLines += "\n"
                        currentGroupIndex += 1
                      }
                    }
                    while (currentGroupIndex != -1 && blankLines.isEmpty && currentGroupIndex < imIndex) iteration()
                    if (!blankLines.isEmpty) {
                      val newline = ScalaPsiElementFactory.createNewLineNode(getManager, blankLines)
                      im.getParent.getNode.addChild(newline, im.getNode)
                    }
                  }
                } else addImportAfterPrevStmt(ourIndex, prevIndex)
              }
              prevStmt = im
            case _ =>
          }
          stmt = stmt.getNextSibling
        }
        //if our stmt is the biggest lexicographically import statement we add this to the end
        if (!added) {
          if (prevStmt != null) {
            val ourIndex = ScalaImportOptimizer.findGroupIndex(pathQualifier, getProject)
            val prevIndex = ScalaImportOptimizer.findGroupIndex(getImportPrefixQualifier(prevStmt), getProject)
            addImportAfterPrevStmt(ourIndex, prevIndex)
          } else {
            addImportAfter(importSt, getLastChild)
          }
        }
      case _ =>
        def updateFirst() {
          getFirstChild match {
            case pack: ScPackaging if !pack.isExplicit => pack.addImportForPath(path, ref, explicitly)
            case elem if elem != null => insertFirstImport(importSt, elem)
            case _ => addImport(importSt)
          }
        }

        getNode.findChildByType(ScalaTokenTypes.tLBRACE) match {
          case null if this.isInstanceOf[ScalaFile] =>
            updateFirst()
          case null =>
            val reference = getNode.findChildByType(ScalaElementTypes.REFERENCE)
            if (reference != null) {
              reference.getPsi.getNextSibling
                addImportAfter(importSt, reference.getPsi)
            } else {
              updateFirst()
            }
          case node =>
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
    def remove(node: ASTNode) = getNode.removeChild(node)
    def shortenWhitespace(node: ASTNode) {
      if (node == null) return
      if (node.getText.count(_ == '\n') >= 2) {
        val nl = ScalaPsiElementFactory.createNewLine(getManager, node.getText.replaceFirst("[\n]", ""))
        getNode.replaceChild(node, nl.getNode)
      }
    }
    def removeWhitespace(node: ASTNode) {
      if (node == null) return
      if (node.getPsi.isInstanceOf[PsiWhiteSpace]) {
        if (node.getText.count(_ == '\n') < 2) remove(node)
        else shortenWhitespace(node)
      }
    }
    def removeSemicolonAndWhitespace(node: ASTNode) {
      if (node == null) return
      if (node.getElementType == ScalaTokenTypes.tSEMICOLON) {
        removeWhitespace(node.getTreeNext)
        remove(node)
      }
      else removeWhitespace(node)
    }

    val node = stmt.getNode
    val next = node.getTreeNext
    val prev = node.getTreePrev

    removeSemicolonAndWhitespace(next)
    remove(node)
    shortenWhitespace(prev)
  }
}

object ScImportsHolder {
  private val resolveCache = RecursionManager.createGuard("resolveCache")
}
