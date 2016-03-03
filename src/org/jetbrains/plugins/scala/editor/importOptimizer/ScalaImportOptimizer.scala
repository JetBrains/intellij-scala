package org.jetbrains.plugins.scala
package editor.importOptimizer


import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.intellij.concurrency.JobLauncher
import com.intellij.lang.{ImportOptimizer, LanguageImportStatements}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{EmptyRunnable, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScForStatement, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
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
    val usedImportedNames = ContainerUtil.newConcurrentSet[String]()
    val allElementsInFile = allElementsIn(scalaFile)

    val progressManager: ProgressManager = ProgressManager.getInstance()
    val indicator: ProgressIndicator =
      if (progressIndicator != null) progressIndicator
      else if (progressManager.hasProgressIndicator) progressManager.getProgressIndicator
      else null
    if (indicator != null) indicator.setText2(file.getName + ": analyzing imports usage")

    val size = allElementsInFile.size * 2 //processAllElementsConcurrentlyUnderProgress will be called 2 times
    val counter = new AtomicInteger(0)

    def processAllElementsConcurrentlyUnderProgress(action: PsiElement => Unit) = {
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(allElementsInFile, indicator, true, true, new Processor[PsiElement] {
        override def process(element: PsiElement): Boolean = {
          val count: Int = counter.getAndIncrement
          if (count <= size && indicator != null) indicator.setFraction(count.toDouble / size)

          action(element)

          true
        }
      })
    }

    processAllElementsConcurrentlyUnderProgress { element =>
      val (imports, names) = collectImportsUsed(element)
      usedImports.addAll(imports)
      usedImportedNames.addAll(names)
    }

    if (indicator != null) indicator.setText2(file.getName + ": collecting additional info")

    def collectRanges(namesAtRangeStart: ScImportStmt => Set[String],
                      createInfo: ScImportStmt => Seq[ImportInfo]): Seq[(TextRange, RangeInfo)] = {
      val importsInfo = ContainerUtil.newConcurrentMap[TextRange, RangeInfo]()
      processAllElementsConcurrentlyUnderProgress {
        case holder: ScImportsHolder =>
          importsInfo.putAll(collectImportRanges(holder, namesAtRangeStart, createInfo))
        case _ =>
      }
      importsInfo.toSeq.sortBy(_._1.getStartOffset)
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

    val importsInfo = collectRanges(namesAtRangeStart, createInfo(_, isImportUsed))

    val optimized = importsInfo.map {
      case (range, rangeInfo) => (range, optimizedImportInfos(rangeInfo, settings))
    }

    new Runnable {
      def run() {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document: Document = documentManager.getDocument(scalaFile)
        documentManager.commitDocument(document)

        val ranges: Seq[(TextRange, Seq[ImportInfo])] =
          if (document.getText != analyzingDocumentText)  //something was changed...
            sameInfosWithUpdatedRanges()
          else optimized

        for ((range, importInfos) <- ranges.reverseIterator) {
          replaceWithNewImportInfos(range, importInfos, settings, document)
        }
        documentManager.commitDocument(document)
      }

      def sameInfosWithUpdatedRanges(): Seq[(TextRange, Seq[ImportInfo])] = {
        optimized.zip {
          collectRanges(_ => Set.empty, _ => Seq.empty)
        }.map {
          case ((_, infos), (range, _)) => (range, infos)
        }
      }
    }
  }

  protected def getImportTextCreator: ImportTextCreator = new ImportTextCreator

  protected def isImportDelimiter(psi: PsiElement) = psi.isInstanceOf[PsiWhiteSpace]

  def supports(file: PsiFile): Boolean = file.isInstanceOf[ScalaFile] && file.getViewProvider.getAllFiles.size() < 3

  def replaceWithNewImportInfos(range: TextRange, importInfos: Seq[ImportInfo], settings: OptimizeImportSettings, document: Document): Unit = {
    val textCreator = getImportTextCreator
    val documentText = document.getText
    import settings._

    @tailrec
    def indentForOffset(index: Int, res: String = ""): String = {
      if (index <= 0) res
      else {
        val c = documentText.charAt(index - 1)
        if (c == ' ' || c == '\t') indentForOffset(index - 1, s"$c$res")
        else res
      }
    }
    val newLineWithIndent: String = "\n" + indentForOffset(range.getStartOffset)

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

    val newRange: TextRange =
      if (text.isEmpty) {
        var start = range.getStartOffset
        while (start > 0 && documentText(start - 1).isWhitespace) start = start - 1
        val end = range.getEndOffset
        new TextRange(start, end)
      } else range

    document.replaceString(newRange.getStartOffset, newRange.getEndOffset, text)
  }

  def collectImportRanges(holder: ScImportsHolder,
                          namesAtRangeStart: ScImportStmt => Set[String],
                          createInfo: ScImportStmt => Seq[ImportInfo]): Map[TextRange, RangeInfo] = {
    val result = mutable.Map[TextRange, RangeInfo]()
    var rangeStart = -1
    var rangeEnd = -1
    var namesAtStart: Set[String] = Set.empty
    val isLocalRange = holder match {
      case _: ScalaFile | _: ScPackaging => false
      case _ => true
    }
    val infos = ArrayBuffer[ImportInfo]()
    val allUsedImportedNames = collectUsedImportedNamesSorted(holder)

    def addRange(): Unit = {
      if (rangeStart != -1) {
        val usedImportedNames = allUsedImportedNames.dropWhile(_._2 < rangeStart).map(_._1).toSet
        val rangeInfo = RangeInfo(namesAtStart, infos.to[Seq], usedImportedNames, isLocalRange)
        result += (new TextRange(rangeStart, rangeEnd) -> rangeInfo)
        rangeStart = -1
        rangeEnd = -1
        namesAtStart = Set.empty
        infos.clear()
      }
    }

    def initRange(psi: PsiElement) {
      rangeStart = psi.getTextRange.getStartOffset
      rangeEnd = psi.getTextRange.getEndOffset
    }
     
    for (child <- holder.getNode.getChildren(null)) {
      child.getPsi match {
        case whitespace: PsiWhiteSpace =>
        case d: ScDocComment => addRange()
        case comment: PsiComment =>
          val next = comment.getNextSibling
          val prev = comment.getPrevSibling
          (next, prev) match {
            case (w1: PsiWhiteSpace, w2: PsiWhiteSpace) if
            w1.getText.contains("\n") && w2.getText.contains("\n") => addRange()
            case _ =>
          }
        case s: LeafPsiElement =>
        case a: PsiElement if isImportDelimiter(a) => //do nothing
        case imp: ScImportStmt =>
          if (rangeStart == -1) {
            imp.getPrevSibling match {
              case a: PsiElement if isImportDelimiter(a) && !a.isInstanceOf[PsiWhiteSpace] =>
                initRange(a)
                rangeEnd = imp.getTextRange.getEndOffset
              case _ => initRange(imp)
            }
            namesAtStart = namesAtRangeStart(imp)
          } else {
            rangeEnd = imp.getTextRange.getEndOffset
          }
          infos ++= createInfo(imp)
        case _ => addRange()
      }
    }
    addRange()
    result.toMap
  }
}

