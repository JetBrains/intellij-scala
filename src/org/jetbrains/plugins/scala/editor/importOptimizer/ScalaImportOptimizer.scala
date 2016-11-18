package org.jetbrains.plugins.scala
package editor.importOptimizer


import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.intellij.concurrency.JobLauncher
import com.intellij.lang.{ImportOptimizer, LanguageImportStatements}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScForStatement, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.{immutable, mutable}

/**
  * User: Alexander Podkhalyuzin
  * Date: 16.06.2009
  */

class ScalaImportOptimizer extends ImportOptimizer {

  import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._

  def processFile(file: PsiFile): Runnable = processFile(file, null)

  def processFile(file: PsiFile, progressIndicator: ProgressIndicator = null): Runnable = {
    def collectImportHoldersAndUsers: (util.ArrayList[ScImportsHolder], util.ArrayList[PsiElement]) = {
      val holders = new util.ArrayList[ScImportsHolder]()
      val users = new util.ArrayList[PsiElement]()

      file.depthFirst.foreach { elem =>
        elem match {
          case holder: ScImportsHolder => holders.add(holder)
          case _ =>
        }
        elem match {
          case ImportUser(e) => users.add(e)
          case _ =>
        }
      }
      (holders, users)
    }

    val scalaFile = file match {
      case scFile: ScalaFile => scFile
      case multiRootFile: PsiFile if multiRootFile.getViewProvider.getLanguages contains ScalaFileType.SCALA_LANGUAGE =>
        multiRootFile.getViewProvider.getPsi(ScalaFileType.SCALA_LANGUAGE).asInstanceOf[ScalaFile]
      case _ => return EmptyRunnable.getInstance()
    }

    val project: Project = scalaFile.getProject
    val documentManager = PsiDocumentManager.getInstance(project)
    val document: Document = documentManager.getDocument(scalaFile)
    val analyzingDocumentText = document.getText

    val usedImports = ContainerUtil.newConcurrentSet[ImportUsed]()
    val usedImportedNames = ContainerUtil.newConcurrentSet[UsedName]()

    val (importHolders, importUsers) = collectImportHoldersAndUsers

    val progressManager: ProgressManager = ProgressManager.getInstance()
    val indicator: ProgressIndicator =
      if (progressIndicator != null) progressIndicator
      else if (progressManager.hasProgressIndicator) progressManager.getProgressIndicator
      else null
    if (indicator != null) indicator.setText2(file.getName + ": analyzing imports usage")

    val size = importHolders.size + importUsers.size //processAllElementsConcurrentlyUnderProgress will be called 2 times
    val counter = new AtomicInteger(0)

    def processAllElementsConcurrentlyUnderProgress[T <: PsiElement](elements: util.List[T])(action: T => Unit) = {
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, indicator, true, true, new Processor[T] {
        override def process(element: T): Boolean = {
          val count: Int = counter.getAndIncrement
          if (count <= size && indicator != null) indicator.setFraction(count.toDouble / size)

          action(element)

          true
        }
      })
    }

    processAllElementsConcurrentlyUnderProgress(importUsers) { element =>
      collectImportsUsed(element, usedImports, usedImportedNames)
    }

    if (indicator != null) indicator.setText2(file.getName + ": collecting additional info")

    def collectRanges(createInfo: ScImportStmt => Seq[ImportInfo]): Seq[RangeInfo] = {
      val importsInfo = ContainerUtil.newConcurrentSet[RangeInfo]()
      processAllElementsConcurrentlyUnderProgress(importHolders) {
        case holder: ScImportsHolder =>
          importsInfo.addAll(collectImportRanges(holder, createInfo, usedImportedNames.toSet))
        case _ =>
      }
      importsInfo.toSeq.sortBy(_.startOffset)
    }

    val settings = OptimizeImportSettings(project)
    import settings._

    def isImportUsed(importUsed: ImportUsed): Boolean = {
      //todo: collect proper information about language features
      importUsed match {
        case ImportSelectorUsed(sel) if sel.isAliasedImport => true
        case _ => usedImports.contains(importUsed) || isLanguageFeatureImport(importUsed) || importUsed.qualName.exists(isAlwaysUsedImport)
      }
    }

