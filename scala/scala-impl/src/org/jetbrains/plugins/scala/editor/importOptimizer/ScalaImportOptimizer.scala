package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.concurrency.JobLauncher
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.ImportOptimizer.CollectingInfoRunnable
import com.intellij.lang.{ASTNode, ImportOptimizer, LanguageImportStatements}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.annotator.usageTracker.RedundantImportUtils
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFor, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.sbt.language.SbtFile

import java.util
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import scala.util.chaining.scalaUtilChainingOps

class ScalaImportOptimizer(isOnTheFly: Boolean) extends ImportOptimizer {

  def this() = this(isOnTheFly = false)

  import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._

  protected def settings(file: PsiFile): OptimizeImportSettings = OptimizeImportSettings(file)

  override def processFile(file: PsiFile): Runnable = {
    processFile(file, null)
  }

  def processFile(
    file: PsiFile,
    @Nullable progressIndicator: ProgressIndicator = null
  ): Runnable = {
    val scalaFile: ScalaFile = file match {
      case scFile: ScalaFile =>
        scFile
      case multiRootFile: PsiFile if multiRootFile.getViewProvider.getLanguages.contains(ScalaLanguage.INSTANCE) =>
        multiRootFile.getViewProvider.getPsi(ScalaLanguage.INSTANCE).asInstanceOf[ScalaFile]
      case _ =>
        return EmptyRunnable.getInstance()
    }

    ScalaActionUsagesCollector.logOptimizeImports(scalaFile.getProject)

    val project: Project = scalaFile.getProject
    val documentManager = PsiDocumentManager.getInstance(project)
    val document: Document = documentManager.getDocument(scalaFile)
    val analyzingDocumentText = document.getText

    val usedImports = ContainerUtil.newConcurrentSet[ImportUsed]()
    val usedImportedNames = ContainerUtil.newConcurrentSet[UsedName]()

    val (importHolders, importUsers) = collectImportHoldersAndUsers(scalaFile)

    val potentialRedundantImports: collection.Set[ImportUsed] =
      RedundantImportUtils.collectPotentiallyRedundantImports(scalaFile)

    val progressManager: ProgressManager = ProgressManager.getInstance()
    val indicator: ProgressIndicator =
      if (progressIndicator != null) progressIndicator
      else if (progressManager.hasProgressIndicator) progressManager.getProgressIndicator
      else null

    if (indicator != null) {
      indicator.setText2(ScalaEditorBundle.message("imports.analyzing.usage", file.name))
    }

    val size = importHolders.size + importUsers.size //processAllElementsConcurrentlyUnderProgress will be called 2 times
    val counter = new AtomicInteger(0)

    def processAllElementsConcurrentlyUnderProgress[T <: PsiElement](elements: util.List[T])(action: T => Unit) = {
      if (indicator != null) {
        indicator.setIndeterminate(false)
      }
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, indicator, true, false, (element: T) => {
        val count: Int = counter.getAndIncrement
        if (count <= size && indicator != null) {
          indicator.setFraction(count.toDouble / size)
        }

        action(element)

        true
      })
    }

    processAllElementsConcurrentlyUnderProgress(importUsers) { element =>
      collectImportsUsed(element, usedImports, usedImportedNames)
    }

    if (indicator != null) {
      indicator.setText2(ScalaEditorBundle.message("imports.collecting.additional.info", file.name))
    }

    def collectRanges(createInfo: ScImportStmt => Seq[ImportInfo]): Seq[ImportRangeInfo] = {
      val importsInfo = ContainerUtil.newConcurrentSet[ImportRangeInfo]()
      processAllElementsConcurrentlyUnderProgress(importHolders) {
        case holder: ScImportsHolder =>
          importsInfo.addAll(collectImportRanges(holder, createInfo, usedImportedNames.asScala.toSet).asJava)
        case _ =>
      }
      importsInfo.asScala.toSeq.sortBy(_.startOffset)
    }

    val importsSettings = settings(scalaFile)

    def isRedundant(importUsed: ImportUsed): Boolean =
      potentialRedundantImports.contains(importUsed) &&
        RedundantImportUtils.isActuallyRedundant(importUsed, project, scalaFile.isScala3File)

    def isImportUsed(importUsed: ImportUsed): Boolean = {
      //todo: collect proper information about language features
      importUsed.isAlwaysUsed ||
        usedImports.contains(importUsed) && !isRedundant(importUsed) ||
        ScalaImportOptimizerHelper.extensions.exists(_.isImportUsed(importUsed)) ||
        isOnTheFly && !mayOptimizeOnTheFly(importUsed)
    }

    val rangeInfos = collectRanges(ImportInfo.createInfos(_, isImportUsed))

    if (indicator != null) {
      indicator.setText2(ScalaEditorBundle.message("imports.optimizing", file.name))
    }

    val rangeInfosTotal = rangeInfos.size
    val optimized = rangeInfos.zipWithIndex.map { case (range, idx) =>
      if (indicator != null) {
        indicator.setFraction(idx.toDouble / rangeInfosTotal)
      }

      (range, optimizedImportInfos(range, importsSettings))
    }