object ScalaImportOptimizer {
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
      s"import $root${relative.getOrElse(prefixQualifier)}.$postfix"
    }

    def getImportText(importInfo: ImportInfo, settings: OptimizeImportSettings): String =
      getImportText(importInfo, settings.isUnicodeArrow, settings.spacesInImports, settings.sortImports)
  }

  def optimizedImportInfos(rangeInfo: RangeInfo, settings: OptimizeImportSettings): Seq[ImportInfo] = {
    import settings._
    val RangeInfo(namesAtRangeStart, importInfos, usedImportedNames, isLocalRange) = rangeInfo

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

    updateToWildcardImports(result, namesAtRangeStart, usedImportedNames, settings)
    updateRootPrefix(result, namesAtRangeStart)

    result.to[immutable.Seq]
  }

  def updateRootPrefix(importInfos: ArrayBuffer[ImportInfo], namesAtRangeStart: Set[String]): Unit = {
    val holderNames = new mutable.HashSet[String]()
    holderNames ++= namesAtRangeStart

    for (i <- importInfos.indices) {
      val info = importInfos(i)
      val canAddRoot = info.relative.isEmpty && !info.rootUsed && info.isStableImport
      if (canAddRoot && holderNames.contains(getFirstId(info.prefixQualifier)))
        importInfos.update(i, info.withRootPrefix)
      holderNames ++= info.allNames
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

  def updateToWildcardImports(infos: ArrayBuffer[ImportInfo], namesAtRangeStart: Set[String],
                              usedImportedNames: Set[String], settings: OptimizeImportSettings): Unit = {

    def shouldUpdate(info: ImportInfo) = {
      val needUpdate = info.singleNames.size >= settings.classCountToUseImportOnDemand
      val mayUpdate = info.hiddenNames.isEmpty && info.renames.isEmpty && !info.wildcardHasUnusedImplicit
      if (needUpdate && mayUpdate) {
        val explicitNames = infos.flatMap {
          case `info` => Seq.empty
          case other => other.singleNames
        }.toSet
        val namesFromOtherWildcards = infos.flatMap {
          case `info` => Seq.empty
          case other => other.allNames
        }.toSet -- explicitNames
        (info.allNamesForWildcard & usedImportedNames & (namesFromOtherWildcards ++ namesAtRangeStart)).isEmpty
      }
      else false
    }

    for ((info, i) <- infos.zipWithIndex) {
      if (shouldUpdate(info)) {
        val newInfo = info.toWildcardInfo
        infos.update(i, newInfo)
      }
    }
  }

  def insertInto(infos: ArrayBuffer[ImportInfo],
                 infoToInsert: ImportInfo,
                 usedImportedNames: Set[String],
                 settings: OptimizeImportSettings): Unit = {

    import settings._

    def addLastAndMoveUpwards(newInfo: ImportInfo): Unit = {
      var i = infos.size
      infos.insert(i, newInfo)
      while(i > 0 && greater(infos(i - 1), infos(i), settings) && swapWithNext(infos, i - 1)) {
        i -= 1
      }
    }

    def replace(oldInfos: Seq[ImportInfo], newInfos: Seq[ImportInfo]) = {
      val oldIndices = oldInfos.map(infos.indexOf).filter(_ >= 0).sorted(Ordering[Int].reverse)
      if (oldIndices.nonEmpty) {
        val minIndex = oldIndices.last
        oldIndices.foreach(infos.remove)
        infos.insert(minIndex, newInfos: _*)
      }
      else {
        newInfos.foreach(addLastAndMoveUpwards)
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

    val actuallyInserted = withAliasedQualifier(infoToInsert)

    val (samePrefixInfos, otherInfos) = infos.partition(_.prefixQualifier == actuallyInserted.prefixQualifier)
    val samePrefixWithNewSplitted = samePrefixInfos.flatMap(_.split) ++ actuallyInserted.split
    val (simpleInfos, notSimpleInfos) = samePrefixWithNewSplitted.partition(_.singleNames.nonEmpty)

    def insertInfoWithWildcard(): Unit = {
      val (wildcard, withArrows) = notSimpleInfos.partition(_.hasWildcard)
      val namesFromOtherWildcards = otherInfos.flatMap(_.namesFromWildcard).toSet
      val simpleNamesToRemain = simpleInfos.flatMap(_.singleNames).toSet & namesFromOtherWildcards & usedImportedNames
      val simpleInfosToRemain = simpleInfos.filter(si => simpleNamesToRemain.contains(si.singleNames.head))
      val renames = withArrows.flatMap(_.renames)
      val hiddenNames = withArrows.flatMap(_.hiddenNames)
      val newHiddenNames =
        (actuallyInserted.allNamesForWildcard -- simpleNamesToRemain -- renames.map(_._1) -- hiddenNames) &
          namesFromOtherWildcards & usedImportedNames

      withArrows ++= newHiddenNames.map(actuallyInserted.toHiddenNameInfo)

      val notSimpleMerged = ImportInfo.merge(withArrows ++ wildcard)
      if (collectImports) {
        val simpleMerged = ImportInfo.merge(simpleInfosToRemain ++ notSimpleMerged)
        replace(samePrefixInfos, simpleMerged.toSeq)
      }
      else {
        replace(samePrefixInfos, simpleInfosToRemain ++ notSimpleMerged)
      }
    }

    if (actuallyInserted.hasWildcard) {
      insertInfoWithWildcard()
    }
    else if (simpleInfos.size >= settings.classCountToUseImportOnDemand && !actuallyInserted.wildcardHasUnusedImplicit) {
      notSimpleInfos += actuallyInserted.toWildcardInfo
      insertInfoWithWildcard()
    }
    else if (collectImports) {
      val merged = ImportInfo.merge(samePrefixInfos :+ actuallyInserted)
      replace(samePrefixInfos, merged.toSeq)
    }
    else addLastAndMoveUpwards(actuallyInserted)
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
              val merged = buffer(i).merge(buffer(j + 1))
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

  private def collectImportsUsed(element: PsiElement): (Set[ImportUsed], Set[String]) = {
    val result = mutable.Set[ImportUsed]()
    val importedNames = mutable.Set[String]()
    element match {
      case ref: ScReferenceElement if PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) == null =>
        ref.multiResolve(false) foreach {
          case scalaResult: ScalaResolveResult if scalaResult.importsUsed.nonEmpty => 
            result ++= scalaResult.importsUsed
            importedNames += scalaResult.name
          case _ =>
        }
      case simple: ScSimpleTypeElement =>
        simple.findImplicitParameters match {
          case Some(parameters) =>
            parameters.foreach {
              case r: ScalaResolveResult => result ++= r.importsUsed
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    //separate match to have reference expressions processed
    element match {
      case expression: ScExpression => result ++= importsUsedFor(expression)
      case _ =>
    }
    (result.toSet, importedNames.toSet)
  }

  def namesAtRangeStart(imp: ScImportStmt): Set[String] = {
    val refText = "someIdentifier"
    val reference = ScalaPsiElementFactory.createReferenceFromText(refText, imp.getContext, imp)
    val rangeNamesSet = new mutable.HashSet[String]()
    def addName(name: String): Unit = rangeNamesSet += ScalaNamesUtil.changeKeyword(name)
    reference.getResolveResultVariants.foreach {
      case ScalaResolveResult(p: PsiPackage, _) =>
        if (p.getParentPackage != null && p.getParentPackage.getName != null) addName(p.getName)
      case ScalaResolveResult(o: ScObject, _) if o.isPackageObject =>
        if (o.qualifiedName.contains(".")) addName(o.name)
      case ScalaResolveResult(o: ScObject, _) =>
        o.getParent match {
          case file: ScalaFile =>
          case _ => addName(o.name)
        }
      case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => addName(td.name)
      case ScalaResolveResult(_: ScTypeDefinition, _) =>
      case ScalaResolveResult(c: PsiClass, _) => addName(c.getName)
      case ScalaResolveResult(f: PsiField, _) if f.hasFinalModifier =>
        addName(f.getName)
      case _ =>
    }
    rangeNamesSet.toSet
  }

  private def collectUsedImportedNamesSorted(holder: ScImportsHolder): ArrayBuffer[(String, Int)] = {
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

    val namesWithOffset = ArrayBuffer[(String, Int)]()
    holder.depthFirst.foreach {
      case ref: ScReferenceElement if ref.qualifier.isEmpty =>
        ref.multiResolve(false) foreach {
          case srr: ScalaResolveResult if srr.importsUsed.nonEmpty =>
            namesWithOffset += (srr.name -> ref.getTextRange.getStartOffset)
          case srr: ScalaResolveResult if implicitlyImported(srr) =>
            namesWithOffset += (srr.name -> ref.getTextRange.getStartOffset)
          case _ =>
        }
      case _ =>
    }
    namesWithOffset.sortBy(_._2)
  }

  def collectUsedImportedNames(holder: ScImportsHolder): Set[String] = {
    collectUsedImportedNamesSorted(holder).map(_._1).toSet
  }


  def createInfo(imp: ScImportStmt, isImportUsed: ImportUsed => Boolean = _ => true): Seq[ImportInfo] =
    imp.importExprs.flatMap(ImportInfo(_, isImportUsed))

  private def allElementsIn(container: PsiElement): util.ArrayList[PsiElement] = {
    val result = new util.ArrayList[PsiElement]()
    def addChildren(elem: PsiElement): Unit = {
      result.add(elem)
      elem.getChildren.foreach(addChildren)
    }
    addChildren(container)
    result
  }


}