package org.jetbrains.plugins.scala.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.scope._
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.{ScExportStmtFactory, ScImportStmtFactory}
import org.jetbrains.plugins.scala.autoImport.quickFix.{ClassToImport, ElementToImport}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.stubOrPsiPrevSibling
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory, ScalaStubBasedElementImpl}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.util.ScalaPsiNamesUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaHighlightingSettings

import scala.annotation.tailrec
import scala.collection.mutable

sealed trait ScImportsOrExportsHolder extends ScalaPsiElement {

  def deleteImportOrExportStmt(stmt: ScImportOrExportStmt): Unit = {
    def remove(node: ASTNode): Unit = getNode.removeChild(node)
    def shortenWhitespace(wsNode: ASTNode): Unit = {
      if (wsNode == null) return

      val lineBreaks = wsNode.getText.count(_ == '\n')
      if (lineBreaks >= 2) {
        val nlText = wsNode.getText.replaceFirst("[\n]", "")
        val nl = ScalaPsiElementFactory.createNewLine(nlText)(using getManager)
        getNode.replaceChild(wsNode, nl.getNode)
      }
    }
    def removeWhitespace(node: ASTNode): Unit = {
      if (node == null) return
      if (node.getPsi.is[PsiWhiteSpace]) {
        val lineBreaks = node.getText.count(_ == '\n')
        if (lineBreaks <= 1) remove(node)
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

trait ScImportsHolder extends ScImportsOrExportsHolder {

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

  def processDeclarationsFromImports(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    if (lastParent != null) {
      val prevImports = previousImports(lastParent)

      val project = getProject

      if (!ScalaHighlightingSettings.isBuiltInHighlightingDisabled(project) && !ScalaHighlightingSettings.isIncrementalHighlightingEnabled(project)) {
        //Resolve all references in previous import expressions in direct order to avoid SOE
        prevImports.foreach { importStmt =>
          ProgressManager.checkCanceled()
          updateResolveCaches(importStmt)
        }
      }

      val shouldStop = prevImports.findLast(!_.processDeclarations(processor, state, lastParent, place))

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

  def addImportForClass(clazz: PsiClass, @Nullable ref: ScReference, aliasName: Option[String] = None): Unit = {
    val isAlreadyAvailableInReferenceScope: Boolean = if (ref == null) false else {
      if (!ref.isValid || ref.isReferenceTo(clazz))
        true
      else {
        val resolveResult = ref.bind()
        resolveResult.exists {
          case ScalaResolveResult(t: ScTypeAliasDefinition, _) if t.typeParameters.isEmpty && !t.isEffectivelyOpaque =>
            val aliasedType = t.aliasedType
            aliasedType.exists {
              case ScDesignatorType(c: PsiClass) =>
                c == clazz
              case _ =>
                false
            }
          case ScalaResolveResult(c: PsiClass, _) if c.qualifiedName == clazz.qualifiedName =>
            true
          case ScalaResolveResult.withActual(p: ScReferencePattern) =>
            p.nameContext match {
              case ContainingClass(o: ScObject) if isPredefined(o.qualifiedName) => true
              case ScPatternDefinition.expr(ResolvesTo(`clazz`))                 => true
              case _                                                             => false
            }
          case _ =>
            false
        }
      }
    }

    if (!isAlreadyAvailableInReferenceScope) {
      addImportForPath(ImportPath(clazz.qualifiedName, aliasName), ref)
    }
  }

  def addImportForElement(element: ElementToImport, @Nullable ref: ScReference): Unit = {
    addImportForElement(element, None, ref)
  }

  private def addImportForElement(element: ElementToImport, aliasName: Option[String], @Nullable ref: ScReference): Unit = {
    element match {
      case ClassToImport(clazz) =>
        addImportForClass(clazz, ref, aliasName)
      case _                    =>
        val importPath = ImportPath(element.qualifiedName, aliasName)
        addImportForPath(importPath, ref)
    }
  }

  def addImportForPsiNamedElement(named: PsiNamedElement,
                                  @Nullable reference: ScReference,
                                  maybeClass: Option[PsiClass] = None): Unit = {
    val needImport =
      reference == null || reference.isValid && !reference.isReferenceTo(named)

    if (needImport) {
      val maybeQualifiedName = maybeClass match {
        case Some(clazz) => Option(clazz.qualifiedName).map(_ + "." + named.name)
        case _ => ScalaPsiNamesUtil.qualifiedName(named)
      }

      maybeQualifiedName.foreach {
        addImportForPath(_, reference)
      }
    }
  }

  final def addImportForPath(
    path: String,
    @Nullable refsContainer: PsiElement = null
  ): Unit = {
    addImportForPath(ImportPath(path), refsContainer)
  }

  protected def addImportForPath(
    path: ImportPath,
    @Nullable refsContainer: PsiElement
  ): Unit = {
    addImportsForPathsImpl(Seq(path), refsContainer)
  }

  final def addImportsForPaths(
    paths: Seq[String],
    @Nullable refsContainer: PsiElement = null
  ): Unit = {
    val importPaths = paths.map(ImportPath.apply(_))
    addImportsForPaths(importPaths, refsContainer)
  }

  final def addImportsForPaths(
    importPaths: Seq[ImportPath],
    @Nullable refsContainer: PsiElement
  )(implicit d: DummyImplicit): Unit = {
    val first = this.firstChildNotWhitespaceComment
    first match {
      case Some(pack: ScPackaging) if !pack.isExplicit && this.children.filterByType[ScImportStmt].isEmpty =>
        //what is this branch for???
        //looks like it's not covered with tests and not clear what it is...
        pack.addImportsForPathsImpl(importPaths, refsContainer)
      case _ =>
        addImportsForPathsImpl(importPaths, refsContainer)
    }
  }

  protected[psi] def addImportsForPathsImpl(
    paths: Seq[ImportPath],
    @Nullable refsContainer: PsiElement
  ): Unit = ImportInserter.instance
    .addImportsForPaths(this, paths, refsContainer)

  def bindImportSelectorOrExpression(
    importExpr: ScImportExpr,
    oldImportExprOrSelectorToDelete: PsiElement,
    newImportPath: ImportPath,
  ): Unit = ImportInserter.instance
    .bindImportSelectorOrExpression(this, importExpr, oldImportExprOrSelectorToDelete, newImportPath)

  @ApiStatus.Internal
  private[scala] def hasCodeBeforeImports: Boolean = {
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

  /** Lets the [[ImportInserter]] call [[insertFirstImport]], which subclasses (also in other modules) override */
  @ApiStatus.Internal
  private[scala] final def insertFirstImportStatement(importSt: ScImportStmt, anchor: PsiElement): PsiElement =
    insertFirstImport(importSt, anchor)

  protected def insertFirstImport(importSt: ScImportStmt, anchor: PsiElement): PsiElement = {
    childBeforeFirstImport match {
      case Some(elem) if anchor != null && elem.endOffset > anchor.startOffset =>
        addImportAfter(importSt, elem)
      case _ =>
        addImportBefore(importSt, anchor)
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
        if (indent.nonEmpty) {
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

  def addImportBefore(importToAdd: ScImportStmt, anchor: PsiElement): PsiElement = {
    val anchorNode = anchor.getNode
    val result = CodeEditUtil.addChildren(getNode, importToAdd.getNode, importToAdd.getNode, anchorNode).getPsi
    indentLine(result)
    result
  }

  def addImportAfter(importToAdd: ScImportStmt, anchor: PsiElement): PsiElement = {
    val anchorNode = anchor.getNode
    if (anchorNode == getNode.getLastChildNode)
      addImport(importToAdd)
    else {
      val anchorNext = anchorNode.getTreeNext
      addImportBefore(importToAdd, anchorNext.getPsi)
    }
  }

  def plainDeleteImport(stmt: ScImportExpr): Unit = {
    stmt.deleteExpr()
  }

  def plainDeleteSelector(sel: ScImportSelector): Unit = {
    sel.deleteSelector(removeRedundantBraces = true)
  }
}

object ScImportsHolder {

  private val PredefinedPackages = Set("scala.Predef", "scala")
  private def isPredefined(fqn: String) = PredefinedPackages.contains(fqn)

  def forNewImportInsertion(place: PsiElement)
                           (implicit project: Project = place.getProject): ScImportsHolder =
    if (ScalaCodeStyleSettings.getInstance(project).isAddImportMostCloseToReference)
      getParentOfType(place, classOf[ScImportsHolder])
    else {
      getParentOfType(place, classOf[ScPackaging]) match {
        case null => place.getContainingFile match {
          case holder: ScImportsHolder => holder
          case file =>
            throw new AssertionError(s"Holder is wrong, file text: ${file.getText}")
        }
        case packaging: ScPackaging => packaging
      }
    }

  //TODO: do not use `apply` name here
  // This methods semantics is different from what one might expect
  // One would expect that this method returns closest import holder for some element
  // But in reality it returns import holder to which a new import statement should be inserted
  // Use more descriptive name `forNewImport` instead
  def apply(place: PsiElement)
           (implicit project: Project = place.getProject): ScImportsHolder =
    forNewImportInsertion(place)(using project)

  /**
   * @param qualifiedName      for example `org.example.MyClass`
   * @param aliasName optional name to rename last part of the path<br>
   *                  For example if `aliasName = MyClass_Renamed`, then import will be<br>
   *                  `org.example.{MyClass => MyClass_Renamed}`
   */
  case class ImportPath(qualifiedName: String, aliasName: Option[String] = None) {
    def importExpressionText: String = {
      val parts = ScalaNamesUtil.escapeKeywordsFqnParts(qualifiedName)
      aliasName match {
        case Some(alias) =>
          val partsInit = parts.init
          val partsLast = parts.last
          assert(parts.size > 1, s"Can't import path without dot ($qualifiedName) with alias ($alias)")
          //NOTE: using Scala 2 syntax with `=>` because this will be converted to ImportInfo later anyway
          //this ImportInfo will be rendered with correctly depending on the context (language level, settings)
          s"${partsInit.mkString(".")}.{$partsLast => ${ScalaNamesUtil.escapeKeyword(alias)}}"

        case _ =>
          s"${parts.mkString(".")}"
      }
    }
  }
}

trait ScExportsHolder extends ScImportsOrExportsHolder {

  def getExportStatements: Seq[ScExportStmt] = {

    val stub = this match {
      case s: ScalaStubBasedElementImpl[_, _] => s.getGreenStub
      case f: ScalaFileImpl                   => f.getStub
      case _                                  => null
    }
    if (stub != null)
      stub.getChildrenByType(ScalaElementType.ExportStatement, ScExportStmtFactory).toSeq
    else
      findChildren[ScExportStmt]
  }

  // TODO: this is a dummy implementation copied from ScImportsHolder
  //  it's wrong, but at least exports are resolved in the same scope where declared
  //  to be improved in SCL-19437 (maybe remove this logic and use SyntheticMembersInjector instead
  def processDeclarationsFromExports(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    val exports = getExportStatements

//    Resolve all references in previous exports expressions in direct order to avoid SOE
//    exports.foreach(updateResolveCaches)
//
    val shouldStop = exports.findLast(!_.processDeclarations(processor, state, lastParent, place))

    if (shouldStop.nonEmpty)
      return false

    true
  }

  private def updateResolveCaches(exportStmt: ScExportStmt): Unit =
    for {
      expr <- exportStmt.importExprs
      ref  <- expr.reference
    } ref.multiResolveScala(false)
}