    new CollectingInfoRunnable {
      private var optimizationResult = ImportOptimizationResult.Empty

      override def run(): Unit = {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document: Document = documentManager.getDocument(scalaFile)
        documentManager.commitDocument(document)

        val ranges: Seq[(ImportRangeInfo, Seq[ImportInfo])] =
          if (document.getText != analyzingDocumentText)  //something was changed...
            sameInfosWithUpdatedRanges()
          else optimized

        for ((range, importInfos) <- ranges.reverseIterator) {
          optimizationResult += replaceWithNewImportInfos(range, importInfos, importsSettings, scalaFile)
        }
        documentManager.commitDocument(document)
      }

      def sameInfosWithUpdatedRanges(): Seq[(ImportRangeInfo, Seq[ImportInfo])] = {
        optimized.zip {
          collectRanges(_ => Seq.empty)
        }.map {
          case ((_, infos), range) => (range, infos)
        }
      }

      override def getUserNotificationInfo: String = {
        val ImportOptimizationResult(deleted, inserted, rearranged) = optimizationResult

        if (deleted == 0) {
          if (rearranged) ScalaEditorBundle.message("import.optimizer.hint.rearranged.imports")
          else null
        } else {
          //noinspection ReferencePassedToNls
          ScalaEditorBundle.message("import.optimizer.hint.removed.imports", deleted, if (deleted == 1) 0 else 1)
            .pipe { notification =>
              if (inserted > 0)
                notification + ScalaEditorBundle.message("import.optimizer.hint.added.imports", inserted, if (inserted == 1) 0 else 1)
              else notification
            }
        }
      }
    }
  }

  def getImportTextCreator: ImportTextCreator = new ImportTextCreator

  protected def isImportDelimiter(psi: PsiElement): Boolean = psi.is[PsiWhiteSpace]

  //we should not apply this to Play2ScalaFile, it is covered by Play2ImportOptimizer
  override def supports(file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile =>
        supportsImpl(scalaFile)
      case _ =>
        false
    }

  private def supportsImpl(file: ScalaFile): Boolean = {
    // show changed imports in intention preview
    if (IntentionPreviewUtils.isPreviewElement(file))
      return true

    //don't process play2 templates
    if (file.getVirtualFile.getName.endsWith(".html"))
      return false

    val vFile = file.getViewProvider.getVirtualFile
    val projectRootManager = ProjectRootManager.getInstance(file.getProject)
    val fileIndex = projectRootManager.getFileIndex

    //only process
    // - files from sources and scratch files, this is a similar to Java & Kotlin (see IDEA-136712)
    // - synthetic files created for Scala REPL (see SCL-20571)
    // - sbt files that are not considered "in source" but are still "in project" (e.g.: top-level build.sbt, see SCL-21336)
    fileIndex.isInSource(vFile) ||
      ScratchUtil.isScratch(vFile) ||
      ScalaLanguageConsole.ScalaConsoleFileMarkerKey.isIn(vFile) ||
      file.isInstanceOf[SbtFile] && fileIndex.isInProject(vFile)
  }

  def replaceWithNewImportInfos(
    range: ImportRangeInfo,
    importInfos: Iterable[ImportInfo],
    settings: OptimizeImportSettings,
    file: ScalaFile
  ): ImportOptimizationResult = {
    replaceWithNewImportInfos(
      range.firstPsi.retrieve(),
      range.lastPsi.retrieve(),
      range.startOffset,
      importInfos,
      settings,
      file
    )
  }

  def replaceWithNewImportInfos(
    firstPsi: PsiElement,
    lastPsi: PsiElement,
    startOffset: Int,
    importInfos: Iterable[ImportInfo],
    settings: OptimizeImportSettings,
    file: ScalaFile
  ): ImportOptimizationResult = {

    def notValid(psi: PsiElement) = psi == null || !psi.isValid

    if (notValid(firstPsi) || notValid(lastPsi)) {
      throw new IllegalStateException("Couldn't update imports: import range was invalidated after initial analysis")
    }

    val textCreator = getImportTextCreator
    val fileText = file.getText

    @tailrec
    def indentForOffset(index: Int, res: String = ""): String = {
      if (index <= 0) res
      else {
        val c = fileText.charAt(index - 1)
        if (c == ' ' || c == '\t') indentForOffset(index - 1, s"$c$res")
        else res
      }
    }
    val newLineWithIndent: String = "\n" + indentForOffset(startOffset)

    var prevGroupIndex = -1
    def groupSeparatorsBefore(currentGroupIndex: Int) = {
      if (currentGroupIndex <= prevGroupIndex || prevGroupIndex == -1) ""
      else if (settings.scalastyleGroups.nonEmpty) newLineWithIndent
      else {
        def isBlankLine(i: Int) = settings.importLayout(i) == BLANK_LINE
        val blankLineNumber =
          Range(currentGroupIndex - 1, prevGroupIndex, -1).dropWhile(!isBlankLine(_)).takeWhile(isBlankLine).size
        newLineWithIndent * blankLineNumber
      }
    }

    val importInfosTexts = importInfos.map { info =>
      val index: Int = findGroupIndex(info.prefixQualifier, info.relative, settings)
      val blankLines = groupSeparatorsBefore(index)
      prevGroupIndex = index
      blankLines + textCreator.getImportText(info, settings)
    }
    val text = importInfosTexts.mkString(newLineWithIndent).replaceAll("""\n[ \t]+\n""", "\n\n")

    //it should cover play template files
    val fileFactory = PsiFileFactory.getInstance(file.getProject)

    val dummyFile = fileFactory.createFileFromText(
      "dummy." + file.getFileType.getDefaultExtension,
      // a small hack to make this work with source3
      if (file.isSource3Enabled) Scala3Language.INSTANCE else file.getLanguage,
      text,
      /*eventSystemEnabled = */ false, /*markAsCopy = */ false)

    val errorElements = dummyFile.children.filterByType[PsiErrorElement].map(_.getNode)
    errorElements.foreach(dummyFile.getNode.removeChild)

    val parentNode = firstPsi.getParent.getNode
    val firstPsiNode = firstPsi.getNode
    val firstNodeToRemove =
      if (text.isEmpty) {
        val prevNode = firstPsiNode.getTreePrev
        if (prevNode != null && ScalaTokenTypes.WHITES_SPACES_TOKEN_SET.contains(prevNode.getElementType)) prevNode
        else firstPsiNode
      }
      else firstPsiNode

    val buffer = AstChildrenBuffer(parentNode, firstNodeToRemove, lastPsi.getNode)

    withDisabledPostprocessFormatting(file.getProject) {
      //replace import statements incrementally instead of replacing the whole range
      //originally added to reduce  number of invalidated elements during refactorings
      //(currently it may not be used in move refactoring)
      val finalResult = dummyFile.getNode.getChildren(null)
      ImportOptimizationResult.calculate(buffer.asArray, finalResult)
        .tap(_ => BufferUpdate.updateIncrementally(buffer, finalResult)(_.getText))
    }
  }

  def collectImportRanges(
    holder: ScImportsHolder
  ): Set[ImportRangeInfo] =
    collectImportRanges(
      holder,
      Set.empty
    )

  def collectImportRanges(
    holder: ScImportsHolder,
    allUsedImportedNames: Set[UsedName]
  ): Set[ImportRangeInfo] =
    collectImportRanges(
      holder,
      ImportInfo.createInfos(_),
      allUsedImportedNames
    )

  def collectImportRanges(
    holder: ScImportsHolder,
    createInfo: ScImportStmt => Seq[ImportInfo],
    allUsedImportedNames: Set[UsedName]
  ): Set[ImportRangeInfo] = {
    val result = mutable.HashSet[ImportRangeInfo]()

    val isLocalRange = isLocalImportHolder(holder)

    val allUsedImportedNamesSorted = allUsedImportedNames.toSeq.sortBy(_.offset)

    val addRangeAndReset: (PsiElement, PsiElement, Seq[ScImportStmt]) => Unit =
      (firstPsi, lastPsi, importStmts) => {
        val rangeStart = firstPsi.getTextRange.getStartOffset
        val usedImportedNames = allUsedImportedNamesSorted.dropWhile(_.offset < rangeStart).map(_.name).toSet

        val importStmtWithInfos: Seq[(ScImportStmt, Seq[ImportInfo])] =
          importStmts.map(importStmt => (importStmt, createInfo(importStmt)))

        val rangeInfo = ImportRangeInfo(PsiAnchor.create(firstPsi), PsiAnchor.create(lastPsi), importStmtWithInfos.toVector, usedImportedNames, isLocalRange)
        result += rangeInfo
      }

    collectImportRangesCore(holder, addRangeAndReset)

    result.toSet
  }

  private def collectImportRangesCore(
    importHolder: ScImportsHolder,
    addRangeAndResetCustom: (PsiElement, PsiElement, Seq[ScImportStmt]) => Unit,
  ): Unit = {
    val holderChildren = importHolder.getNode.getChildren(null)
    collectImportRangesCore(holderChildren, addRangeAndResetCustom)
  }

  private def collectImportRangesCore(
    importHolderChildren: Iterable[ASTNode],
    addRangeAndResetCustom: (PsiElement, PsiElement, Seq[ScImportStmt]) => Unit,
  ): Unit = {
    var firstPsi: PsiElement = null
    var lastPsi: PsiElement = null
    val importStatements = mutable.ArrayBuffer[ScImportStmt]()

    def addRangeAndReset(): Unit = {
      if (firstPsi != null && lastPsi != null) {
        addRangeAndResetCustom(firstPsi, lastPsi, importStatements.toSeq)

        firstPsi = null
        lastPsi = null
        importStatements.clear()
      }
    }

    def initRange(psi: PsiElement): Unit = {
      firstPsi = psi
      lastPsi = psi
    }

    for (child <- importHolderChildren) {
      child.getPsi match {
        case _: PsiWhiteSpace =>
        case _: ScDocComment =>
          addRangeAndReset()
        case comment: PsiComment =>
          if (isSurroundedWithNewLines(comment)) {
            addRangeAndReset()
          }
        case _: LeafPsiElement =>
        case a: PsiElement if isImportDelimiter(a) => //do nothing
        case imp: ScImportStmt =>
          importStatements += imp
          if (firstPsi == null) {
            imp.getPrevSibling match {
              case a: PsiElement if isImportDelimiter(a) && !a.is[PsiWhiteSpace] =>
                initRange(a)
                lastPsi = imp
              case _ =>
                initRange(imp)
            }
          }
          else {
            lastPsi = imp
          }
        case _ =>
          addRangeAndReset()
      }
    }

    addRangeAndReset()
  }

  private def isSurroundedWithNewLines(element: PsiElement): Boolean = {
    val next = element.getNextSibling
    val prev = element.getPrevSibling
    (next, prev) match {
      case (w1: PsiWhiteSpace, w2: PsiWhiteSpace)  =>
        w1.textContains('\n') && w2.textContains('\n')
      case _ =>
        false
    }
  }

  /**
   * Example:<br>
   * Before: {{{
   *   import org.example.{A, B, C, _}
   *   val a: A = ???
   * }}}
   * After: {{{
   *   import org.example.{A, _}
   *   val a: A = ???
   * }}}
   *
   */
  @RequiresWriteLock
  def removeAllUnusedSingleNamesInImportsWithWildcards(
    importHolder: ScImportsHolder,
    settings0: OptimizeImportSettings
  ): Unit = {
    // we want to just remove single names, but not collapse import to wildcard import
    val settings = settings0.withoutCollapseSelectorsToWildcard

    val hasWildcards = importHolder.getImportStatements.exists(_.importExprs.exists(_.hasWildcardSelector))
    if (!hasWildcards) {
      return
    }

    val rangeInfos = collectImportRanges(importHolder)

    val allUsedImportNames = collectUsedImportedNames(importHolder)

    val allImportInfosFromHolder = rangeInfos.flatMap(_.importStmtWithInfos.flatMap(_._2))

    for {
      rangeInfo <- rangeInfos
      (importStmt, _) <- rangeInfo.importStmtWithInfos
    } {
      val currentStmtImportInfos: Seq[ImportInfo] = ImportInfo.createInfos(importStmt)

      val importInfosNewOpts: Seq[Option[ImportInfo]] = for {
        info <- currentStmtImportInfos
      } yield {
        if (info.hasWildcard)
          filterClashingSingleNamesForWildcardImport(
            info, info, allImportInfosFromHolder, importStmt, allUsedImportNames
          )
        else
          None
      }

      val modified = importInfosNewOpts.exists(_.isDefined)
      if (modified) {
        val importInfosNew = importInfosNewOpts.collect { case Some(info) => info }
        val textCreator = getImportTextCreator
        val texts = importInfosNew.map(textCreator.getImportExprText(_, settings))
        val newImportStmtText = s"import ${texts.mkString(", ")}"

        val newImport = ScalaPsiElementFactory.createImportFromText(newImportStmtText, importHolder)
        importStmt.replace(newImport)
      }
    }
  }

}

