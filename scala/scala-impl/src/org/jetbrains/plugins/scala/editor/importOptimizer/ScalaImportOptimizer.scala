package org.jetbrains.plugins.scala
package editor.importOptimizer


import java.util
import java.util.concurrent.atomic.AtomicInteger
import com.intellij.concurrency.JobLauncher
import com.intellij.lang.{ImportOptimizer, LanguageImportStatements}
import com.intellij.notification.{Notification, NotificationDisplayType, NotificationGroup, NotificationType}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFor, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

class ScalaImportOptimizer extends ImportOptimizer {

  import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._

  protected def settings(file: PsiFile): OptimizeImportSettings = OptimizeImportSettings(file)

  override def processFile(file: PsiFile): Runnable = processFile(file, null)

  def processFile(file: PsiFile, progressIndicator: ProgressIndicator = null): Runnable = {
    def collectImportHoldersAndUsers: (util.ArrayList[ScImportsHolder], util.ArrayList[PsiElement]) = {
      val holders = new util.ArrayList[ScImportsHolder]()
      val users = new util.ArrayList[PsiElement]()

      file.depthFirst().foreach { elem =>
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

    val scalaFile: ScalaFile = file match {
      case scFile: ScalaFile => scFile
      case multiRootFile: PsiFile if multiRootFile.getViewProvider.getLanguages.contains(ScalaLanguage.INSTANCE) =>
        multiRootFile.getViewProvider.getPsi(ScalaLanguage.INSTANCE).asInstanceOf[ScalaFile]
      case _ => return EmptyRunnable.getInstance()
    }

    Stats.trigger(FeatureKey.optimizeImports)

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

    if (indicator != null)
      indicator.setText2(ScalaEditorBundle.message("imports.analyzing.usage", file.name))

    val size = importHolders.size + importUsers.size //processAllElementsConcurrentlyUnderProgress will be called 2 times
    val counter = new AtomicInteger(0)

    def processAllElementsConcurrentlyUnderProgress[T <: PsiElement](elements: util.List[T])(action: T => Unit) = {
      if (indicator != null) {
        indicator.setIndeterminate(false)
      }
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, indicator, true, false, (element: T) => {
        val count: Int = counter.getAndIncrement
        if (count <= size && indicator != null) indicator.setFraction(count.toDouble / size)

        action(element)

        true
      })
    }

    processAllElementsConcurrentlyUnderProgress(importUsers) { element =>
      collectImportsUsed(element, usedImports, usedImportedNames)
    }

    if (indicator != null) indicator.setText2(ScalaEditorBundle.message("imports.collecting.additional.info", file.name))

    def collectRanges(createInfo: ScImportStmt => Seq[ImportInfo]): Seq[RangeInfo] = {
      val importsInfo = ContainerUtil.newConcurrentSet[RangeInfo]()
      processAllElementsConcurrentlyUnderProgress(importHolders) {
        case holder: ScImportsHolder =>
          importsInfo.addAll(collectImportRanges(holder, createInfo, usedImportedNames.asScala.toSet).asJava)
        case _ =>
      }
      importsInfo.asScala.toSeq.sortBy(_.startOffset)
    }

    val importsSettings = settings(scalaFile)

    def isImportUsed(importUsed: ImportUsed): Boolean = {
      //todo: collect proper information about language features
      importUsed.isAlwaysUsed ||
        usedImports.contains(importUsed) ||
        ScalaImportOptimizerHelper.extensions.exists(_.isImportUsed(importUsed))
    }

    val rangeInfos = collectRanges(createInfo(_, isImportUsed))

    val optimized = rangeInfos.map(range => (range, optimizedImportInfos(range, importsSettings)))

    new Runnable {
      override def run(): Unit = {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document: Document = documentManager.getDocument(scalaFile)
        documentManager.commitDocument(document)

        val ranges: Seq[(RangeInfo, Seq[ImportInfo])] =
          if (document.getText != analyzingDocumentText)  //something was changed...
            sameInfosWithUpdatedRanges()
          else optimized

        for ((range, importInfos) <- ranges.reverseIterator) {
          replaceWithNewImportInfos(range, importInfos, importsSettings, scalaFile)
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

  protected def isImportDelimiter(psi: PsiElement): Boolean = psi.is[PsiWhiteSpace]

  override def supports(file: PsiFile): Boolean = file.is[ScalaFile]

  def replaceWithNewImportInfos(range: RangeInfo, importInfos: Iterable[ImportInfo], settings: OptimizeImportSettings, file: PsiFile): Unit = {
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
    def groupSeparatorsBefore(currentGroupIndex: Int) = {
      if (currentGroupIndex <= prevGroupIndex || prevGroupIndex == -1) ""
      else if (scalastyleGroups.nonEmpty) newLineWithIndent
      else {
        def isBlankLine(i: Int) = importLayout(i) == BLANK_LINE
        val blankLineNumber =
          Range(currentGroupIndex - 1, prevGroupIndex, -1).dropWhile(!isBlankLine(_)).takeWhile(isBlankLine).size
        newLineWithIndent * blankLineNumber
      }
    }

    val text = importInfos.map { info =>
      val index: Int = findGroupIndex(info.prefixQualifier, info.relative, settings)
      val blankLines = groupSeparatorsBefore(index)
      prevGroupIndex = index
      blankLines + textCreator.getImportText(info, settings)
    }.mkString(newLineWithIndent).replaceAll("""\n[ \t]+\n""", "\n\n")

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
      val finalResult = dummyFile.getNode.getChildren(null)
      BufferUpdate.updateIncrementally(buffer, finalResult)(_.getText)
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

    def initRange(psi: PsiElement): Unit = {
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
              case a: PsiElement if isImportDelimiter(a) && !a.is[PsiWhiteSpace] =>
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
      case elem @ (_: ScReference | _: ImplicitArgumentsOwner) => Some(elem)
      case _                                                          => None
    }
  }

  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty
  val _root_prefix = "_root_"

  def findOptimizerFor(file: ScalaFile): Option[ImportOptimizer] = {
    val topLevelFile = file.getViewProvider.getPsi(file.getViewProvider.getBaseLanguage)
    val optimizers = LanguageImportStatements.INSTANCE.forFile(topLevelFile)
    if (optimizers.isEmpty) return None

    if (!topLevelFile.getViewProvider.getBaseLanguage.isKindOf(ScalaLanguage.INSTANCE) && topLevelFile.getViewProvider.getPsi(ScalaLanguage.INSTANCE) == null)
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

  class ImportTextCreator {

    private case class ImportTextData(prefix: String, dotOrNot: String, postfix: String) {
      def fullText: String = s"import $prefix$dotOrNot$postfix"

      //see ScalastyleSettings.compareImports
      def forScalastyleSorting: String = {
        if (postfix.startsWith("{")) prefix + dotOrNot
        else prefix + dotOrNot + postfix
      }
    }

    private def getImportTextData(importInfo: ImportInfo,
                                  isUnicodeArrow: Boolean,
                                  spacesInImports: Boolean,
                                  isScala3OrSource3: Boolean,
                                  nameOrdering: Option[Ordering[String]]): ImportTextData = {
      import importInfo._

      val groupStrings = new ArrayBuffer[String]

      def addGroup(names: Iterable[String]) = {
        if (nameOrdering.isDefined) groupStrings ++= names.toSeq.sorted(nameOrdering.get)
        else groupStrings ++= names
      }

      val arrow =
        if (isScala3OrSource3) "as"
        else if (isUnicodeArrow) ScalaTypedHandler.unicodeCaseArrow
        else "=>"
      val wildcard =
        if (isScala3OrSource3) "*"
        else "_"
      addGroup(singleNames)
      addGroup(renames.map { case (from, to) => s"$from $arrow $to" })
      addGroup(hiddenNames.map(_ + s" $arrow _"))

      if (hasWildcard) groupStrings += wildcard
      val space = if (spacesInImports) " " else ""
      val root = if (rootUsed) s"${_root_prefix}." else ""
      val hasAlias = renames.nonEmpty || hiddenNames.nonEmpty
      val postfix =
        if (groupStrings.length > 1 || (hasAlias && !isScala3OrSource3)) groupStrings.mkString(s"{$space", ", ", s"$space}")
        else groupStrings.head
      val prefix = s"$root${relative.getOrElse(prefixQualifier)}"
      val dotOrNot = if (prefix.endsWith(".") || prefix.isEmpty) "" else "."
      ImportTextData(prefix, dotOrNot, postfix)
    }

    def getImportText(importInfo: ImportInfo,
                      isUnicodeArrow: Boolean,
                      spacesInImports: Boolean,
                      isScala3OrSource3: Boolean,
                      nameOrdering: Option[Ordering[String]]): String =
      getImportTextData(importInfo, isUnicodeArrow, spacesInImports, isScala3OrSource3, nameOrdering).fullText

    def getScalastyleSortableText(importInfo: ImportInfo): String =
      getImportTextData(importInfo, isUnicodeArrow = false, spacesInImports = false, isScala3OrSource3 = false, nameOrdering = None)
        .forScalastyleSorting

    def getImportText(importInfo: ImportInfo, settings: OptimizeImportSettings): String = {
      val ordering =
        if (settings.scalastyleOrder) Some(ScalastyleSettings.nameOrdering)
        else if (settings.sortImports) Some(Ordering.String)
        else None
      getImportText(importInfo, settings.isUnicodeArrow, settings.spacesInImports, settings.isScala3OrSource3, ordering)
    }
  }

  def optimizedImportInfos(rangeInfo: RangeInfo, settings: OptimizeImportSettings): Seq[ImportInfo] = {
    import settings._
    val RangeInfo(firstPsi, _, importInfos, usedImportedNames, isLocalRange) = rangeInfo

    val buffer = ArrayBuffer(importInfos: _*)

    val needReplaceWithFqnImports = addFullQualifiedImports && !(isLocalRange && isLocalImportsCanBeRelative)

    if (needReplaceWithFqnImports) {
      for ((info, i) <- importInfos.zipWithIndex) {
        buffer(i) = basePackage match {
          case Some(base) if (info.prefixQualifier + ".").startsWith(base + ".") =>
            info.copy(relative = Some(info.prefixQualifier.substring(base.length + 1)))
          case _ =>
            info.withoutRelative
        }
      }
    }

    if (sortImports) notifyAboutNewImportLayout(buffer, firstPsi.getFile.getProject) {
      sortImportInfos(buffer, settings)
    }

    val result =
      if (collectImports) mergeImportInfos(buffer)
      else buffer.flatMap(_.split)

    updateToWildcardImports(result, firstPsi, usedImportedNames, settings)
    updateRootPrefix(result)

    result.toSeq
  }

  // TODO Remove the import layout notification in 2021.1+
  private def notifyAboutNewImportLayout(buffer: mutable.Buffer[ImportInfo], project: Project)(sort: => Unit): Unit = {
    val hadJavaGroupAtTheTop = buffer.length > 1 && buffer.head.prefixQualifier.startsWith("java") && !buffer.last.prefixQualifier.startsWith("java")

    sort

    val settings = ScalaApplicationSettings.getInstance
    val style = ScalaCodeStyleSettings.getInstance(project)

    if (settings.SUGGEST_LEGACY_IMPORT_LAYOUT && style.getImportLayout.sameElements(ScalaCodeStyleSettings.DEFAULT_IMPORT_LAYOUT)) {

      if (hadJavaGroupAtTheTop && !buffer.head.prefixQualifier.startsWith("java.")) {
        val notification = {
          @nowarn
          val group = new NotificationGroup(ScalaEditorBundle.message("import.layout.group"), NotificationDisplayType.STICKY_BALLOON, true)
          group.createNotification(ScalaEditorBundle.message("import.layout.updated.title"), ScalaEditorBundle.message("import.layout.updated.description"), NotificationType.INFORMATION)
        }

        def action(@Nls name: String)(f: () => Unit) = new AnAction(name) {
          override def actionPerformed(e: AnActionEvent): Unit = f()
        }

        def hide(): Unit = Option(notification.getBalloon).foreach(_.hide())

        notification.setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)

        notification
          .addAction(action(ScalaEditorBundle.message("import.optimizer.got.it"))(() => hide()))
          .addAction(action(ScalaEditorBundle.message("import.optimizer.switch.to.legacy.scheme")){ () =>
            style.setImportLayout(ScalaCodeStyleSettings.LEGACY_IMPORT_LAYOUT)
            CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
            hide()
          })

        notification.notify(project)

        settings.SUGGEST_LEGACY_IMPORT_LAYOUT = false
      }
    }
  }

  def updateRootPrefix(importInfos: mutable.Buffer[ImportInfo]): Unit = {
    val importedNames = new mutable.HashSet[String]()

    for (i <- importInfos.indices) {
      val info = importInfos(i)
      if (info.canAddRoot && importedNames.contains(getFirstId(info.prefixQualifier)))
        importInfos.update(i, info.withRootPrefix)

      if (!ScalaImportOptimizerHelper.extensions.exists(_.cannotShadowName(info)))
        importedNames ++= info.allNames
    }
  }

  def sortImportInfos(buffer: mutable.Buffer[ImportInfo], settings: OptimizeImportSettings): Unit = {
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

  def updateToWildcardImports(infos: mutable.Buffer[ImportInfo],
                              startPsi: PsiAnchor,
                              usedImportedNames: Set[String],
                              settings: OptimizeImportSettings): Unit = {

    val rangeStartPsi = startPsi.retrieve()
    def resolvesAtRangeStart(name: String): Boolean = {
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

    def updateWithWildcardNames(buffer: mutable.Buffer[ImportInfo]): Unit = {
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

  def insertImportInfos(infosToAdd: collection.Seq[ImportInfo], infos: collection.Seq[ImportInfo], rangeStart: PsiAnchor, settings: OptimizeImportSettings): collection.Seq[ImportInfo] = {
    import settings._

    def addLastAndMoveUpwards(newInfo: ImportInfo, buffer: mutable.Buffer[ImportInfo]): Unit = {
      var i = buffer.size
      buffer.insert(i, newInfo)
      while(i > 0 && greater(buffer(i - 1), buffer(i), settings) && swapWithNext(buffer, i - 1)) {
        i -= 1
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

    def insertSimpleInfo(info: ImportInfo, buffer: mutable.Buffer[ImportInfo]): Unit = {
      val samePrefixInfos = buffer.filter(_.prefixQualifier == info.prefixQualifier)
      if (collectImports) {
        val merged = ImportInfo.merge(samePrefixInfos :+ info)
        replace(samePrefixInfos, merged.toSeq, buffer)
      }
      else addLastAndMoveUpwards(info, buffer)
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
      if (collectImports) {
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
      updateToWildcardImports(buffer, rangeStart, usedNames, settings)
    }
    else {
      actuallyInserted.foreach(insertSimpleInfo(_, buffer))
    }

    updateRootPrefix(buffer)
    buffer.toVector
  }

  private def swapWithNext(buffer: mutable.Buffer[ImportInfo], i: Int): Boolean = {
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

  private def mergeImportInfos(buffer: mutable.Buffer[ImportInfo]): mutable.Buffer[ImportInfo] = {
    def canBeMergedAt(i: Int): Boolean =
      i > -1 && i < buffer.length && {
        val el = buffer(i)
        !ScalaImportOptimizerHelper.extensions.exists(_.preventMerging(el))
      }

    def samePrefixAfter(i: Int): Int = {
      if (!canBeMergedAt(i)) return -1

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

  def findGroupIndex(prefix: String, relativeTo: Option[String], settings: OptimizeImportSettings): Int = {
    settings.scalastyleGroups match {
      case Some(patterns) =>
        patterns.indexWhere(_.matcher(prefix).matches())
      case _ =>
        def matches(packagePattern: String) =
          prefix == packagePattern || prefix.startsWith(packagePattern + ".") ||
            (packagePattern == BASE_PACKAGE_IMPORTS && relativeTo.exists(id => settings.basePackage.contains(prefix.dropRight(id.length + 1))))

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

  def greater(lInfo: ImportInfo, rInfo: ImportInfo, settings: OptimizeImportSettings): Boolean = {
    val textCreator = new ImportTextCreator

    val lIndex = findGroupIndex(lInfo.prefixQualifier, lInfo.relative, settings)
    val rIndex = findGroupIndex(rInfo.prefixQualifier, rInfo.relative, settings)

    if (lIndex > rIndex) true
    else if (rIndex > lIndex) false
    else if (settings.scalastyleOrder) {
      val lText = textCreator.getScalastyleSortableText(lInfo)
      val rText = textCreator.getScalastyleSortableText(rInfo)
      ScalastyleSettings.compareImports(lText, rText) > 0
    }
    else {
      val lText = textCreator.getImportText(lInfo, settings)
      val rText = textCreator.getImportText(rInfo, settings)
      lText > rText
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
        names.add(UsedName(srr.name, fromElem.getTextRange.getStartOffset))
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
        if impQual.qualifier.isEmpty && PsiTreeUtil.getParentOfType(impQual, classOf[ScImportStmt]) != null =>
        //don't add as ImportUsed to be able to optimize it away if it is used only in unused imports
        val hasImportUsed = impQual.multiResolveScala(false).exists(_.importsUsed.nonEmpty)
        if (hasImportUsed) {
          names.add(UsedName(impQual.refName, impQual.getTextRange.getStartOffset))
        }
      case ref: ScReference if PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) == null =>
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

  //quite heavy computation, is really needed only for dealing with wildcard imports
  def collectUsedImportedNames(holder: ScImportsHolder): Set[String] = {
    val imports = new util.HashSet[ImportUsed]()
    val names = new util.HashSet[UsedName]()

    holder.depthFirst().foreach {
      case ImportUser(elem) => collectImportsUsed(elem, imports, names)
      case _ =>
    }
    names.asScala.toSet.map((x: UsedName) => x.name)
  }


  def createInfo(imp: ScImportStmt, isImportUsed: ImportUsed => Boolean = _ => true): Seq[ImportInfo] =
    imp.importExprs.flatMap(ImportInfo(_, isImportUsed))


}
