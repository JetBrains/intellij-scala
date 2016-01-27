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
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScForStatement, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Set, immutable, mutable}

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

    def createInfo(imp: ScImportStmt): Seq[ImportInfo] = imp.importExprs.flatMap(getImportInfo(_, isImportUsed))

    val importsInfo = collectRanges(namesAtRangeStart, createInfo)

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

  private def replaceWithNewImportInfos(range: TextRange, importInfos: Seq[ImportInfo], settings: OptimizeImportSettings, document: Document): Unit = {
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
      blankLines + textCreator.getImportText(info, isUnicodeArrow, spacesInImports, sortImports)
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

  private def collectImportRanges(holder: ScImportsHolder,
                                  namesAtRangeStart: ScImportStmt => Set[String],
                                  createInfo: ScImportStmt => Seq[ImportInfo]): Map[TextRange, RangeInfo] = {
    def collectUsedImportedNames() = {
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

    val result = mutable.Map[TextRange, RangeInfo]()
    var rangeStart = -1
    var rangeEnd = -1
    var namesAtStart: Set[String] = Set.empty
    val isLocalRange = holder match {
      case _: ScalaFile | _: ScPackaging => false
      case _ => true
    }
    val infos = ArrayBuffer[ImportInfo]()
    val allUsedImportedNames = collectUsedImportedNames()

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
    val topLevelFile = file.getViewProvider.getPsi(file.getViewProvider.getBaseLanguage)
    val optimizers = LanguageImportStatements.INSTANCE.forFile(topLevelFile)
    if (optimizers.isEmpty) return

    if (topLevelFile.getViewProvider.getPsi(ScalaFileType.SCALA_LANGUAGE) == null) return

    val i = optimizers.iterator()
    while (i.hasNext) {
      val opt = i.next()
      if (opt supports topLevelFile) {
        opt.processFile(topLevelFile).run()
        return
      }
    }
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
      groupStrings ++= singleNames
      val arrow = if (isUnicodeArrow) ScalaTypedHandler.unicodeCaseArrow else "=>"
      groupStrings ++= renames.map(pair => s"${pair._1} $arrow ${pair._2}")
      groupStrings ++= hiddenNames.map(_ + s" $arrow _")
      val sortedGroupStrings = if (sortLexicografically) groupStrings.sorted else groupStrings
      if (hasWildcard) sortedGroupStrings += "_"
      val space = if (spacesInImports) " " else ""
      val root = if (rootUsed) s"${_root_prefix}." else ""
      val postfix =
        if (sortedGroupStrings.length > 1 || renames.nonEmpty || hiddenNames.nonEmpty) sortedGroupStrings.mkString(s"{$space", ", ", s"$space}")
        else sortedGroupStrings(0)
      s"import $root${relative.getOrElse(prefixQualifier)}.$postfix"
    }
  }

  case class ImportInfo(importsUsed: Set[ImportUsed],
                        prefixQualifier: String,
                        relative: Option[String],
                        allNames: Set[String],
                        singleNames: Set[String],
                        renames: Map[String, String],
                        hiddenNames: Set[String],
                        hasWildcard: Boolean,
                        rootUsed: Boolean,
                        isStableImport: Boolean = true,
                        allNamesForWildcard: Set[String] = Set.empty,
                        wildcardHasUnusedImplicit: Boolean = false) {
    def withoutRelative: ImportInfo =
      if (relative.isDefined || rootUsed) copy(relative = None) else this
  }

  def name(s: String): String = {
    if (ScalaNamesUtil.isKeyword(s)) s"`$s`"
    else s
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
      else buffer.flatMap(split)

    updateToWildcardImports(result, namesAtRangeStart, usedImportedNames, settings)
    updateRootUsed(result, namesAtRangeStart)

    result.to[immutable.Seq]
  }

  def updateRootUsed(importInfos: ArrayBuffer[ImportInfo], namesAtRangeStart: Set[String]): Unit = {
    val holderNames = new mutable.HashSet[String]()
    holderNames ++= namesAtRangeStart

    for (i <- importInfos.indices) {
      val info = importInfos(i)
      val canAddRoot = info.relative.isEmpty && !info.rootUsed && info.isStableImport
      if (canAddRoot && holderNames.contains(getFirstId(info.prefixQualifier)))
        importInfos.update(i, info.copy(rootUsed = true))
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
        val newInfo = info.copy(hasWildcard = true, singleNames = Set.empty, allNames = info.allNamesForWildcard)
        infos.update(i, newInfo)
      }
    }
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

  private def split(info: ImportInfo): Seq[ImportInfo] = {
    val result = new ArrayBuffer[ImportInfo]()
    result ++= info.singleNames.toSeq.sorted.map { name =>
      info.copy(singleNames = Set(name), renames = Map.empty, hiddenNames = Set.empty, hasWildcard = false)
    }
    result ++= info.renames.map { rename =>
      info.copy(renames = Map(rename), singleNames = Set.empty, hiddenNames = Set.empty, hasWildcard = false)
    }
    result ++= info.hiddenNames.map { hided =>
      info.copy(hiddenNames = Set(hided), singleNames = Set.empty, renames = Map.empty, hasWildcard = false)
    }
    if (info.hasWildcard) {
      result += info.copy(singleNames = Set.empty, renames = Map.empty, hiddenNames = Set.empty)
    }
    result
  }

  private def mergeImportInfos(buffer: ArrayBuffer[ImportInfo]): ArrayBuffer[ImportInfo] = {
    def merge(first: ImportInfo, second: ImportInfo): ImportInfo = {
      val relative = first.relative.orElse(second.relative)
      val rootUsed = relative.isEmpty && (first.rootUsed || second.rootUsed)
      new ImportInfo(first.importsUsed ++ second.importsUsed, first.prefixQualifier, relative,
        first.allNames ++ second.allNames, first.singleNames ++ second.singleNames,
        first.renames ++ second.renames, first.hiddenNames ++ second.hiddenNames,
        first.hasWildcard || second.hasWildcard, rootUsed, first.isStableImport && second.isStableImport,
        first.allNamesForWildcard)
    }
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
          val merged = merge(buffer(i), buffer(i + 1))
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
              val merged = merge(buffer(j), buffer(j + 1))
              buffer(j) = merged
              buffer.remove(j + 1)
            }
          } else i += 1
        }
      } else i += 1
    }
    buffer
  }

  def getImportInfo(imp: ScImportExpr, isImportUsed: ImportUsed => Boolean): Option[ImportInfo] = {
    val importsUsed = ArrayBuffer[ImportUsed]()
    val allNames = mutable.HashSet[String]()
    val singleNames = mutable.HashSet[String]()
    val renames = mutable.HashMap[String, String]()
    val hiddenNames = mutable.HashSet[String]()
    var hasWildcard = false
    val namesForWildcard = mutable.HashSet[String]()
    val implicitNames = mutable.HashSet[String]()
    var hasNonUsedImplicits = false

    def shouldAddName(resolveResult: ResolveResult): Boolean = {
      resolveResult match {
        case ScalaResolveResult(p: PsiPackage, _) => true
        case ScalaResolveResult(m: PsiMethod, _) => m.containingClass != null
        case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => true
        case ScalaResolveResult(_: ScTypeAlias, _) => true
        case ScalaResolveResult(_: PsiClass, _) => true
        case ScalaResolveResult(f: PsiField, _) => f.hasFinalModifier
        case _ => false
      }
    }

    def addAllNames(ref: ScStableCodeReferenceElement, nameToAdd: String): Unit = {
      if (ref.multiResolve(false).exists(shouldAddName)) allNames += nameToAdd
    }

    def collectAllNamesForWildcard(): Unit = {
      val refText = imp.qualifier.getText + ".someIdentifier"
      val reference = ScalaPsiElementFactory.createReferenceFromText(refText, imp.qualifier.getContext, imp.qualifier)
        .asInstanceOf[ScStableCodeReferenceElementImpl]
      val processor = new CompletionProcessor(StdKinds.stableImportSelector, reference, collectImplicits = true, includePrefixImports = false)

      reference.doResolve(reference, processor).foreach {
        case rr: ScalaResolveResult if shouldAddName(rr) =>
          val element = rr.element
          val nameToAdd = name(element.name)
          namesForWildcard += nameToAdd
          if (ScalaPsiUtil.isImplicit(element))
            implicitNames += nameToAdd
        case _ =>
      }
    }

    collectAllNamesForWildcard()

    if (!imp.singleWildcard && imp.selectorSet.isEmpty) {
      val importUsed: ImportExprUsed = ImportExprUsed(imp)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        imp.reference match {
          case Some(ref) =>
            singleNames += ref.refName
            addAllNames(ref, ref.refName)
          case None => //something is not valid
        }
      }
    } else if (imp.singleWildcard) {
      val importUsed =
        if (imp.selectorSet.isEmpty) ImportExprUsed(imp)
        else ImportWildcardSelectorUsed(imp)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        hasWildcard = true
        allNames ++= namesForWildcard
      }
    }
    for (selector <- imp.selectors) {
      val importUsed: ImportSelectorUsed = ImportSelectorUsed(selector)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        val refName: String = selector.reference.refName
        if (selector.isAliasedImport) {
          val importedName: String = selector.importedName
          if (importedName == "_") {
            hiddenNames += refName
          } else if (importedName == refName) {
            singleNames += refName
            addAllNames(selector.reference, refName)
          } else {
            renames += ((refName, importedName))
            addAllNames(selector.reference, importedName)
          }
        } else {
          singleNames += refName
          addAllNames(selector.reference, refName)
        }
      }
    }
    allNames --= hiddenNames
    hasNonUsedImplicits = (implicitNames -- singleNames).nonEmpty

    if (importsUsed.isEmpty) return None //all imports are empty

    val qualifier = imp.qualifier
    if (qualifier == null) return None //ignore invalid imports

    @tailrec
    def deepestQualifier(ref: ScStableCodeReferenceElement): ScStableCodeReferenceElement = {
      ref.qualifier match {
        case Some(q) => deepestQualifier(q)
        case None => ref
      }
    }

    def packageFqn(p: PsiPackage): String = {
      p.getParentPackage match {
        case null => name(p.getName)
        case parent if parent.getName == null => name(p.getName)
        case parent => packageFqn(parent) + "." + name(p.getName)
      }
    }

    @tailrec
    def explicitQualifierString(ref: ScStableCodeReferenceElement, withDeepest: Boolean, res: String = ""): String = {
      ref.qualifier match {
        case Some(q) => explicitQualifierString(q, withDeepest, ref.refName + withDot(res))
        case None if withDeepest && ref.refName != _root_prefix => ref.refName + withDot(res)
        case None => res
      }
    }

    def withDot(s: String): String = {
      if (s.isEmpty) "" else "." + s
    }

    @tailrec
    def isRelativeObject(o: ScObject, res: Boolean = false): Boolean = {
      o.getContext match {
        case _: ScTemplateBody =>
          o.containingClass match {
            case containingObject: ScObject => isRelativeObject(containingObject, res = true)
            case _ => false //inner of some class/trait
          }
        case _: ScPackaging | _: ScalaFile => true
        case _ => res //something in default package or in local object
      }
    }

    def qualifiedRef(ref: ScStableCodeReferenceElement): String = {
      if (ref.getText == _root_prefix) return _root_prefix

      val refName = ref.refName
      ref.bind() match {
        case Some(ScalaResolveResult(p: PsiPackage, _)) =>
          if (p.getParentPackage != null && p.getParentPackage.getName != null) packageFqn(p)
          else refName
        case Some(ScalaResolveResult(o: ScObject, _)) =>
          if (isRelativeObject(o)) o.qualifiedName
          else refName
        case Some(ScalaResolveResult(c: PsiClass, _)) =>
          val parts = c.qualifiedName.split('.')
          if (parts.length > 1) parts.map(name).mkString(".") else refName
        case Some(ScalaResolveResult(td: ScTypedDefinition, _)) =>
          ScalaPsiUtil.nameContext(td) match {
            case m: ScMember =>
              m.containingClass match {
                case o: ScObject if isRelativeObject(o, res = true) =>
                  o.qualifiedName + withDot(refName)
                case _ => refName
              }
            case _ => refName
          }
        case Some(ScalaResolveResult(f: PsiField, _)) =>
          val clazzFqn = f.containingClass match {
            case null => throw new IllegalStateException() //somehting is wrong
            case clazz => clazz.qualifiedName.split('.').map(name).mkString(".")
          }
          clazzFqn + withDot(refName)
        case _ => throw new IllegalStateException() //do not process invalid import
      }
    }

    val deepRef = deepestQualifier(qualifier)
    val rootUsed = deepRef.getText == _root_prefix

    val (prefixQualifier, isRelative) =
      if (rootUsed) (explicitQualifierString(qualifier, withDeepest = false), false)
      else {
        val qualifiedDeepRef =
          try qualifiedRef(deepRef)
          catch {
            case _: IllegalStateException => return None
          }
        val prefixQual = qualifiedDeepRef + withDot(explicitQualifierString(qualifier, withDeepest = false))
        val relative = qualifiedDeepRef != deepRef.getText
        (prefixQual, relative)
      }

    val relativeQualifier =
      if (isRelative) Some(explicitQualifierString(qualifier, withDeepest = true))
      else None

    val isStableImport = {
      deepRef.resolve() match {
        case named: PsiNamedElement => ScalaPsiUtil.hasStablePath(named)
        case _ => false
      }
    }

    Some(new ImportInfo(importsUsed.toSet, prefixQualifier, relativeQualifier, allNames.toSet,
      singleNames.toSet, renames.toMap, hiddenNames.toSet, hasWildcard, rootUsed,
      isStableImport, namesForWildcard, hasNonUsedImplicits))
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
    import settings._
    val textCreator = new ImportTextCreator
    val lPrefix: String = lInfo.prefixQualifier
    val rPrefix: String = rInfo.prefixQualifier
    val lText = textCreator.getImportText(lInfo, isUnicodeArrow, spacesInImports, sortImports)
    val rText = textCreator.getImportText(rInfo, isUnicodeArrow, spacesInImports, sortImports)
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
    res
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
    (result, importedNames)
  }

  private def namesAtRangeStart(imp: ScImportStmt): Set[String] = {
    val refText = "someIdentifier"
    val reference = ScalaPsiElementFactory.createReferenceFromText(refText, imp.getContext, imp)
    val rangeNamesSet = new mutable.HashSet[String]()
    def addName(name: String): Unit = rangeNamesSet += name
    reference.getResolveResultVariants.foreach {
      case ScalaResolveResult(p: PsiPackage, _) =>
        if (p.getParentPackage != null && p.getParentPackage.getName != null) addName(name(p.getName))
      case ScalaResolveResult(o: ScObject, _) if o.isPackageObject =>
        if (o.qualifiedName.contains(".")) addName(o.name)
      case ScalaResolveResult(o: ScObject, _) =>
        o.getParent match {
          case file: ScalaFile =>
          case _ => addName(o.name)
        }
      case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => addName(td.name)
      case ScalaResolveResult(_: ScTypeDefinition, _) =>
      case ScalaResolveResult(c: PsiClass, _) => addName(name(c.getName))
      case ScalaResolveResult(f: PsiField, _) if f.hasFinalModifier =>
        addName(name(f.getName))
      case _ =>
    }
    rangeNamesSet.toSet
  }

  private def allElementsIn(container: PsiElement): util.ArrayList[PsiElement] = {
    val result = new util.ArrayList[PsiElement]()
    def addChildren(elem: PsiElement): Unit = {
      result.add(elem)
      elem.getChildren.foreach(addChildren)
    }
    addChildren(container)
    result
  }

  private case class RangeInfo(namesAtRangeStart: Set[String], importInfos: Seq[ImportInfo], 
                               usedImportedNames: Set[String], isLocal: Boolean)
}

case class OptimizeImportSettings(addFullQualifiedImports: Boolean,
                                  isLocalImportsCanBeRelative: Boolean,
                                  sortImports: Boolean,
                                  collectImports: Boolean,
                                  isUnicodeArrow: Boolean,
                                  spacesInImports: Boolean,
                                  classCountToUseImportOnDemand: Int,
                                  importLayout: Array[String],
                                  isAlwaysUsedImport: String => Boolean) {

  private def this(s: ScalaCodeStyleSettings) {
    this(
      s.isAddFullQualifiedImports,
      s.isDoNotChangeLocalImportsOnOptimize,
      s.isSortImports,
      s.isCollectImports,
      s.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR,
      s.SPACES_IN_IMPORTS,
      s.getClassCountToUseImportOnDemand,
      s.getImportLayout,
      s.isAlwaysUsedImport
    )
  }
}

object OptimizeImportSettings {
  def apply(project: Project) = new OptimizeImportSettings(ScalaCodeStyleSettings.getInstance(project))
}