    val rangeInfos = collectRanges(createInfo(_, isImportUsed))

    val optimized = rangeInfos.map(range => (range, optimizedImportInfos(range, settings)))

    new Runnable {
      def run() {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document: Document = documentManager.getDocument(scalaFile)
        documentManager.commitDocument(document)

        val ranges: Seq[(RangeInfo, Seq[ImportInfo])] =
          if (document.getText != analyzingDocumentText)  //something was changed...
            sameInfosWithUpdatedRanges()
          else optimized

        for ((range, importInfos) <- ranges.reverseIterator) {
          replaceWithNewImportInfos(range, importInfos, settings, scalaFile)
        }
        documentManager.commitDocument(document)
      }

      def sameInfosWithUpdatedRanges(): Seq[(RangeInfo, Seq[ImportInfo])] = {
        optimized.zip {
          collectRanges(_ => Seq.empty)
        }.map {
          case ((_, infos), range) => (range, infos)
        }
      }
    }
  }

  protected def getImportTextCreator: ImportTextCreator = new ImportTextCreator

  protected def isImportDelimiter(psi: PsiElement) = psi.isInstanceOf[PsiWhiteSpace]

  def supports(file: PsiFile): Boolean = file.isInstanceOf[ScalaFile] && file.getViewProvider.getAllFiles.size() < 3

  def replaceWithNewImportInfos(range: RangeInfo, importInfos: Seq[ImportInfo], settings: OptimizeImportSettings, file: PsiFile): Unit = {
    val firstPsi = range.firstPsi.retrieve()
    val lastPsi = range.lastPsi.retrieve()

    def notValid(psi: PsiElement) = psi == null || !psi.isValid

    if (notValid(firstPsi) || notValid(lastPsi)) {
      throw new IllegalStateException("Couldn't update imports: import range was invalidated after initial analysis")
    }

    val textCreator = getImportTextCreator
    val fileText = file.getText
    import settings._

    @tailrec
    def indentForOffset(index: Int, res: String = ""): String = {
      if (index <= 0) res
      else {
        val c = fileText.charAt(index - 1)
        if (c == ' ' || c == '\t') indentForOffset(index - 1, s"$c$res")
        else res
      }
    }
    val newLineWithIndent: String = "\n" + indentForOffset(range.startOffset)

    var prevGroupIndex = -1
    def groupSeparatorsBefore(info: ImportInfo, currentGroupIndex: Int) = {
      if (currentGroupIndex <= prevGroupIndex || prevGroupIndex == -1) ""
      else {
        def isBlankLine(i: Int) = importLayout(i) == ScalaCodeStyleSettings.BLANK_LINE
        val blankLineNumber =
          Range(currentGroupIndex - 1, prevGroupIndex, -1).dropWhile(!isBlankLine(_)).takeWhile(isBlankLine).size
        newLineWithIndent * blankLineNumber
      }
    }

    val text = importInfos.map { info =>
      val index: Int = findGroupIndex(info.prefixQualifier, settings)
      val blankLines = groupSeparatorsBefore(info, index)
      prevGroupIndex = index
      blankLines + textCreator.getImportText(info, settings)
    }.mkString(newLineWithIndent).replaceAll("""\n[ \t]+\n""", "\n\n")

    //it should cover play template files
    val fileFactory = PsiFileFactory.getInstance(file.getProject)
    val dummyFile = fileFactory.createFileFromText("dummy." + file.getFileType.getDefaultExtension, file.getLanguage, text)

    val errorElements = dummyFile.getChildren.filter(_.isInstanceOf[PsiErrorElement]).map(_.getNode)
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

    val anchor = Option(lastPsi.getNextSibling).map(_.getNode).orNull

    withDisabledPostprocessFormatting(file.getProject) {
      parentNode.removeRange(firstNodeToRemove, anchor)
      parentNode.addChildren(dummyFile.getNode.getFirstChildNode, null, anchor)
    }
  }

  def collectImportRanges(holder: ScImportsHolder,
                          createInfo: ScImportStmt => Seq[ImportInfo],
                          allUsedImportedNames: Set[UsedName]): Set[RangeInfo] = {
    val result = mutable.HashSet[RangeInfo]()
    var firstPsi: PsiElement = null
    var lastPsi: PsiElement = null
    val isLocalRange = holder match {
      case _: ScalaFile | _: ScPackaging => false
      case _ => true
    }
    val infos = ArrayBuffer[ImportInfo]()
    val sortedUsedNames = allUsedImportedNames.toSeq.sortBy(_.offset)

    def addRange(): Unit = {
      if (firstPsi != null && lastPsi != null) {
        val rangeStart = firstPsi.getTextRange.getStartOffset
        val usedImportedNames = sortedUsedNames.dropWhile(_.offset < rangeStart).map(_.name).toSet
        val rangeInfo = RangeInfo(PsiAnchor.create(firstPsi), PsiAnchor.create(lastPsi), infos.toVector, usedImportedNames, isLocalRange)
        result += rangeInfo
        firstPsi = null
        lastPsi = null
        infos.clear()
      }
    }

    def initRange(psi: PsiElement) {
      firstPsi = psi
      lastPsi = psi
    }
     
    for (child <- holder.getNode.getChildren(null)) {
      child.getPsi match {
        case _: PsiWhiteSpace =>
        case _: ScDocComment => addRange()
        case comment: PsiComment =>
          val next = comment.getNextSibling
          val prev = comment.getPrevSibling
          (next, prev) match {
            case (w1: PsiWhiteSpace, w2: PsiWhiteSpace) if
            w1.getText.contains("\n") && w2.getText.contains("\n") => addRange()
            case _ =>
          }
        case _: LeafPsiElement =>
        case a: PsiElement if isImportDelimiter(a) => //do nothing
        case imp: ScImportStmt =>
          if (firstPsi == null) {
            imp.getPrevSibling match {
              case a: PsiElement if isImportDelimiter(a) && !a.isInstanceOf[PsiWhiteSpace] =>
                initRange(a)
                lastPsi = imp
              case _ => initRange(imp)
            }
          } else {
            lastPsi = imp
          }
          infos ++= createInfo(imp)
        case _ => addRange()
      }
    }
    addRange()
    result.toSet
  }
}

