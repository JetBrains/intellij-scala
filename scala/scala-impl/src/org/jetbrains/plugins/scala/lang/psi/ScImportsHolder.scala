package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.scope._
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScImportStmtFactory
import org.jetbrains.plugins.scala.editor.importOptimizer._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.stubOrPsiPrevSibling
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory, ScalaStubBasedElementImpl}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable

trait ScImportsHolder extends ScalaPsiElement {

  def getImportStatements: Seq[ScImportStmt] = {
    val stub =  this match {
      case s: ScalaStubBasedElementImpl[_, _] => s.getGreenStub
      case f: ScalaFileImpl => f.getStub
      case _ => null
    }
    if (stub != null)
      stub.getChildrenByType(ScalaElementType.ImportStatement, ScImportStmtFactory).toSeq
    else
      findChildren[ScImportStmt]
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    if (lastParent != null) {
      val prevImports = previousImports(lastParent)

      //Resolve all references in previous import expressions in direct order to avoid SOE
      prevImports.foreach(updateResolveCaches)

      val shouldStop =
        prevImports.reverse
          .find(!_.processDeclarations(processor, state, lastParent, place))

      if (shouldStop.nonEmpty)
        return false
    }
    true
  }

  @tailrec
  private def previousImports(lastParent: PsiElement, acc: List[ScImportStmt] = Nil): List[ScImportStmt] = {
    stubOrPsiPrevSibling(lastParent) match {
      case null              => acc
      case imp: ScImportStmt => previousImports(imp, imp :: acc)
      case other             => previousImports(other, acc)
    }
  }

  private def updateResolveCaches(importStmt: ScImportStmt): Unit =
    for {
      expr <- importStmt.importExprs
      ref  <- expr.reference
    } ref.multiResolveScala(false)


  def getImportsForLastParent(lastParent: PsiElement): Seq[ScImportStmt] = {
    val buffer = mutable.ArrayBuffer.empty[ScImportStmt]
    if (lastParent != null) {
      var run = stubOrPsiPrevSibling(lastParent)
      while (run != null) {
        ProgressManager.checkCanceled()
        run match {
          case importStmt: ScImportStmt => buffer += importStmt
          case _ =>
        }
        run = stubOrPsiPrevSibling(run)
      }
    }
    buffer.toVector
  }

  def getAllImportUsed: mutable.Set[ImportUsed] = {
    val res = mutable.HashSet.empty[ImportUsed]
    def processChild(element: PsiElement): Unit = {
      for (child <- element.getChildren) {
        child match {
          case imp: ScImportExpr =>
            if (/*!imp.singleWildcard && */imp.selectorSet.isEmpty) {
              res += ImportExprUsed(imp)
            }
            else if (imp.hasWildcardSelector) {
              res += ImportWildcardSelectorUsed(imp)
            }
            for (selector <- imp.selectors if !selector.isWildcardSelector) {
              res += ImportSelectorUsed(selector)
            }
          case _ => processChild(child)
        }
      }
    }
    processChild(this)
    res
  }