object ScalaImportOptimizer {

  private case class UsedName(name: String, offset: Int)

  /** Import optimization action run statistics
   *
   * @param deletedImports  number of removed imports
   * @param insertedImports number of inserted imports
   * @param rearranged      whether the imports are the same but in another order
   */
  final case class ImportOptimizationResult(deletedImports: Int, insertedImports: Int, rearranged: Boolean) {
    def +(that: ImportOptimizationResult): ImportOptimizationResult =
      ImportOptimizationResult(
        deletedImports = this.deletedImports + that.deletedImports,
        insertedImports = this.insertedImports + that.insertedImports,
        rearranged = this.rearranged || that.rearranged
      )
  }

  object ImportOptimizationResult {
    val Empty: ImportOptimizationResult =
      ImportOptimizationResult(deletedImports = 0, insertedImports = 0, rearranged = false)

    def calculate(nodesBefore: Array[ASTNode], nodesAfter: Array[ASTNode]): ImportOptimizationResult = {
      val oldImports = collectImports(nodesBefore)
      val newImports = collectImports(nodesAfter)

      val sameLength = oldImports.length == newImports.length
      val nothingChanged = sameLength && oldImports == newImports

      if (nothingChanged) ImportOptimizationResult.Empty
      else {
        val deleted = oldImports.diff(newImports).length
        val added = newImports.diff(oldImports).length
        val rearranged = sameLength && deleted == 0 && added == 0
        ImportOptimizationResult(deleted, added, rearranged)
      }
    }