object ScalaImportOptimizer {

  private case class UsedName(name: String, offset: Int)

  private object ImportUser {
    def unapply(e: PsiElement): Option[PsiElement] = e match {
      case elem @ (_: ScReferenceElement | _: ScSimpleTypeElement | _: ScExpression) => Some(elem)
      case _ => None
    }
  }

  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty
  val _root_prefix = "_root_"

  /**
    * We can't just select ScalaImportOptimizer because of Play2 templates
    *
    * @param file Any parallel psi file
    */
  def runOptimizerUnsafe(file: ScalaFile) {
    findOptimizerFor(file).foreach(_.processFile(file).run)
  }

  def findOptimizerFor(file: ScalaFile): Option[ImportOptimizer] = {
    val topLevelFile = file.getViewProvider.getPsi(file.getViewProvider.getBaseLanguage)
    val optimizers = LanguageImportStatements.INSTANCE.forFile(topLevelFile)
    if (optimizers.isEmpty) return None

    if (topLevelFile.getViewProvider.getPsi(ScalaFileType.SCALA_LANGUAGE) == null) return None

    val i = optimizers.iterator()
    while (i.hasNext) {
      val opt = i.next()
      if (opt supports topLevelFile) {
        return Some(opt)
      }
    }
    None
  }

  def isLanguageFeatureImport(used: ImportUsed): Boolean = {
    val expr = used match {
      case ImportExprUsed(e) => e
      case ImportSelectorUsed(selector) => PsiTreeUtil.getParentOfType(selector, classOf[ScImportExpr])
      case ImportWildcardSelectorUsed(e) => e
    }
    if (expr == null) return false
    if (expr.qualifier == null) return false
    expr.qualifier.resolve() match {
      case o: ScObject =>
        o.qualifiedName.startsWith("scala.language") || o.qualifiedName.startsWith("scala.languageFeature")
      case _ => false
    }
  }