  def addImportForClass(clazz: PsiClass, ref: PsiElement = null): Unit = {
    ref match {
      case ref: ScReference =>
        if (!ref.isValid || ref.isReferenceTo(clazz)) return
        ref.bind().foreach {
          case ScalaResolveResult(t: ScTypeAliasDefinition, _) if t.typeParameters.isEmpty =>
            t.aliasedType.foreach {
              case ScDesignatorType(c: PsiClass) if c == clazz => return
              case _ =>
            }
          case ScalaResolveResult(c: PsiClass, _) if c.qualifiedName == clazz.qualifiedName => return
          case ScalaResolveResult.withActual(p: ScReferencePattern) =>
            p.nameContext match {
              case ContainingClass(o: ScObject) if Set("scala.Predef", "scala").contains(o.qualifiedName) => return
              case ScPatternDefinition.expr(ResolvesTo(`clazz`)) => return
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    addImportForPath(clazz.qualifiedName, ref)
  }

  def addImportForPsiNamedElement(named: PsiNamedElement,
                                  reference: PsiElement,
                                  maybeClass: Option[PsiClass] = None): Unit = {
    val needImport = reference match {
      case null => true
      case ref: ScReference if ref.isValid => !ref.isReferenceTo(named)
      case _ => false
    }

    if (needImport) {
      val maybeQualifiedName = maybeClass match {
        case Some(clazz) => Option(clazz.qualifiedName).map(_ + "." + named.name)
        case _ => ScalaNamesUtil.qualifiedName(named)
      }

      maybeQualifiedName.foreach {
        addImportForPath(_, reference)
      }
    }
  }

  def addImportsForPaths(paths: Seq[String], refsContainer: PsiElement = null): Unit = {
    import ScalaImportOptimizer._

    implicit val manager: PsiManager = getManager
    def samePackage(path: String) = {
      val ref = createReferenceFromText(path)
      val pathQualifier = Option(ref).flatMap(_.qualifier.map(_.getText)).getOrElse("")
      val ourPackageName = this.parentOfType(classOf[ScPackaging], strict = false)
        .map(_.fullPackageName)
      ourPackageName.contains(pathQualifier)
    }

    def firstChildNotCommentWhitespace =
      this.children.dropWhile(el => el.isInstanceOf[PsiComment] || el.isInstanceOf[PsiWhiteSpace]).headOption

    firstChildNotCommentWhitespace.foreach {
      case pack: ScPackaging if !pack.isExplicit && this.children.filterByType[ScImportStmt].isEmpty =>
        pack.addImportsForPaths(paths, refsContainer)
        return
      case _ =>
    }

    val file = this.getContainingFile match {
      case sf: ScalaFile => sf
      case _ => return
    }

    //don't add wildcard imports here, it should be done only on explicit "Optimize Imports" action
    val settings = OptimizeImportSettings(file).copy(classCountToUseImportOnDemand = Int.MaxValue)

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
      val dummyImport = createImportFromText("import dummy.dummy", this)
      val inserted = insertFirstImport(dummyImport, getFirstChild).asInstanceOf[ScImportStmt]
      val psiAnchor = PsiAnchor.create(inserted)
      val rangeInfo = RangeInfo(psiAnchor, psiAnchor, importInfosToAdd, usedImportedNames = Set.empty, isLocal = false)
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
        case Some(rangeInfo @ RangeInfo(rangeStart, _, infosFromRange, _, _)) =>
          val resultInfos = insertImportInfos(importInfosToAdd, infosFromRange, rangeStart, settings)
          optimizer.replaceWithNewImportInfos(rangeInfo, resultInfos, settings, file)
        case _ =>
      }
    }
  }

  def addImportForPath(path: String, ref: PsiElement = null): Unit =
    addImportsForPaths(Seq(path), ref)

  private def hasValidQualifier(importInfo: ImportInfo, place: PsiElement): Boolean = {
    val ref = createReferenceFromText(importInfo.prefixQualifier, this, place)
    ref.multiResolveScala(false).nonEmpty
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
      case _ =>
        addImportBefore(importSt, first)
    }
  }

  protected def indentLine(element: PsiElement): Unit = {
    val indent = CodeStyleManager.getInstance(getProject).getLineIndent(getContainingFile, element.getTextRange.getStartOffset)
    if (indent == null) return

    //it's better to work with psi on this stage
    element.getPrevSibling match {
      case ws: PsiWhiteSpace =>
        val oldTextNoIndent = ws.getText.reverse.dropWhile(c => c == ' ' || c == '\t').reverse
        val newText = oldTextNoIndent + indent
        if (!ws.textMatches(newText)) {
          val indented = ScalaPsiElementFactory.createNewLine(newText)
          ws.replace(indented)
        }
      case _ =>
        if (!indent.isEmpty) {
          val indented = ScalaPsiElementFactory.createNewLine(s"$indent")
          addBefore(indented, element)
        }
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
    val result = CodeEditUtil.addChildren(getNode, element.getNode, element.getNode, anchorNode).getPsi
    indentLine(result)
    result
  }

  def addImportAfter(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor.getNode == getNode.getLastChildNode) return addImport(element)
    addImportBefore(element, anchor.getNode.getTreeNext.getPsi)
  }

  def plainDeleteImport(stmt: ScImportExpr): Unit = {
    stmt.deleteExpr()
  }

  def plainDeleteSelector(sel: ScImportSelector): Unit = {
    sel.deleteSelector()
  }

  def deleteImportStmt(stmt: ScImportStmt): Unit = {
    def remove(node: ASTNode): Unit = getNode.removeChild(node)
    def shortenWhitespace(node: ASTNode): Unit = {
      if (node == null) return
      if (node.getText.count(_ == '\n') >= 2) {
        val nl = createNewLine(node.getText.replaceFirst("[\n]", ""))(getManager)
        getNode.replaceChild(node, nl.getNode)
      }
    }
    def removeWhitespace(node: ASTNode): Unit = {
      if (node == null) return
      if (node.getPsi.isInstanceOf[PsiWhiteSpace]) {
        if (node.getText.count(_ == '\n') < 2) remove(node)
        else shortenWhitespace(node)
      }
    }
    def removeSemicolonAndWhitespace(node: ASTNode): Unit = {
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

  def apply(reference: PsiElement)
           (implicit project: Project = reference.getProject): ScImportsHolder =
    if (ScalaCodeStyleSettings.getInstance(project).isAddImportMostCloseToReference)
      getParentOfType(reference, classOf[ScImportsHolder])
    else {
      getParentOfType(reference, classOf[ScPackaging]) match {
        case null => reference.getContainingFile match {
          case holder: ScImportsHolder => holder
          case file =>
            throw new AssertionError(s"Holder is wrong, file text: ${file.getText}")
        }
        case packaging: ScPackaging => packaging
      }
    }
}