    private final case class ImportDescriptor(path: String, nameOrSelector: String)

    private object ImportDescriptor {
      def apply(i: ScImportExpr): Seq[ImportDescriptor] = {
        val qualifier = i.qualifier.fold("")(_.qualName)

        i.selectorSet match {
          case Some(selectorSet) => selectorSet.selectors.map(s => ImportDescriptor(qualifier, s.getText))
          case None => i.reference.map(r => ImportDescriptor(qualifier, r.refName)).toList
        }
      }
    }

    private def collectImports(nodes: Array[ASTNode]): List[ImportDescriptor] =
      nodes.toList
        .flatMap(_.getPsi.asOptionOf[ScImportStmt])
        .flatMap(_.importExprs)
        .flatMap(ImportDescriptor(_))
  }

  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty
  val _root_prefix = "_root_"

  def findOptimizerFor(file: ScalaFile): Option[ImportOptimizer] = {
    val topLevelFile = file.getViewProvider.getPsi(file.getViewProvider.getBaseLanguage)
    val optimizers = LanguageImportStatements.INSTANCE.forFile(topLevelFile)
    if (optimizers.isEmpty)
      return None

    val topLevelViewProvider = topLevelFile.getViewProvider
    val hasScalaLanguage =
      topLevelViewProvider.getBaseLanguage.isKindOf(ScalaLanguage.INSTANCE) ||
        topLevelViewProvider.getPsi(ScalaLanguage.INSTANCE) != null

    if (!hasScalaLanguage)
      return None

    val i = optimizers.iterator()
    while (i.hasNext) {
      val opt = i.next()
      if (opt supports topLevelFile) {
        return Some(opt)
      }
    }
    None
  }

  def findScalaOptimizerFor(file: ScalaFile): Option[ScalaImportOptimizer] =
    findOptimizerFor(file).filterByType[ScalaImportOptimizer]

  class ImportTextCreator {

    private case class ImportTextData(prefix: String, dotOrNot: String, postfix: String) {
      def fullText: String = s"import $expressionText"
      def expressionText: String = s"$prefix$dotOrNot$postfix"

      //see ScalastyleSettings.compareImports
      def forScalastyleSorting: String = {
        if (postfix.startsWith("{")) prefix + dotOrNot
        else prefix + dotOrNot + postfix
      }
    }

    private def getImportTextData(importInfo: ImportInfo, options: ImportTextGenerationOptions): ImportTextData = {
      val groupStrings = new ArrayBuffer[String]

      def addGroup(names: Iterable[String]) = {
        if (options.nameOrdering.isDefined)
          groupStrings ++= names.toSeq.sorted(options.nameOrdering.get)
        else
          groupStrings ++= names
      }

      val scala3SyntaxAllowed =
        options.scalaFeatures.isScala3 || !options.forceScala2SyntaxInSource3
      val useScala3RenamingImports =
        options.scalaFeatures.`Scala 3 renaming imports` && scala3SyntaxAllowed
      val useScala3Wildcards =
        options.scalaFeatures.`Scala 3 wildcard imports` && scala3SyntaxAllowed
      val useScala3WildcardsInSelectors =
        options.scalaFeatures.`Scala 3 wildcard imports in selector` && scala3SyntaxAllowed

      val renameArrow =
        if (useScala3RenamingImports) "as"
        else if (options.isUnicodeArrow) ScalaTypedHandler.unicodeCaseArrow
        else "=>"
      val wildcard =
        if (useScala3Wildcards) "*"
        else "_"
      addGroup(importInfo.singleNames)
      addGroup(importInfo.renames.map { case (from, to) => s"$from $renameArrow $to" })
      addGroup(importInfo.hiddenNames.map(_ + s" $renameArrow _"))

      if (importInfo.hasWildcard) {
        groupStrings += wildcard
      }

      addGroup(importInfo.givenTypeTexts.map("given " + _))

      if (importInfo.hasGivenWildcard) {
        groupStrings += "given"
      }

      val space = if (options.spacesInImports) " " else ""
      val root = if (importInfo.rootUsed) s"${_root_prefix}." else ""
      val hasAlias = importInfo.renames.nonEmpty || importInfo.hiddenNames.nonEmpty
      val postfix: String =
        if (groupStrings.length > 1 || hasAlias && !useScala3RenamingImports) {
          // this is needed to fix the wildcard-import bug in 2.13.6 with -Xsource:3
          def fixWildcardInSelector(s: String): String =
            if (s == wildcard && !useScala3WildcardsInSelectors) "_"
            else s
          groupStrings
            .map(fixWildcardInSelector)
            .mkString(s"{$space", ", ", s"$space}")
        } else groupStrings.head
      val prefix = s"$root${importInfo.relative.getOrElse(importInfo.prefixQualifier)}"
      val dotOrNot = if (prefix.endsWith(".") || prefix.isEmpty) "" else "."
      ImportTextData(prefix, dotOrNot, postfix)
    }

    def getImportText(importInfo: ImportInfo, options: ImportTextGenerationOptions): String = {
      val textData = getImportTextData(importInfo, options)
      textData.fullText
    }

    def getImportExprText(importInfo: ImportInfo, options: ImportTextGenerationOptions): String = {
      val textData = getImportTextData(importInfo, options)
      textData.expressionText
    }

    def getScalastyleSortableText(importInfo: ImportInfo): String = {
      val textData = getImportTextData(importInfo, ImportTextGenerationOptions.default)
      textData.forScalastyleSorting
    }

    def getImportText(importInfo: ImportInfo, settings: OptimizeImportSettings): String =
      getImportText(importInfo, ImportTextGenerationOptions.from(settings))

    def getImportExprText(importInfo: ImportInfo, settings: OptimizeImportSettings): String =
      getImportExprText(importInfo, ImportTextGenerationOptions.from(settings))
  }