  class ImportTextCreator {
    def getImportText(importInfo: ImportInfo, isUnicodeArrow: Boolean, spacesInImports: Boolean,
                      sortLexicografically: Boolean): String = {
      import importInfo._

      val groupStrings = new ArrayBuffer[String]

      def addGroup(names: Iterable[String]) = {
        if (sortLexicografically) groupStrings ++= names.toSeq.sorted
        else groupStrings ++= names
      }

      val arrow = if (isUnicodeArrow) ScalaTypedHandler.unicodeCaseArrow else "=>"
      addGroup(singleNames)
      addGroup(renames.map(pair => s"${pair._1} $arrow ${pair._2}"))
      addGroup(hiddenNames.map(_ + s" $arrow _"))

      if (hasWildcard) groupStrings += "_"
      val space = if (spacesInImports) " " else ""
      val root = if (rootUsed) s"${_root_prefix}." else ""
      val postfix =
        if (groupStrings.length > 1 || renames.nonEmpty || hiddenNames.nonEmpty) groupStrings.mkString(s"{$space", ", ", s"$space}")
        else groupStrings(0)
      val prefix = s"$root${relative.getOrElse(prefixQualifier)}"
      val dotOrNot = if (prefix.endsWith(".") || prefix.isEmpty) "" else "."
      s"import $prefix$dotOrNot$postfix"
    }

    def getImportText(importInfo: ImportInfo, settings: OptimizeImportSettings): String =
      getImportText(importInfo, settings.isUnicodeArrow, settings.spacesInImports, settings.sortImports)
  }

  def optimizedImportInfos(rangeInfo: RangeInfo, settings: OptimizeImportSettings): Seq[ImportInfo] = {
    import settings._
    val RangeInfo(firstPsi, _, importInfos, usedImportedNames, isLocalRange) = rangeInfo

    val buffer = new ArrayBuffer[ImportInfo]()

    val needReplaceWithFqnImports = addFullQualifiedImports && !(isLocalRange && isLocalImportsCanBeRelative)

    if (needReplaceWithFqnImports)
      buffer ++= importInfos.map(_.withoutRelative)
    else
      buffer ++= importInfos

    if (sortImports) sortImportInfos(buffer, settings)

    val result =
      if (collectImports) mergeImportInfos(buffer)
      else buffer.flatMap(_.split)

    updateToWildcardImports(result, firstPsi, usedImportedNames, settings)
    updateRootPrefix(result)

    result.to[immutable.Seq]
  }

  def updateRootPrefix(importInfos: ArrayBuffer[ImportInfo]): Unit = {
    val importedNames = new mutable.HashSet[String]()

    for (i <- importInfos.indices) {
      val info = importInfos(i)
      if (info.canAddRoot && importedNames.contains(getFirstId(info.prefixQualifier)))
        importInfos.update(i, info.withRootPrefix)

      importedNames ++= info.allNames
    }
  }

  def sortImportInfos(buffer: ArrayBuffer[ImportInfo], settings: OptimizeImportSettings): Unit = {
    @tailrec
    def iteration(): Unit = {
      var i = 0
      var changed = false
      while (i + 1 < buffer.length) {
        val (lInfo, rInfo) = (buffer(i), buffer(i + 1))
        if (greater(lInfo, rInfo, settings) && swapWithNext(buffer, i)) changed = true
        i = i + 1
      }
      if (changed) iteration()
    }

    iteration()
  }

