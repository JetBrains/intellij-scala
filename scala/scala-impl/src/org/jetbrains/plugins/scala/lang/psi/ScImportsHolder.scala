package org.jetbrains.plugins.scala.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.scope._
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.{ScExportStmtFactory, ScImportStmtFactory}
import org.jetbrains.plugins.scala.autoImport.quickFix.{ClassToImport, ElementToImport}
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer.ImportInsertionPlace
import org.jetbrains.plugins.scala.editor.importOptimizer._
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
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory, ScalaStubBasedElementImpl}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

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
        val nl = ScalaPsiElementFactory.createNewLine(nlText)(getManager)
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
    import scala.language.existentials

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

      //Resolve all references in previous import expressions in direct order to avoid SOE
      prevImports.foreach { importStmt =>
        ProgressManager.checkCanceled()
        updateResolveCaches(importStmt)
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
          case ScalaResolveResult(t: ScTypeAliasDefinition, _) if t.typeParameters.isEmpty =>
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
        case _ => ScalaNamesUtil.qualifiedName(named)
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
    refsContainer: PsiElement = null
  ): Unit = {
    val importPaths = paths.map(ImportPath.apply(_))

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
  ): Unit = {
    val file = getContainingFile match {
      case sf: ScalaFile => sf
      case _ =>
        return
    }

    val optimizer = ScalaImportOptimizer.findScalaOptimizerFor(file) match {
      case Some(o) => o
      case _ =>
        return
    }
    val settings = OptimizeImportSettings(file)

    addImportsForPathsImpl(paths, file, settings, optimizer, refsContainer)
  }

  /**
   * Implementation:
   *  1. search for the best [[ImportRangeInfo import range]] to insert import into<br>
   *     (it constructs import infos for each import in the range)
   *  1. insert new import infos to appropriate places
   *  1. REPLACES THE WHOLE IMPORT RANGE with a newly-rendered import infos (using ScalaImportOptimizer)
   *
   * The last step can invalidate sibling import statements.<br>
   * So [[addImportsForPathsImpl]] methods shouldn't be used when adding imports one by one,
   * if some code relies on the fact that some other imports are not invalidated.<br>
   * For example, it shouldn't be used during move "Move" refactoring.
   * See
   *
   * @see [[insertImportPathToTheBestPlaceDuringRefactoring]]
   */
  private def addImportsForPathsImpl(
    paths: Seq[ImportPath],
    containingFile: ScalaFile,
    fileOptimizeSettings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer,
    @Nullable refsContainer: PsiElement
  ): Unit = {

    //don't add wildcard imports here, it should be done only on explicit "Optimize Imports" action
    val settings = fileOptimizeSettings.withoutCollapseSelectorsToWildcard

    val place = getImportStatements.lastOption.getOrElse(getFirstChild.getNextSibling)

    val importInfosToAdd: Seq[ImportInfo] = paths
      .filterNot(p => isFromSamePackage(p.qualifiedName))
      .flatMap(createInfoFromPath(_, place))
      .filter(hasValidQualifier(_, place))

    val importRanges = optimizer.collectImportRanges(this)

    val needToInsertFirst =
      if (importRanges.isEmpty) true
      else refsContainer == null && hasCodeBeforeImports

    val insertionRangeWithImportsToAdd: Option[(ImportRangeInfo, Seq[ImportInfo])] =
      if (needToInsertFirst) {

        def determineAnchor(): PsiElement = {
          def isDirective(el: PsiElement): Boolean = el.is[ScDirective]

          def isWhitespaceOrCommentOrDirective(el: PsiElement): Boolean = el.isWhitespaceOrComment || isDirective(el)

          val firstElement: PsiElement = getFirstChild

          (firstElement +: firstElement.nextSiblings.toSeq)
            .takeWhile(isWhitespaceOrCommentOrDirective)
            .findLast(isDirective)
            .map(_.getNextSibling).getOrElse(firstElement)
        }

        //ScalaImportOptimizer only works with import ranges
        //So we insert a temporary dummy import to simply replace it with a well-formatted import
        val dummyImport: ScImportStmt = ScalaPsiElementFactory.createImportFromText("import dummy.dummy", this)

        val anchor = determineAnchor()

        val inserted = insertFirstImport(dummyImport, anchor)
        val psiAnchor = PsiAnchor.create(inserted)
        val rangeInfo = ImportRangeInfo(psiAnchor, psiAnchor, Seq((dummyImport, importInfosToAdd)), usedImportedNames = Set.empty, isLocal = false)
        val infosToAdd = ScalaImportOptimizer.optimizedImportInfos(rangeInfo, settings)

        Some((rangeInfo, infosToAdd))
      }
      else {
        val sortedRanges = importRanges.toSeq.sortBy(_.startOffset)
        val selectedRange: Option[ImportRangeInfo] =
          if (refsContainer != null && ScalaCodeStyleSettings.getInstance(getProject).isAddImportMostCloseToReference)
            sortedRanges.findLast(_.endOffset < refsContainer.getTextRange.getStartOffset)
          else
            sortedRanges.headOption

        selectedRange.map { rangeInfo =>
          val importInfos = rangeInfo.importStmtWithInfos.flatMap(_._2)
          val resultInfos = ScalaImportOptimizer.insertImportInfos(importInfosToAdd, importInfos, rangeInfo.firstPsi, settings)
          (rangeInfo, resultInfos)
        }
      }

    insertionRangeWithImportsToAdd match {
      case Some((insertionRange, resultInfos)) =>
        optimizer.replaceWithNewImportInfos(insertionRange, resultInfos, settings, containingFile)
      case _ =>
    }
  }

  def bindImportSelectorOrExpression(
    importExpr: ScImportExpr,
    oldImportExprOrSelectorToDelete: PsiElement,
    newImportPath: ImportPath,
  ): Unit = {
    // This method must not be called with an ScImportExpr that is inside an ScExportStmt
    assert(importExpr.getParent.is[ScImportStmt])
    val importStmt = importExpr.getParent.asInstanceOf[ScImportOrExportStmt]

    val scalaFile = this.getContainingFile match {
      case file: ScalaFile =>
        file
      case other =>
        Log.error(new IncorrectOperationException(s"Containing file should be ScalaFile, but got: ${Option(other).map(_.getClass.getName).orNull}"))
        return
    }


    val optimizer = ScalaImportOptimizer.findScalaOptimizerFor(scalaFile) match {
      case Some(o) => o
      case _ =>
        Log.error(new IncorrectOperationException(s"Can't find scala optimizer for file: ${scalaFile.getName} / ${scalaFile.getVirtualFile}"))
        return
    }

    val settings = OptimizeImportSettings(scalaFile)

    val oldImportStatementOffset = importStmt.startOffset

    val isLastRemainingImportToBeDeleted: Boolean = {
      val willImportStmtBeDeleted = importStmt.importExprs.size == 1 &&
        !importExpr.hasWildcardSelector &&
        importExpr.selectorSet.forall(_.selectors.sizeIs == 1)

      val hasSiblingImportStatements = importStmt.getNextSiblingNotWhitespace.is[ScImportStmt] || importStmt.getPrevSiblingNotWhitespace.is[ScImportStmt]
      willImportStmtBeDeleted && !hasSiblingImportStatements
    }
    if (isLastRemainingImportToBeDeleted) {
      /**
       * We need to handle last remaining import in a special way.
       * Because otherwise, if we remove the import there will be no import range info for it.
       * But we want to preserve old local import position, during e.g. "Move" refactoring<br>
       * For example, after moving X, Y, Z imports position should be the same and not moved to the start of the block: {{{
       *   import org.example.X
       *   val xxx: X = ???
       *
       *   import org.example.Y
       *   val yyy: Y = ???
       *
       *   import org.example.Z
       *   val zzz: Z = ???
       * }}}
       */
      val newImportStatementOpt = this.newImportStatement(newImportPath, settings, optimizer)
      newImportStatementOpt match {
        case Some(newImportStatement) =>
          if (this.canSkipImport(newImportPath)) {
            this.deleteImportOrExportStmt(importStmt)
          }
          else {
            importStmt.replace(newImportStatement)
          }
        case _ =>
          Log.error(new IncorrectOperationException(s"Can't create new import statement when binding element (qualifiedName: ${newImportPath.qualifiedName})"))
      }
    }
    else {
      oldImportExprOrSelectorToDelete match {
        case s: ScImportSelector =>
          s.deleteSelector(removeRedundantBraces = false)
        case e: ScImportExpr  =>
          e.deleteExpr()
      }
      this.insertImportPathToTheBestPlaceDuringRefactoring(
        newImportPath,
        oldImportStatementOffset,
        scalaFile,
        settings,
        optimizer
      )
    }
  }

  /**
   * The method is similar to [[addImportsForPathsImpl]] but is dedicated for adding single import path
   * without touching all other imports in the same range even if they are not sorted/optimized.
   *
   * @see [[addImportsForPathsImpl]] for implementation details
   */
  private def insertImportPathToTheBestPlaceDuringRefactoring(
    path: ImportPath,
    oldImportStatementOffset: Int,
    scalaFile: ScalaFile,
    fileOptimizeSettings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer
  ): Unit = {
    if (canSkipImport(path))
      return

    //what is this `place` for???
    val place = getImportStatements.lastOption.getOrElse(getFirstChild.getNextSibling)

    val newImportInfoCandidate = createInfoFromPath(path, place)
    val newImportInfo = newImportInfoCandidate match {
      case Some(info) if hasValidQualifier(info, place) =>
        info
      case _ =>
        return
    }

    //don't add wildcard imports here, it should be done only on explicit "Optimize Imports" action
    val settings = fileOptimizeSettings.withoutCollapseSelectorsToWildcard

    //NOTE: I don't know any conditions when the error can occur
    //  if it occurs, please add such case to unit tests
    def error(message: String): Unit = {
     Log.error(s"insertImportPathToTheBestPlaceDuringRefactoring: $message" +
       s" (scalaFile: $scalaFile, import: $path, oldImportStatementOffset: ${oldImportStatementOffset})")
    }

    val existingImportRanges: Set[ImportRangeInfo] = optimizer.collectImportRanges(this)
    if (existingImportRanges.isEmpty) {
      error(s"expecting at least single import statement")
    }
    else {
      val importRangesSorted: Seq[ImportRangeInfo] = existingImportRanges.toSeq.sortBy(_.startOffset)

      val selectedImportRange: Option[ImportRangeInfo] =
        importRangesSorted.find(_.rangeCanAccept(oldImportStatementOffset))

      selectedImportRange match {
        case Some(rangeInfo) =>
          val insertionPlace = ScalaImportOptimizer.bestPlaceToInsertNewImport(newImportInfo, rangeInfo, settings)
          insertToBestPlace(newImportInfo, insertionPlace, scalaFile, settings, optimizer)
        case None =>
          error("can't detect best insertion place")
      }
    }
  }

  private def insertToBestPlace(
    newImportInfo: ImportInfo,
    insertionPlace: ImportInsertionPlace,
    scalaFile: ScalaFile,
    settings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer
  ): Unit = {
     insertionPlace match {
      case ImportInsertionPlace.InsertFirst(rangeInfo) =>
        val textCreator = optimizer.getImportTextCreator
        val newImportText = textCreator.getImportText(newImportInfo, settings)
        val newImport = ScalaPsiElementFactory.createImportFromText(newImportText, this)
        addImportBefore(newImport, rangeInfo.firstPsi.retrieve())

      case ImportInsertionPlace.InsertAfterStatement(range, importStmtIdx) =>
        val textCreator = optimizer.getImportTextCreator
        val newImportText = textCreator.getImportText(newImportInfo, settings)
        val newImport = ScalaPsiElementFactory.createImportFromText(newImportText, this)
        val importStmt = range.importStmtWithInfos(importStmtIdx)._1
        addImportAfter(newImport, importStmt)

      case ImportInsertionPlace.MergeInto(range, importStmtIdx) =>
        //replace only single import statement infos, do not touch other sibling imports
        val (importStmt, importStmtInfos) = range.importStmtWithInfos(importStmtIdx)
        val importInfosMerged = ScalaImportOptimizer.sortAndMergeImports(importStmtInfos :+ newImportInfo, settings)

        optimizer.replaceWithNewImportInfos(
          firstPsi = importStmt,
          lastPsi = importStmt,
          startOffset = importStmt.startOffset,
          importInfos = importInfosMerged,
          settings = settings,
          file = scalaFile
        )
    }
  }

  private def newImportStatement(
    importPath: ImportPath,
    settings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer
  ): Option[ScImportStmt] = {
    val place: PsiElement =  null // TODO: what is this parameter for? Add docs to the usage place
    val importInfoOpt = createInfoFromPath(importPath, place)
    val importInfo = importInfoOpt match {
      case Some(info) if hasValidQualifier(info, place) =>
        info
      case _ =>
        return None
    }
    val textCreator = optimizer.getImportTextCreator

    val newImportText = textCreator.getImportText(importInfo, settings)
    val newImport = ScalaPsiElementFactory.createImportFromText(newImportText, this)
    Some(newImport)
  }

  private def canSkipImport(importPath: ImportPath): Boolean =
    importPath.aliasName.isEmpty && isFromSamePackage(importPath.qualifiedName)

  private def isFromSamePackage(otherPath: String): Boolean = {
    val otherPathQualifier: Option[String] = {
      val ref = ScalaPsiElementFactory.createReferenceFromText(otherPath)
      Option(ref).flatMap(_.qualifier.map(_.getText))
    }

    val ourPackageName = this.parentOfType(classOf[ScPackaging], strict = false).map(_.fullPackageName)
    ourPackageName.contains(otherPathQualifier.getOrElse(""))
  }

  private def hasValidQualifier(importInfo: ImportInfo, place: PsiElement): Boolean = {
    val ref = ScalaPsiElementFactory.createReferenceFromText(importInfo.prefixQualifier, this, place)
    ref.multiResolveScala(false).nonEmpty
  }

  private def createInfoFromPath(path: ImportPath, place: PsiElement): Option[ImportInfo] = {
    val importExprText = path.importExpressionText
    val importExpr = ScalaPsiElementFactory.createImportExprFromText(importExprText, this, place)
    ImportInfo.create(importExpr, _ => true)
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

  private val Log = Logger.getInstance(classOf[ScImportsHolder])

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
    forNewImportInsertion(place)(project)

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
    import scala.language.existentials

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