  def optimizedImportInfos(rangeInfo: ImportRangeInfo, settings: OptimizeImportSettings): Seq[ImportInfo] = {
    val ImportRangeInfo(firstPsi, _, importStatementsWithInfos, usedImportedNames, isLocalRange) = rangeInfo

    val importInfos = importStatementsWithInfos.flatMap(_._2)
    val buffer = ArrayBuffer(importInfos: _*)

    val needReplaceWithFqnImports =
      settings.addFullQualifiedImports &&
        !(isLocalRange && settings.isLocalImportsCanBeRelative)

    if (needReplaceWithFqnImports) {
      for ((info, i) <- importInfos.zipWithIndex) {
        buffer(i) = settings.basePackage match {
          case Some(base) if (info.prefixQualifier + ".").startsWith(base + ".") =>
            info.copy(relative = Some(info.prefixQualifier.substring(base.length + 1)))
          case _ =>
            info.withoutRelative
        }
      }
    }

    val result = sortAndMergeImportsInPlace(buffer, settings)
    updateToWildcardImportsInPlace(result, firstPsi, usedImportedNames, settings)
    updateRootPrefix(result)

    result.toSeq
  }

  def sortAndMergeImports(importInfos: Seq[ImportInfo], settings: OptimizeImportSettings): Seq[ImportInfo] = {
    val buffer = ArrayBuffer.from(importInfos)
    val bufferNew = sortAndMergeImportsInPlace(buffer, settings)
    bufferNew.toSeq
  }

  /** ATTENTION: can modify original buffer or return a new one */
  private def sortAndMergeImportsInPlace(
    buffer: mutable.Buffer[ImportInfo],
    settings: OptimizeImportSettings
  ): mutable.Buffer[ImportInfo] = {
    if (settings.sortImports) {
      sortImportInfos(buffer, settings)
    }

    if (settings.collectImports) {
      mergeImportInfosInPlace(buffer)
      buffer
    }
    else {
      buffer.flatMap(_.split)
    }
  }

  private def updateRootPrefix(importInfos: mutable.Buffer[ImportInfo]): Unit = {
    val importedNames = new mutable.HashSet[String]()

    for (i <- importInfos.indices) {
      val info = importInfos(i)
      if (info.canAddRoot && importedNames.contains(getFirstId(info.prefixQualifier))) {
        importInfos.update(i, info.withRootPrefix)
      }

      if (!ScalaImportOptimizerHelper.extensions.exists(_.cannotShadowName(info))) {
        importedNames ++= info.allNames
      }
    }
  }

  private def sortImportInfos(buffer: mutable.Buffer[ImportInfo], settings: OptimizeImportSettings): Unit = {
    @tailrec
    def iteration(): Unit = {
      var i = 0
      var changed = false
      while (i + 1 < buffer.length) {
        val (lInfo, rInfo) = (buffer(i), buffer(i + 1))
        if (greater(lInfo, rInfo, settings) && swapWithNextIfCan(buffer, i)) {
          changed = true
        }
        i = i + 1
      }
      if (changed) iteration()
    }

    iteration()
  }

  private def updateToWildcardImportsInPlace(
    infos: mutable.Buffer[ImportInfo],
    startPsi: PsiAnchor,
    usedImportedNames: Set[String],
    settings: OptimizeImportSettings
  ): Boolean = {
    var modified = false

    val rangeStartPsi = startPsi.retrieve()

    def updateWithWildcardNames(buffer: mutable.Buffer[ImportInfo]): Unit = {
      for ((info, idx) <- buffer.zipWithIndex) {
        val withWildcardNames = info.withAllNamesForWildcard(rangeStartPsi)
        if (info != withWildcardNames) {
          modified = true
          buffer.update(idx, withWildcardNames)
        }
      }
    }

    updateWithWildcardNames(infos)

    for ((info, i) <- infos.zipWithIndex) {
      val newInfoOpt = possiblyWithWildcardImportWithClashedNames(info, infos, rangeStartPsi, usedImportedNames, settings)
      newInfoOpt match {
        case Some(newInfo) if newInfo != info =>
          modified = true
          infos.update(i, newInfo)
        case _ =>
      }
    }

    modified
  }

  private def possiblyWithWildcardImportWithClashedNames(
    info: ImportInfo,
    allInfos: collection.Iterable[ImportInfo],
    rangeStartPsi: PsiElement,
    usedImportedNames: Set[String],
    settings: OptimizeImportSettings
  ): Option[ImportInfo] = {
    val tooManySingleNames = info.singleNames.size >= settings.classCountToUseImportOnDemand
    val onlySingleNames    = info.hiddenNames.isEmpty && info.renames.isEmpty

    val mayReplaceAllWithWildcard = tooManySingleNames && onlySingleNames && !info.hasWildcard
    val mayMergeIntoWildcard = onlySingleNames && info.hasWildcard

    if (!mayReplaceAllWithWildcard && !mayMergeIntoWildcard)
      return None

    val withWildcard = info.toWildcardInfo.withAllNamesForWildcard(rangeStartPsi)

    if (withWildcard.wildcardHasUnusedImplicit)
      return None

    filterClashingSingleNamesForWildcardImport(withWildcard, info, allInfos, rangeStartPsi, usedImportedNames)
  }

  private def filterClashingSingleNamesForWildcardImport(
    info: ImportInfo,
    infoOriginal: ImportInfo,
    allInfos: collection.Iterable[ImportInfo],
    rangeStartPsi: PsiElement,
    usedImportedNames: Set[String]
  ): Option[ImportInfo] = {
    val allExplicitNames = allInfos.flatMap {
      case `infoOriginal` => Seq.empty
      case other => other.singleNames
    }.toSet

    val allNamesFromOtherWildcards = allInfos.flatMap {
      case `infoOriginal` => Seq.empty
      case other => other.allNames
    }.toSet -- allExplicitNames

    val problematicNames = info.allNamesForWildcard & usedImportedNames
    val clashesWithOtherWildcards = problematicNames & allNamesFromOtherWildcards

    def notAtRangeStart = problematicNames.forall(name => !resolvesAtRangeStart(name, rangeStartPsi))

    if (clashesWithOtherWildcards.size < infoOriginal.singleNames.size && notAtRangeStart)
      Some(info.copy(singleNames = clashesWithOtherWildcards))
    else
      None
  }

  private def resolvesAtRangeStart(name: String, rangeStartPsi: PsiElement): Boolean = {
    if (rangeStartPsi == null) false
    else {
      val ref = ScalaPsiElementFactory.createReferenceFromText(name, rangeStartPsi.getContext, rangeStartPsi)
      ref.bind().map(_.element).exists {
        case p: PsiPackage =>
          p.getParentPackage != null && p.getParentPackage.name != null
        case o: ScObject if o.isPackageObject => o.qualifiedName.contains(".")
        case o: ScObject =>
          o.getParent match {
            case _: ScalaFile => false
            case _ => true
          }
        case td: ScTypedDefinition if td.isStable => true
        case _: ScTypeDefinition => false
        case _: PsiClass => true
        case f: PsiField if f.hasFinalModifier => true
        case _ => false
      }
    }
  }