  def updateToWildcardImports(infos: ArrayBuffer[ImportInfo],
                              startPsi: PsiAnchor,
                              usedImportedNames: Set[String],
                              settings: OptimizeImportSettings): Unit = {

    val rangeStartPsi = startPsi.retrieve()
    def resolvesAtRangeStart(name: String): Boolean = {
      if (rangeStartPsi == null) false
      else {
        val ref = ScalaPsiElementFactory.createReferenceFromText(name, rangeStartPsi.getContext, rangeStartPsi)
        ref.bind().exists {
          case ScalaResolveResult(p: PsiPackage, _) =>
            p.getParentPackage != null && p.getParentPackage.getName != null
          case ScalaResolveResult(o: ScObject, _) if o.isPackageObject => o.qualifiedName.contains(".")
          case ScalaResolveResult(o: ScObject, _) =>
            o.getParent match {
              case _: ScalaFile => false
              case _ => true
            }
          case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => true
          case ScalaResolveResult(_: ScTypeDefinition, _) => false
          case ScalaResolveResult(_: PsiClass, _) => true
          case ScalaResolveResult(f: PsiField, _) if f.hasFinalModifier => true
          case _ => false
        }
      }
    }

    def updateWithWildcardNames(buffer: ArrayBuffer[ImportInfo]) {
      for ((info, idx) <- buffer.zipWithIndex) {
        val withWildcardNames = info.withAllNamesForWildcard(rangeStartPsi)
        if (info != withWildcardNames) {
          buffer.update(idx, withWildcardNames)
        }
      }
    }

    def possiblyWithWildcard(info: ImportInfo): ImportInfo = {
      val needUpdate = info.singleNames.size >= settings.classCountToUseImportOnDemand
      val onlySingleNames = info.hiddenNames.isEmpty && info.renames.isEmpty && !info.hasWildcard

      if (!needUpdate || !onlySingleNames) return info

      val withWildcard = info.toWildcardInfo.withAllNamesForWildcard(rangeStartPsi)

      if (withWildcard.wildcardHasUnusedImplicit) return info

      updateWithWildcardNames(infos)

      val explicitNames = infos.flatMap {
        case `info` => Seq.empty
        case other => other.singleNames
      }.toSet

      val namesFromOtherWildcards = infos.flatMap {
        case `info` => Seq.empty
        case other => other.allNames
      }.toSet -- explicitNames

      val problematicNames = withWildcard.allNamesForWildcard & usedImportedNames
      val clashesWithOtherWildcards = problematicNames & namesFromOtherWildcards

      def notAtRangeStart = problematicNames.forall(name => !resolvesAtRangeStart(name))

      if (clashesWithOtherWildcards.size < info.singleNames.size && notAtRangeStart)
        withWildcard.copy(hiddenNames = clashesWithOtherWildcards)
      else info
    }

    for ((info, i) <- infos.zipWithIndex) {
      val newInfo = possiblyWithWildcard(info)
      if (info != newInfo)
        infos.update(i, newInfo)
    }
  }

