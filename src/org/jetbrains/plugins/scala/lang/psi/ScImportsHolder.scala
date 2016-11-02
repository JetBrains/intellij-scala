package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.scope._
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.importOptimizer._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

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
    findChildrenByClassScala(classOf[ScImportStmt]).toSeq
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
    buffer.toVector
  }

  def getAllImportUsed: mutable.Set[ImportUsed] = {
    val res: mutable.Set[ImportUsed] = new mutable.HashSet[ImportUsed]
    def processChild(element: PsiElement) {
      for (child <- element.getChildren) {
        child match {
          case imp: ScImportExpr =>
            if (/*!imp.singleWildcard && */imp.selectorSet.isEmpty) {
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
        case p: ScPackaging if !p.isExplicit && buf.isEmpty => return p.importStatementsInHeader
        case _: ScTypeDefinition | _: ScPackaging => return buf.toVector
        case _ =>
      }
    }
    buf.toVector
  }

  def addImportForClass(clazz: PsiClass, ref: PsiElement = null) {
    ref match {
      case ref: ScReferenceElement =>
        if (!ref.isValid || ref.isReferenceTo(clazz)) return
        ref.bind() match {
          case Some(ScalaResolveResult(t: ScTypeAliasDefinition, _)) if t.typeParameters.isEmpty =>
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
    if (needImport) {
      cClass match {
        case Some(clazz) =>
          val qualName = clazz.qualifiedName
          if (qualName != null) {
            addImportForPath(qualName + "." + elem.name, ref)
          }
        case _ =>
          val qualName = ScalaNamesUtil.qualifiedName(elem).orNull
          if (qualName != null) {
            addImportForPath(qualName, ref)
          }
      }
    }
  }

  def addImportsForPaths(paths: Seq[String], refsContainer: PsiElement = null): Unit = {
    import ScalaImportOptimizer._

    implicit val manager = getManager
    def samePackage(path: String) = {
      val ref = createReferenceFromText(path)
      val pathQualifier = Option(ref).flatMap(_.qualifier.map(_.getText)).getOrElse("")
      val ourPackageName: Option[String] =
        Option(PsiTreeUtil.getParentOfType(this, classOf[ScPackaging], false)).map(_.fullPackageName)
      ourPackageName.contains(pathQualifier)
    }

    getFirstChild match {
      case pack: ScPackaging if !pack.isExplicit && children.filterByType(classOf[ScImportStmt]).isEmpty =>
        pack.addImportsForPaths(paths, refsContainer)
        return
      case _ =>
    }

    val file = this.getContainingFile match {
      case sf: ScalaFile => sf
      case _ => return
    }

    val settings = OptimizeImportSettings(getProject)

    val optimizer: ScalaImportOptimizer = findOptimizerFor(file) match {
      case Some(o: ScalaImportOptimizer) => o
      case _ => return
    }

    val place = getImportStatements.lastOption.getOrElse(getFirstChild.getNextSibling)

    val importInfosToAdd = paths
      .filterNot(samePackage)
      .flatMap(createInfoFromPath(_, place))
      .filter(hasValidQualifier(_, place))

    val importRanges = optimizer.collectImportRanges(this, createInfo(_), Set.empty)

    val needToInsertFirst =
      if (importRanges.isEmpty) true
      else refsContainer == null && hasCodeBeforeImports

    if (needToInsertFirst) {
      val dummyImport = createImportFromText("import dummy._")
      val usedNames = collectUsedImportedNames(this)
      val inserted = insertFirstImport(dummyImport, getFirstChild).asInstanceOf[ScImportStmt]
      val psiAnchor = PsiAnchor.create(inserted)
      val rangeInfo = RangeInfo(psiAnchor, psiAnchor, importInfosToAdd, usedImportedNames = usedNames, isLocal = false)
      val infosToAdd = optimizedImportInfos(rangeInfo, settings)

      optimizer.replaceWithNewImportInfos(rangeInfo, infosToAdd, settings, file)
    }
    else {
      val sortedRanges = importRanges.toSeq.sortBy(_.startOffset)
      val selectedRange =
        if (refsContainer != null && ScalaCodeStyleSettings.getInstance(getProject).isAddImportMostCloseToReference)
          sortedRanges.reverse.find(_.endOffset < refsContainer.getTextRange.getStartOffset)
        else sortedRanges.headOption

      selectedRange match {
        case Some(rangeInfo @ (RangeInfo(rangeStart, _, infosFromRange, _, _))) =>
          val resultInfos = insertImportInfos(importInfosToAdd, infosFromRange, rangeStart, settings)
          optimizer.replaceWithNewImportInfos(rangeInfo, resultInfos, settings, file)
        case _ =>
      }
    }
  }

  def addImportForPath(path: String, ref: PsiElement = null): Unit = {
    addImportsForPaths(Seq(path), ref)
  }

  private def hasValidQualifier(importInfo: ImportInfo, place: PsiElement): Boolean = {
    val ref = createReferenceFromText(importInfo.prefixQualifier, this, place)
    ref.multiResolve(false).nonEmpty
  }

  private def createInfoFromPath(path: String, place: PsiElement): Seq[ImportInfo] = {
    val importText = s"import ${ScalaNamesUtil.escapeKeywordsFqn(path)}"
    val importStmt = createImportFromTextWithContext(importText, this, place)
    ScalaImportOptimizer.createInfo(importStmt)
  }

  private def hasCodeBeforeImports: Boolean = {
    val firstChild = childBeforeFirstImport.getOrElse(getFirstChild)
    var nextChild = firstChild
    while (nextChild != null) {
      nextChild match {
        case _: ScImportStmt => return false
        case _: ScBlockStatement => return true
        case _ => nextChild = nextChild.getNextSibling
      }
    }
    true
  }

  protected def insertFirstImport(importSt: ScImportStmt, first: PsiElement): PsiElement = {
    childBeforeFirstImport match {
      case Some(elem) if first != null && elem.getTextRange.getEndOffset > first.getTextRange.getStartOffset =>
        addImportAfter(importSt, elem)
      case _ => addBefore(importSt, first)
    }
  }

  protected def childBeforeFirstImport: Option[PsiElement] = {
    Option(getNode.findChildByType(ScalaTokenTypes.tLBRACE)).map(_.getPsi)
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
        val nl = createNewLine(node.getText.replaceFirst("[\n]", ""))(getManager)
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