  /**
   * This method is similar to [[insertImportInfos]], but it doesn't change shuffle already existing import statemtns in the rangeInfo
   */
  def bestPlaceToInsertNewImport(
    newInfo: ImportInfo,
    rangeInfo: ImportRangeInfo,
    settings: OptimizeImportSettings
  ): ImportInsertionPlace = {
    if (rangeInfo.importStmtWithInfos.isEmpty)
      return ImportInsertionPlace.InsertFirst(rangeInfo)

    val importStmtsWithInfos: Seq[(ScImportStmt, Seq[ImportInfo])] =
      rangeInfo.importStmtWithInfos

    if (importStmtsWithInfos.isEmpty)
      return ImportInsertionPlace.InsertFirst(rangeInfo)

    val importInfos: Seq[ImportInfo] =
      importStmtsWithInfos.flatMap(_._2)

    def infoIdxToImportStmtIdx(infoIdx: Int): Int =
      importStmtsWithInfos.iterator.scanLeft(0) { case (acc, (_, infos)) => acc + infos.size }.indexWhere(_ > infoIdx) - 1

    if (settings.collectImports) {
      val indexWithSameQualifier = importInfos.indexWhere(_.prefixQualifier == newInfo.prefixQualifier)
      if (indexWithSameQualifier >= 0) {
        val importStmtIdx = infoIdxToImportStmtIdx(indexWithSameQualifier)
        return ImportInsertionPlace.MergeInto(rangeInfo, importStmtIdx)
      }
    }

    var infoIdx = importInfos.size - 1
    var continue = true

    while (infoIdx >= 0 && continue) {
      val currentInfo = importInfos(infoIdx)

      val compareResult = compare(currentInfo, newInfo, settings)
      if (compareResult > 0 && canSwapImports(currentInfo, newInfo)) {
        //ok, continue searching
        infoIdx -= 1
      }
      else {
        continue = false
      }
    }

    if (infoIdx < 0)
      ImportInsertionPlace.InsertFirst(rangeInfo)
    else {
      val importStmtIdx = infoIdxToImportStmtIdx(infoIdx)
      ImportInsertionPlace.InsertAfterStatement(rangeInfo, importStmtIdx)
    }
  }

  sealed trait ImportInsertionPlace
  object ImportInsertionPlace {
    final case class MergeInto(importRange: ImportRangeInfo, importInfoIdx: Int) extends ImportInsertionPlace
    final case class InsertFirst(importRange: ImportRangeInfo) extends ImportInsertionPlace
    final case class InsertAfterStatement(importRange: ImportRangeInfo, importStmtIdx: Int) extends ImportInsertionPlace {
      assert(importStmtIdx >= 0, "importInfoIdx must be non negative")
    }
  }

  /** @see [[bestPlaceToInsertNewImport]] */
  def insertImportInfos(
    infosToAdd: Seq[ImportInfo],
    infos: Seq[ImportInfo],
    rangeStart: PsiAnchor,
    settings: OptimizeImportSettings
  ): Seq[ImportInfo] = {

    def addLastAndMoveUpwards(newInfo: ImportInfo, buffer: mutable.Buffer[ImportInfo]): Unit = {
      var idx = buffer.size
      //TODO: we can first finds the index and only then modify the buffer, otherwise why do we we shuffle the data in vain?
      buffer.insert(idx, newInfo)
      while(idx > 0 && greater(buffer(idx - 1), buffer(idx), settings) && swapWithNextIfCan(buffer, idx - 1)) {
        idx -= 1
      }
    }

    def replace(oldInfos: collection.Seq[ImportInfo], newInfos: collection.Seq[ImportInfo], buffer: mutable.Buffer[ImportInfo]): Unit = {
      val oldIndices = oldInfos.map(buffer.indexOf).filter(_ >= 0).sorted(Ordering[Int].reverse)
      if (oldIndices.nonEmpty) {
        val minIndex = oldIndices.last
        oldIndices.foreach(buffer.remove)
        buffer.insertAll(minIndex, newInfos)
      }
      else {
        newInfos.foreach(addLastAndMoveUpwards(_, buffer))
      }
    }

    def withAliasedQualifier(info: ImportInfo): ImportInfo = {
      if (settings.addFullQualifiedImports)
        return info

      for {
        oldInfo <- infos
        (name, newName) <- oldInfo.renames
      } {
        val renamerPrefix = oldInfo.prefixQualifier
        val oldPrefix = s"$renamerPrefix.$name"
        if (info.prefixQualifier.startsWith(oldPrefix)) {
          val stripped = info.prefixQualifier.stripPrefix(oldPrefix)
          val newRelative = s"$newName$stripped"
          val newPrefix = s"$renamerPrefix.$newRelative"
          return info.copy(prefixQualifier = newPrefix, relative = Some(newRelative), rootUsed = false)
        }
      }
      info
    }

    val actuallyInserted = infosToAdd.map(withAliasedQualifier)
    val addedPrefixes = actuallyInserted.map(_.prefixQualifier)

    val addedPrefixesSingleNamesCount = addedPrefixes.map { prefix =>
      val singleNamesCount = (actuallyInserted ++ infos)
        .filter(_.prefixQualifier == prefix)
        .flatMap(_.singleNames)
        .distinct.size
      prefix -> singleNamesCount
    }.toMap

    def tooManySingleNames(qualifier: String) =
      addedPrefixesSingleNamesCount(qualifier) >= settings.classCountToUseImportOnDemand

    def insertSimpleInfo(info: ImportInfo, buffer: mutable.Buffer[ImportInfo]): Unit = {
      val samePrefixInfos = buffer.filter(_.prefixQualifier == info.prefixQualifier)
      if (settings.collectImports) {
        val merged = ImportInfo.merge(samePrefixInfos :+ info)
        replace(samePrefixInfos, merged.toSeq, buffer)
      }
      else {
        addLastAndMoveUpwards(info, buffer)
      }
    }

    def insertInfoWithWildcard(info: ImportInfo, buffer: mutable.Buffer[ImportInfo], usedNames: Set[String]): Unit = {
      val (samePrefixInfos, otherInfos) = buffer.partition(_.prefixQualifier == info.prefixQualifier)
      val samePrefixWithNewSplitted = samePrefixInfos.flatMap(_.split) ++ info.split

      val (simpleInfos, notSimpleInfos) = samePrefixWithNewSplitted.partition(_.singleNames.nonEmpty)
      val (wildcard, withArrows) = notSimpleInfos.partition(_.hasWildcard)

      val namesFromOtherWildcards = otherInfos.flatMap(_.namesFromWildcard).toSet
      val simpleNamesToRemain = simpleInfos.flatMap(_.singleNames).toSet & namesFromOtherWildcards & usedNames
      val simpleInfosToRemain = simpleInfos.filter(si => simpleNamesToRemain.contains(si.singleNames.head))
      val renames = withArrows.flatMap(_.renames)
      val hiddenNames = withArrows.flatMap(_.hiddenNames)
      val newHiddenNames = {
        val fromInsertedWildcard = info.allNamesForWildcard -- simpleNamesToRemain -- renames.map(_._1) -- hiddenNames
        fromInsertedWildcard & namesFromOtherWildcards & usedNames
      }

      withArrows ++= newHiddenNames.map(info.toHiddenNameInfo)

      val notSimpleMerged = ImportInfo.merge(withArrows ++ wildcard)
      if (settings.collectImports) {
        val simpleMerged = ImportInfo.merge(simpleInfosToRemain ++ notSimpleMerged)
        replace(samePrefixInfos, simpleMerged.toSeq, buffer)
      }
      else {
        replace(samePrefixInfos, simpleInfosToRemain ++ notSimpleMerged, buffer)
      }
    }

    val needAdditionalInfo = infosToAdd.exists(_.hasWildcard) || addedPrefixes.exists(tooManySingleNames)
    val buffer = infos.toBuffer

    if (needAdditionalInfo) {
      val rangeStartPsi = rangeStart.retrieve()
      val holder = PsiTreeUtil.getParentOfType(rangeStartPsi, classOf[ScImportsHolder])
      val usedNames = collectUsedImportedNames(holder)

      for (info <- infosToAdd) {
        if (info.hasWildcard) {
          insertInfoWithWildcard(info, buffer, usedNames)
        }
        else {
          insertSimpleInfo(info, buffer)
        }
      }
      updateToWildcardImportsInPlace(buffer, rangeStart, usedNames, settings)
    }
    else {
      actuallyInserted.foreach(insertSimpleInfo(_, buffer))
    }

    updateRootPrefix(buffer)
    buffer.toVector
  }

