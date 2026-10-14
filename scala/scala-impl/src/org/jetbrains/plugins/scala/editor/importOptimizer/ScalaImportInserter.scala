package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer.ImportInsertionPlace
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder.ImportPath
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ImportInserter, ScImportsHolder}
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

final class ScalaImportInserter extends ImportInserter {

  import ScalaImportInserter._

  override def addImportsForPaths(
    holder: ScImportsHolder,
    paths: Seq[ImportPath],
    @Nullable refsContainer: PsiElement
  ): Unit = {
    val file = holder.getContainingFile match {
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

    addImportsForPathsImpl(holder, paths, file, settings, optimizer, refsContainer)
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
    holder: ScImportsHolder,
    paths: Seq[ImportPath],
    containingFile: ScalaFile,
    fileOptimizeSettings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer,
    @Nullable refsContainer: PsiElement
  ): Unit = {

    //don't add wildcard imports here, it should be done only on explicit "Optimize Imports" action
    val settings = fileOptimizeSettings.withoutCollapseSelectorsToWildcard

    val place = holder.getImportStatements.lastOption.getOrElse(holder.getFirstChild.getNextSibling)

    val importInfosToAdd: Seq[ImportInfo] = paths
      .filterNot(p => isFromSamePackage(holder, p.qualifiedName))
      .flatMap(createInfoFromPath(holder, _, place))
      .filter(hasValidQualifier(holder, _, place))

    val importRanges = optimizer.collectImportRanges(holder)

    val needToInsertFirst =
      if (importRanges.isEmpty) true
      else refsContainer == null && holder.hasCodeBeforeImports

    val insertionRangeWithImportsToAdd: Option[(ImportRangeInfo, Seq[ImportInfo])] =
      if (needToInsertFirst) {

        def determineAnchor(): PsiElement = {
          def isDirective(el: PsiElement): Boolean = el.is[ScDirective]

          def isWhitespaceOrCommentOrDirective(el: PsiElement): Boolean = el.isWhitespaceOrComment || isDirective(el)

          val firstElement: PsiElement = holder.getFirstChild

          (firstElement +: firstElement.nextSiblings.toSeq)
            .takeWhile(isWhitespaceOrCommentOrDirective)
            .findLast(isDirective)
            .map(_.getNextSibling).getOrElse(firstElement)
        }

        //ScalaImportOptimizer only works with import ranges
        //So we insert a temporary dummy import to simply replace it with a well-formatted import
        val dummyImport: ScImportStmt = ScalaPsiElementFactory.createImportFromText("import dummy.dummy", holder)

        val anchor = determineAnchor()

        val inserted = holder.insertFirstImportStatement(dummyImport, anchor)
        val psiAnchor = PsiAnchor.create(inserted)
        val rangeInfo = ImportRangeInfo(psiAnchor, psiAnchor, Seq((dummyImport, importInfosToAdd)), usedImportedNames = Set.empty, isLocal = false)
        val infosToAdd = ScalaImportOptimizer.optimizedImportInfos(rangeInfo, settings)

        Some((rangeInfo, infosToAdd))
      }
      else {
        val sortedRanges = importRanges.toSeq.sortBy(_.startOffset)
        val selectedRange: Option[ImportRangeInfo] =
          if (refsContainer != null && ScalaCodeStyleSettings.getInstance(holder.getProject).isAddImportMostCloseToReference)
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

  override def bindImportSelectorOrExpression(
    holder: ScImportsHolder,
    importExpr: ScImportExpr,
    oldImportExprOrSelectorToDelete: PsiElement,
    newImportPath: ImportPath,
  ): Unit = {
    // This method must not be called with an ScImportExpr that is inside an ScExportStmt
    assert(importExpr.getParent.is[ScImportStmt])
    val importStmt = importExpr.getParent.asInstanceOf[ScImportOrExportStmt]

    val scalaFile = holder.getContainingFile match {
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
      val newImportStatementOpt = newImportStatement(holder, newImportPath, settings, optimizer)
      newImportStatementOpt match {
        case Some(newImportStatement) =>
          if (canSkipImport(holder, newImportPath)) {
            holder.deleteImportOrExportStmt(importStmt)
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
      insertImportPathToTheBestPlaceDuringRefactoring(
        holder,
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
    holder: ScImportsHolder,
    path: ImportPath,
    oldImportStatementOffset: Int,
    scalaFile: ScalaFile,
    fileOptimizeSettings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer
  ): Unit = {
    if (canSkipImport(holder, path))
      return

    //what is this `place` for???
    val place = holder.getImportStatements.lastOption.getOrElse(holder.getFirstChild.getNextSibling)

    val newImportInfoCandidate = createInfoFromPath(holder, path, place)
    val newImportInfo = newImportInfoCandidate match {
      case Some(info) if hasValidQualifier(holder, info, place) =>
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

    val existingImportRanges: Set[ImportRangeInfo] = optimizer.collectImportRanges(holder)
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
          insertToBestPlace(holder, newImportInfo, insertionPlace, scalaFile, settings, optimizer)
        case None =>
          error("can't detect best insertion place")
      }
    }
  }

  private def insertToBestPlace(
    holder: ScImportsHolder,
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
        val newImport = ScalaPsiElementFactory.createImportFromText(newImportText, holder)
        holder.addImportBefore(newImport, rangeInfo.firstPsi.retrieve())

      case ImportInsertionPlace.InsertAfterStatement(range, importStmtIdx) =>
        val textCreator = optimizer.getImportTextCreator
        val newImportText = textCreator.getImportText(newImportInfo, settings)
        val newImport = ScalaPsiElementFactory.createImportFromText(newImportText, holder)
        val importStmt = range.importStmtWithInfos(importStmtIdx)._1
        holder.addImportAfter(newImport, importStmt)

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
    holder: ScImportsHolder,
    importPath: ImportPath,
    settings: OptimizeImportSettings,
    optimizer: ScalaImportOptimizer
  ): Option[ScImportStmt] = {
    val place: PsiElement =  null // TODO: what is this parameter for? Add docs to the usage place
    val importInfoOpt = createInfoFromPath(holder, importPath, place)
    val importInfo = importInfoOpt match {
      case Some(info) if hasValidQualifier(holder, info, place) =>
        info
      case _ =>
        return None
    }
    val textCreator = optimizer.getImportTextCreator

    val newImportText = textCreator.getImportText(importInfo, settings)
    val newImport = ScalaPsiElementFactory.createImportFromText(newImportText, holder)
    Some(newImport)
  }
}

private object ScalaImportInserter {

  private val Log = Logger.getInstance(classOf[ScImportsHolder])

  private def canSkipImport(holder: ScImportsHolder, importPath: ImportPath): Boolean =
    importPath.aliasName.isEmpty && isFromSamePackage(holder, importPath.qualifiedName)

  private def isFromSamePackage(holder: ScImportsHolder, otherPath: String): Boolean = {
    val otherPathQualifier: Option[String] = {
      val ref = ScalaPsiElementFactory.createReferenceFromText(otherPath)(using holder.projectContext)
      Option(ref).flatMap(_.qualifier.map(_.getText))
    }

    val ourPackageName = holder.parentOfType(classOf[ScPackaging], strict = false).map(_.fullPackageName)
    ourPackageName.contains(otherPathQualifier.getOrElse(""))
  }

  private def hasValidQualifier(holder: ScImportsHolder, importInfo: ImportInfo, place: PsiElement): Boolean = importInfo.prefixQualifier.exists { prefixQualifier =>
    val ref = ScalaPsiElementFactory.createReferenceFromText(prefixQualifier, holder, place)
    ref.multiResolveScala(false).nonEmpty
  }

  private def createInfoFromPath(holder: ScImportsHolder, path: ImportPath, place: PsiElement): Option[ImportInfo] = {
    val importExprText = path.importExpressionText
    val importExpr = ScalaPsiElementFactory.createImportExprFromText(importExprText, holder, place)
    ImportInfo.create(importExpr, _ => true)
  }
}