  def insertImportInfos(infosToAdd: Seq[ImportInfo], infos: Seq[ImportInfo], rangeStart: PsiAnchor, settings: OptimizeImportSettings): Seq[ImportInfo] = {
    import settings._

    def addLastAndMoveUpwards(newInfo: ImportInfo, buffer: ArrayBuffer[ImportInfo]): Unit = {
      var i = buffer.size
      buffer.insert(i, newInfo)
      while(i > 0 && greater(buffer(i - 1), buffer(i), settings) && swapWithNext(buffer, i - 1)) {
        i -= 1
      }
    }

    def replace(oldInfos: Seq[ImportInfo], newInfos: Seq[ImportInfo], buffer: ArrayBuffer[ImportInfo]) = {
      val oldIndices = oldInfos.map(buffer.indexOf).filter(_ >= 0).sorted(Ordering[Int].reverse)
      if (oldIndices.nonEmpty) {
        val minIndex = oldIndices.last
        oldIndices.foreach(buffer.remove)
        buffer.insert(minIndex, newInfos: _*)
      }
      else {
        newInfos.foreach(addLastAndMoveUpwards(_, buffer))
      }
    }

    def withAliasedQualifier(info: ImportInfo): ImportInfo = {
      if (addFullQualifiedImports) return info

      for {
        oldInfo <- infos
        renamerPrefix = oldInfo.prefixQualifier
        (name, newName) <- oldInfo.renames
      } {
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

    val tooManySingleNames: Map[String, Boolean] = addedPrefixes.map { prefix =>
      val singleNamesCount = (actuallyInserted ++ infos)
        .filter(_.prefixQualifier == prefix)
        .flatMap(_.singleNames)
        .distinct.size
      prefix -> (singleNamesCount >= classCountToUseImportOnDemand)
    }.toMap

    def insertSimpleInfo(info: ImportInfo, buffer: ArrayBuffer[ImportInfo]): Unit = {
      val samePrefixInfos = buffer.filter(_.prefixQualifier == info.prefixQualifier)
      if (collectImports) {
        val merged = ImportInfo.merge(samePrefixInfos :+ info)
        replace(samePrefixInfos, merged.toSeq, buffer)
      }
      else addLastAndMoveUpwards(info, buffer)
    }

    def insertInfoWithWildcard(info: ImportInfo, buffer: ArrayBuffer[ImportInfo], usedNames: Set[String]): Unit = {
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
      if (collectImports) {
        val simpleMerged = ImportInfo.merge(simpleInfosToRemain ++ notSimpleMerged)
        replace(samePrefixInfos, simpleMerged.toSeq, buffer)
      }
      else {
        replace(samePrefixInfos, simpleInfosToRemain ++ notSimpleMerged, buffer)
      }
    }

    val needAdditionalInfo = infosToAdd.exists(_.hasWildcard) || addedPrefixes.exists(tooManySingleNames)
    val buffer = infos.to[ArrayBuffer]

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
      updateToWildcardImports(buffer, rangeStart, usedNames, settings)
    }
    else {
      actuallyInserted.foreach(insertSimpleInfo(_, buffer))
    }

    updateRootPrefix(buffer)
    buffer.toVector
  }

  private def swapWithNext(buffer: ArrayBuffer[ImportInfo], i: Int): Boolean = {
    val first: ImportInfo = buffer(i)
    val second: ImportInfo = buffer(i + 1)
    val firstPrefix: String = first.relative.getOrElse(first.prefixQualifier)
    val firstPart: String = getFirstId(firstPrefix)
    val secondPrefix = second.relative.getOrElse(second.prefixQualifier)
    val secondPart = getFirstId(secondPrefix)
    if (first.rootUsed || !second.allNames.contains(firstPart)) {
      if (second.rootUsed || !first.allNames.contains(secondPart)) {
        val t = first
        buffer(i) = second
        buffer(i + 1) = t
        true
      } else false
    } else false
  }

  private def mergeImportInfos(buffer: ArrayBuffer[ImportInfo]): ArrayBuffer[ImportInfo] = {
    def samePrefixAfter(i: Int): Int = {
      var j = i + 1
      while (j < buffer.length) {
        if (buffer(j).prefixQualifier == buffer(i).prefixQualifier) return j
        j += 1
      }
      -1
    }
    var i = 0
    while (i < buffer.length - 1) {
      val prefixIndex: Int = samePrefixAfter(i)
      if (prefixIndex != -1) {
        if (prefixIndex == i + 1) {
          val merged = buffer(i).merge(buffer(i + 1))
          buffer(i) = merged
          buffer.remove(i + 1)
        } else {
          if (swapWithNext(buffer, i)) {
            var j = i + 1
            var break = false
            while (!break && j != prefixIndex - 1) {
              if (!swapWithNext(buffer, j)) break = true
              j += 1
            }
            if (!break) {
              val merged = buffer(j).merge(buffer(j + 1))
              buffer(j) = merged
              buffer.remove(j + 1)
            }
          } else i += 1
        }
      } else i += 1
    }
    buffer
  }

  def getFirstId(s: String): String = {
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

  def findGroupIndex(info: String, settings: OptimizeImportSettings): Int = {
    val groups = settings.importLayout
    val suitable = groups.filter { group =>
      group != ScalaCodeStyleSettings.BLANK_LINE && (group == ScalaCodeStyleSettings.ALL_OTHER_IMPORTS ||
        info.startsWith(group))
    }
    val elem = suitable.tail.foldLeft(suitable.head) { (l, r) =>
      if (l == ScalaCodeStyleSettings.ALL_OTHER_IMPORTS) r
      else if (r == ScalaCodeStyleSettings.ALL_OTHER_IMPORTS) l
      else if (r.startsWith(l)) r
      else l
    }

    groups.indexOf(elem)
  }

  def greater(lPrefix: String, rPrefix: String, lText: String, rText: String, settings: OptimizeImportSettings): Boolean = {
    val lIndex = findGroupIndex(lPrefix, settings)
    val rIndex = findGroupIndex(rPrefix, settings)
    if (lIndex > rIndex) true
    else if (rIndex > lIndex) false
    else lText > rText
  }

  def greater(lInfo: ImportInfo, rInfo: ImportInfo, settings: OptimizeImportSettings): Boolean = {
    val textCreator = new ImportTextCreator
    val lPrefix: String = lInfo.prefixQualifier
    val rPrefix: String = rInfo.prefixQualifier
    val lText = textCreator.getImportText(lInfo, settings)
    val rText = textCreator.getImportText(rInfo, settings)
    ScalaImportOptimizer.greater(lPrefix, rPrefix, lText, rText, settings)
  }

  private def importsUsedFor(expr: ScExpression): Set[ImportUsed] = {
    val res = mutable.HashSet[ImportUsed]()

    res ++= expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType()).importsUsed

    expr match {
      case call: ScMethodCall => res ++= call.getImportsUsed
      case f: ScForStatement => res ++= ScalaPsiUtil.getExprImports(f)
      case _ =>
    }

    expr.findImplicitParameters match {
      case Some(seq) =>
        for (rr <- seq if rr != null) {
          res ++= rr.importsUsed
        }
      case _ =>
    }
    res.toSet
  }

  private def collectImportsUsed(element: PsiElement, imports: util.Set[ImportUsed], names: util.Set[UsedName]): Unit = {
    def implicitlyImported(srr: ScalaResolveResult) = {
      srr.element match {
        case c: PsiClass =>
          val qName = c.qualifiedName
          val name = c.name
          qName == s"scala.$name" || qName == s"java.lang.$name"
        case ContainingClass(o: ScObject) =>
          o.isPackageObject && Set("scala", "scala.Predef").contains(o.qualifiedName)
        case _ => false
      }
    }

    def addResult(srr: ScalaResolveResult, fromElem: PsiElement) = {
      val importsUsed = srr.importsUsed
      if (importsUsed.nonEmpty || implicitlyImported(srr)) {
        imports.addAll(importsUsed)
        names.add(UsedName(srr.name, fromElem.getTextRange.getStartOffset))
      }
    }

    def addFromExpression(expr: ScExpression): Unit = {
      val afterImplicitConversion = expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType())

      imports.addAll(afterImplicitConversion.importsUsed)
      afterImplicitConversion.implicitFunction.foreach(f => names.add(UsedName(f.name, expr.getTextRange.getStartOffset)))

      expr match {
        case call: ScMethodCall => imports.addAll(call.getImportsUsed)
        case f: ScForStatement => imports.addAll(ScalaPsiUtil.getExprImports(f))
        case _ =>
      }

      expr.findImplicitParameters match {
        case Some(seq) =>
          for (rr <- seq if rr != null) {
            addResult(rr, expr)
          }
        case _ =>
      }
    }

    element match {
      case impQual: ScStableCodeReferenceElement
        if impQual.qualifier.isEmpty && PsiTreeUtil.getParentOfType(impQual, classOf[ScImportStmt]) != null =>
        //don't add as ImportUsed to be able to optimize it away if it is used only in unused imports
        val hasImportUsed = impQual.multiResolve(false).exists {
          case srr: ScalaResolveResult => srr.importsUsed.nonEmpty
          case _ => false
        }
        if (hasImportUsed) {
          names.add(UsedName(impQual.refName, impQual.getTextRange.getStartOffset))
        }
      case ref: ScReferenceElement if PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) == null =>
        ref.multiResolve(false) foreach {
          case scalaResult: ScalaResolveResult => addResult(scalaResult, ref)
          case _ =>
        }
      case simple: ScSimpleTypeElement =>
        simple.findImplicitParameters match {
          case Some(parameters) =>
            parameters.foreach {
              case r: ScalaResolveResult => addResult(r, simple)
              case _ =>
            }
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

  //quite heavy computation, is really needed only for dealing with wildcard imports
  def collectUsedImportedNames(holder: ScImportsHolder): Set[String] = {
    val imports = new util.HashSet[ImportUsed]()
    val names = new util.HashSet[UsedName]()

    holder.depthFirst.foreach {
      case ImportUser(elem) => collectImportsUsed(elem, imports, names)
      case _ =>
    }
    names.toSet.map((x: UsedName) => x.name)
  }


  def createInfo(imp: ScImportStmt, isImportUsed: ImportUsed => Boolean = _ => true): Seq[ImportInfo] =
    imp.importExprs.flatMap(ImportInfo(_, isImportUsed))


}