  private def swapWithNextIfCan(buffer: mutable.Buffer[ImportInfo], idx: Int): Boolean = {
    val canSwap = canSwapWithNext(buffer, idx)
    if (canSwap) {
      swapWithNext(buffer, idx)
    }
    canSwap
  }

  private def canSwapWithNext(buffer: collection.Seq[ImportInfo], idx: Int): Boolean = {
    val first: ImportInfo = buffer(idx)
    val second: ImportInfo = buffer(idx + 1)
    canSwapImports(first, second)
  }

  private def canSwapImports(first: ImportInfo, second: ImportInfo): Boolean = {
    val firstPrefix: String = first.relative.getOrElse(first.prefixQualifier)
    val firstPart: String = getFirstId(firstPrefix)

    val secondPrefix: String = second.relative.getOrElse(second.prefixQualifier)
    val secondPart: String = getFirstId(secondPrefix)

    val condition1 = first.rootUsed || !second.allNames.contains(firstPart)
    if (condition1)
      second.rootUsed || !first.allNames.contains(secondPart)
    else
      false
  }

  private def swapWithNext(buffer: mutable.Buffer[ImportInfo], idx: Int): Unit = {
    val first: ImportInfo = buffer(idx)
    val second: ImportInfo = buffer(idx + 1)

    val tmp = first
    buffer(idx) = second
    buffer(idx + 1) = tmp
  }

  private def mergeImportInfosInPlace(infos: mutable.Buffer[ImportInfo]): Unit = {
    def canBeMergedAt(i: Int): Boolean =
      i > -1 && i < infos.length && {
        val el = infos(i)
        !ScalaImportOptimizerHelper.extensions.exists(_.preventMerging(el))
      }

    def samePrefixAfter(i: Int): Int = {
      if (!canBeMergedAt(i)) return -1

      var j = i + 1
      while (j < infos.length) {
        if (infos(j).prefixQualifier == infos(i).prefixQualifier) return j
        j += 1
      }
      -1
    }

    var infoIdx = 0
    while (infoIdx < infos.length - 1) {
      val prefixIndex: Int = samePrefixAfter(infoIdx)
      if (prefixIndex != -1) {
        if (prefixIndex == infoIdx + 1) {
          val merged = infos(infoIdx).merge(infos(infoIdx + 1))
          infos(infoIdx) = merged
          infos.remove(infoIdx + 1)
        }
        else {
          if (swapWithNextIfCan(infos, infoIdx)) {
            var j = infoIdx + 1
            var break = false
            while (!break && j != prefixIndex - 1) {
              if (swapWithNextIfCan(infos, j)) {
                //ok
              } else {
                break = true
              }
              j += 1
            }
            if (!break) {
              val merged = infos(j).merge(infos(j + 1))
              infos(j) = merged
              infos.remove(j + 1)
            }
          }
          else infoIdx += 1
        }
      }
      else infoIdx += 1
    }
  }

  private def getFirstId(s: String): String = {
    if (s.startsWith("`")) {
      val index: Int = s.indexOf('`', 1)
      if (index == -1) s
      else s.substring(0, index + 1)
    } else {
      val index: Int = s.indexOf('.')
      if (index == -1) s
      else s.substring(0, index)
    }
  }

  private def findGroupIndex(prefix: String, relativeTo: Option[String], settings: OptimizeImportSettings): Int = {
    settings.scalastyleGroups match {
      case Some(patterns) =>
        patterns.indexWhere(_.matcher(prefix).matches())
      case _ =>
        def matches(packagePattern: String) =
          prefix == packagePattern || prefix.startsWith(packagePattern + ".") ||
            packagePattern == BASE_PACKAGE_IMPORTS && relativeTo.exists(id => settings.basePackage.contains(prefix.dropRight(id.length + 1)))

        val groups = settings.importLayout

        val mostSpecific = groups
          .filterNot(_ == BLANK_LINE)
          .filter(matches)
          .sortBy(_.length)
          .lastOption

        mostSpecific
          .map(groups.indexOf(_))
          .getOrElse {
            0 max groups.indexOf(ALL_OTHER_IMPORTS)
          }
    }
  }

  private def greater(lInfo: ImportInfo, rInfo: ImportInfo, settings: OptimizeImportSettings): Boolean = {
    val res = compare(lInfo, rInfo, settings)
    res > 0
  }

  private def compare(lInfo: ImportInfo, rInfo: ImportInfo, settings: OptimizeImportSettings) = {
    val textCreator = new ImportTextCreator

    val lIndex = findGroupIndex(lInfo.prefixQualifier, lInfo.relative, settings)
    val rIndex = findGroupIndex(rInfo.prefixQualifier, rInfo.relative, settings)

    if (lIndex > rIndex)
      1
    else if (lIndex < rIndex)
      -1
    else {
      //both belongs to the same import group
      //(e.g. both imports are from `java` imports (e.g. `import java.util.List`)
      val result = if (settings.scalastyleOrder) {
        val lText = textCreator.getScalastyleSortableText(lInfo)
        val rText = textCreator.getScalastyleSortableText(rInfo)
        ScalastyleSettings.compareImports(lText, rText)
      }
      else {
        val lText = textCreator.getImportText(lInfo, settings)
        val rText = textCreator.getImportText(rInfo, settings)
        lText.compareTo(rText)
      }
      result
    }
  }

  private def collectImportsUsed(element: PsiElement, imports: util.Set[ImportUsed], names: util.Set[UsedName]): Unit = {
    val defaultImportsSet = element.defaultImports

    def fromDefaultImport(srr: ScalaResolveResult): Boolean =
      srr.element match {
        case c: PsiClass =>
          val qName = c.qualifiedName

          if (qName == null) false
          else {
            val name    = c.name
            val pkgName = qName.substring(0, qName.length - name.length)
            defaultImportsSet.contains(pkgName)
          }
        case ContainingClass(o: ScObject) => defaultImportsSet.contains(o.qualifiedName)
        case _                            => false
      }

    def addResult(srr: ScalaResolveResult, fromElem: PsiElement): Unit = {
      val importsUsed = srr.importsUsed
      if (importsUsed.nonEmpty || fromDefaultImport(srr)) {
        imports.addAll(importsUsed.asJava)
        // TODO Why does List() resolve to "List", but to "apply" if there's import scala.collection.immutable.List? (see SCL-19972)
        val name = if (srr.name == "apply") fromElem.getText else srr.name
        names.add(UsedName(name, fromElem.getTextRange.getStartOffset))
      }
    }

    def addWithImplicits(srr: ScalaResolveResult, fromElem: PsiElement): Unit = {
      withImplicits(srr).foreach(addResult(_, fromElem))
    }

    def withImplicits(srr: ScalaResolveResult): Seq[ScalaResolveResult] = {
      srr +:
        srr.implicitConversion.toSeq.flatMap(withImplicits) ++:
        srr.implicitParameters.flatMap(withImplicits)
    }

    def addFromExpression(expr: ScExpression): Unit = {
      expr match {
        case call: ScMethodCall => imports.addAll(call.getImportsUsed.asJava)
        case f: ScFor => imports.addAll(ScalaPsiUtil.getExprImports(f).asJava)
        case _ =>
      }

      val implicits = expr.implicitConversion() ++: expr.findImplicitArguments.getOrElse(Seq.empty)

      implicits.foreach(addWithImplicits(_, expr))
    }

    element match {
      case impQual: ScStableCodeReference
        if impQual.qualifier.isEmpty && ScalaPsiUtil.getParentImportStatement(impQual) != null =>
        //don't add as ImportUsed to be able to optimize it away if it is used only in unused imports
        val hasImportUsed = impQual.multiResolveScala(false).exists(_.importsUsed.nonEmpty)
        if (hasImportUsed) {
          names.add(UsedName(impQual.refName, impQual.getTextRange.getStartOffset))
        }
      case ref: ScReference if ScalaPsiUtil.getParentImportStatement(ref) == null =>
        ref.multiResolveScala(false).foreach(addWithImplicits(_, ref))
      case c: ScConstructorInvocation =>
        c.findImplicitArguments match {
          case Some(parameters) =>
            parameters.foreach(addWithImplicits(_, c))
          case _ =>
        }
      case _ =>
    }
    //separate match to have reference expressions processed
    element match {
      case e: ScExpression => addFromExpression(e)
      case _ =>
    }
  }

  private def mayOptimizeOnTheFly(importUsed: ImportUsed): Boolean =
    importUsed.importExpr.forall(mayOptimizeOnTheFly)

  private def mayOptimizeOnTheFly(importExpr: ScImportExpr): Boolean =
    !importExpr.hasWildcardSelector &&
      !importExpr.selectors.exists(_.isAliasedImport) &&
      isOnTopOfTheFile(importExpr) &&
      !importExpr.selectors.exists(_.isGivenSelector)

  private def isOnTopOfTheFile(importExpr: ScImportExpr): Boolean = {
    def isOnTopOfTheFile(stmt: ScImportStmt) =
      stmt.getParent.is[PsiFile, ScPackaging] && !stmt.prevSiblings.exists(_.is[ScMember])

    val importStmt = Option(importExpr.getParent).filterByType[ScImportStmt]
    importStmt.forall(isOnTopOfTheFile)
  }

  //utilities
  private def canElementUseImport(element: PsiElement): Boolean =
    element.is[ScReference, ImplicitArgumentsOwner]

  private def isLocalImportHolder(holder: ScImportsHolder): Boolean =
    !holder.is[ScalaFile, ScPackaging]

  private def collectImportHoldersAndUsers(file: PsiFile): (util.ArrayList[ScImportsHolder], util.ArrayList[PsiElement]) = {
    val holders = new util.ArrayList[ScImportsHolder]()
    val users = new util.ArrayList[PsiElement]()

    file.depthFirst().foreach { elem =>
      elem match {
        case holder: ScImportsHolder =>
          holders.add(holder)
        case _ =>
      }
      if (canElementUseImport(elem)) {
        users.add(elem)
      }
    }
    (holders, users)
  }

  //quite heavy computation, is really needed only for dealing with wildcard imports
  private def collectUsedImportedNames(holder: ScImportsHolder): Set[String] = {
    val imports = new util.HashSet[ImportUsed]()
    val names = new util.HashSet[UsedName]()

    holder.depthFirst().foreach { elem =>
      if (canElementUseImport(elem)) {
        collectImportsUsed(elem, imports, names)
      }
    }
    names.asScala.toSet.map((x: UsedName) => x.name)
  }
}
