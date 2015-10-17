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
import com.intellij.util.containers.{ConcurrentHashMap, ConcurrentHashSet}
import org.jetbrains.plugins.scala.editor.typedHandler.ScalaTypedHandler
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScForStatement, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Set, mutable}

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

    val textCreator = getImportTextCreator

    val usedImports = new ConcurrentHashSet[ImportUsed]
    val list: util.ArrayList[PsiElement] =  new util.ArrayList[PsiElement]()
    val notProcessed = new ArrayBuffer[PsiElement]()
    def addChildren(element: PsiElement): Unit = {
      list.add(element)
      element.getChildren.foreach(addChildren)
    }
    addChildren(scalaFile)

    val size = list.size * 2
    val progressManager: ProgressManager = ProgressManager.getInstance()
    val indicator: ProgressIndicator =
      if (progressIndicator != null) progressIndicator
      else if (progressManager.hasProgressIndicator) progressManager.getProgressIndicator
      else null
    if (indicator != null) indicator.setText2(file.getName + ": analyzing imports usage")
    val i = new AtomicInteger(0)
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, indicator, true, true, new Processor[PsiElement] {
      override def process(element: PsiElement): Boolean = {
        val count: Int = i.getAndIncrement
        if (count <= size && indicator != null) indicator.setFraction(count.toDouble / size)
        element match {
          case ref: ScReferenceElement =>
            if (PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) == null) {
              ref.multiResolve(false) foreach {
                case scalaResult: ScalaResolveResult => scalaResult.importsUsed.foreach(usedImports.add)
                case _ =>
              }
            }
          case simple: ScSimpleTypeElement =>
            simple.findImplicitParameters match {
              case Some(parameters) =>
                parameters.foreach {
                  case r: ScalaResolveResult => r.importsUsed.foreach(usedImports.add)
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        val imports = element match {
          case expression: ScExpression => checkTypeForExpression(expression)
          case _ => ScalaImportOptimizer.NO_IMPORT_USED
        }
        imports.foreach(usedImports.add)
        true
      }
    })

    if (indicator != null) indicator.setText2(file.getName + ": collecting additional info")

    def collectRanges(rangeStarted: ScImportStmt => Set[String],
                      createInfo: ScImportStmt => Seq[ImportInfo]): ConcurrentHashMap[TextRange, (Set[String], Seq[ImportInfo], Boolean)] = {
      val importsInfo = new ConcurrentHashMap[TextRange, (Set[String], Seq[ImportInfo], Boolean)]
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(list, indicator, true, true, new Processor[PsiElement] {
        override def process(element: PsiElement): Boolean = {
          val count: Int = i.getAndIncrement
          if (count <= size && indicator != null) indicator.setFraction(count.toDouble / size)
          element match {
            case imp: ScImportsHolder =>
              var rangeStart = -1
              var rangeEnd = -1
              var rangeNames: Set[String] = Set.empty
              val isLocalRange = imp match {
                case _: ScalaFile | _: ScPackaging => false
                case _ => true
              }
              val infos = new ArrayBuffer[ImportInfo]

              def addRange(): Unit = {
                if (rangeStart != -1) {
                  importsInfo.put(new TextRange(rangeStart, rangeEnd), (rangeNames, Seq(infos: _*), isLocalRange))
                  rangeStart = -1
                  rangeEnd = -1
                  rangeNames = Set.empty
                  infos.clear()
                }
              }

              def initRange(psi: PsiElement) {
                rangeStart = psi.getTextRange.getStartOffset
                rangeEnd = psi.getTextRange.getEndOffset
              }

              for (child <- imp.getNode.getChildren(null)) {
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
                      rangeNames = rangeStarted(imp)
                    } else {
                      rangeEnd = imp.getTextRange.getEndOffset
                    }
                    infos ++= createInfo(imp)
                  case _ => addRange()
                }
              }
              addRange()
            case _ =>
          }
          true
        }
      })
      importsInfo
    }

    def rangeStarted(imp: ScImportStmt): Set[String] = {
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
        case ScalaResolveResult(f: PsiField, _) if f.hasModifierProperty("final") =>
          addName(name(f.getName))
        case _ =>
      }
      rangeNamesSet.toSet
    }

    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    def isImportUsed(importUsed: ImportUsed): Boolean = {
      //todo: collect proper information about language features
      importUsed match {
        case ImportSelectorUsed(sel) if sel.isAliasedImport => true
        case _ => usedImports.contains(importUsed) || isLanguageFeatureImport(importUsed) || importUsed.qualName.exists(settings.isAlwaysUsedImport)
      }
    }

    def createInfo(imp: ScImportStmt): Seq[ImportInfo] = {
      imp.importExprs.flatMap(expr =>
        getImportInfo(expr, isImportUsed) match {
          case Some(importInfo) => Seq(importInfo)
          case _ => Seq.empty
        }
      )
    }

    val importsInfo = collectRanges(rangeStarted, createInfo)

    val addFullQualifiedImports = settings.isAddFullQualifiedImports
    val isLocalImportsCanBeRelative = settings.isDoNotChangeLocalImportsOnOptimize
    val sortImports = settings.isSortImports
    val collectImports = settings.isCollectImports
    val groups = settings.getImportLayout
    val isUnicodeArrow = settings.REPLACE_CASE_ARROW_WITH_UNICODE_CHAR
    val spacesInImports = settings.SPACES_IN_IMPORTS

    val sortedImportsInfo: mutable.Map[TextRange, Seq[ImportInfo]] =
      for ((range, (names, _importInfos, isLocalRange)) <- importsInfo) yield {
        var importInfos = _importInfos

        if (addFullQualifiedImports && !(isLocalRange && isLocalImportsCanBeRelative)) {
          val holderNames = new mutable.HashSet[String]()
          holderNames ++= names
          importInfos = _importInfos.map { info =>
            val res = info.withoutRelative(holderNames)
            holderNames ++= info.allNames
            res
          }
        }

        val buffer = new ArrayBuffer[ImportInfo]()
        buffer ++= importInfos

        def swap(i: Int): Boolean = {
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



        if (sortImports) {
          @tailrec
          def iteration(): Unit = {
            var i = 0
            var changed = false
            while (i + 1 < buffer.length) {
              val l: String = buffer(i).prefixQualifier
              val r: String = buffer(i + 1).prefixQualifier
              val lText = getImportTextCreator.getImportText(buffer(i), isUnicodeArrow, spacesInImports, sortImports)
              val rText = getImportTextCreator.getImportText(buffer(i + 1), isUnicodeArrow, spacesInImports, sortImports)
              if (greater(l, r, lText, rText, project) && swap(i)) changed = true
              i = i + 1
            }
            if (changed) iteration()
          }

          iteration()
        }

        if (collectImports) {
          def merge(first: ImportInfo, second: ImportInfo): ImportInfo = {
            val relative = if (first.relative.nonEmpty) first.relative else second.relative
            val rootUsed = if (first.relative.nonEmpty) first.rootUsed else second.rootUsed
            new ImportInfo(first.importUsed ++ second.importUsed, first.prefixQualifier, relative,
              first.allNames ++ second.allNames, first.singleNames ++ second.singleNames,
              first.renames ++ second.renames, first.hidedNames ++ second.hidedNames,
              first.hasWildcard || second.hasWildcard, rootUsed)
          }
          var i = 0
          while (i < buffer.length - 1) {
            def containsPrefix: Int = {
              var j = i + 1
              while (j < buffer.length) {
                if (buffer(j).prefixQualifier == buffer(i).prefixQualifier) return j
                j += 1
              }
              -1
            }
            val prefixIndex: Int = containsPrefix
            if (prefixIndex != -1) {
              if (prefixIndex == i + 1) {
                val merged = merge(buffer(i), buffer(i + 1))
                buffer(i) = merged
                buffer.remove(i + 1)
              } else {
                if (swap(i)) {
                  var j = i + 1
                  var break = false
                  while (!break && j != prefixIndex - 1) {
                    if (!swap(j)) break = true
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
        } else {
          val result = buffer.flatMap { info =>
            val innerBuffer = new ArrayBuffer[ImportInfo]
            innerBuffer ++= info.singleNames.toSeq.sorted.map { name =>
              info.copy(singleNames = Set(name), renames = Map.empty, hidedNames = Set.empty, hasWildcard = false)
            }
            innerBuffer ++= info.renames.map { rename =>
              info.copy(renames = Map(rename), singleNames = Set.empty, hidedNames = Set.empty, hasWildcard = false)
            }
            innerBuffer ++= info.hidedNames.map { hided =>
              info.copy(hidedNames = Set(hided), singleNames = Set.empty, renames = Map.empty, hasWildcard = false)
            }
            if (info.hasWildcard) {
              innerBuffer += info.copy(singleNames = Set.empty, renames = Map.empty, hidedNames = Set.empty)
            }
            innerBuffer.toSeq
          }
          buffer.clear()
          buffer ++= result
        }
        importInfos = buffer.toSeq
        (range, importInfos)
      }

    new Runnable {
      def run() {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document: Document = documentManager.getDocument(scalaFile)
        documentManager.commitDocument(document)
        val ranges: Seq[(TextRange, Seq[ImportInfo])] = if (document.getText != analyzingDocumentText) {
          //something was changed...
          sortedImportsInfo.toSeq.sortBy(_._1.getStartOffset).zip {
            collectRanges(_ => Set.empty, _ => Seq.empty).toSeq.sortBy(_._1.getStartOffset)
          }.map {
            case ((_, seq), (range, _)) => (range, seq)
          }
        } else sortedImportsInfo.toSeq.sortBy(_._1.getStartOffset)

        for ((range, importInfos) <- ranges.reverseIterator) {
          val documentText = document.getText
          def splitterCalc(index: Int, res: String = ""): String = {
            if (index < 0) res
            else {
              val c = documentText.charAt(index)
              if (c == ' ' || c == '\t') splitterCalc(index - 1, "" + c + res)
              else res
            }
          }
          val splitter: String = "\n" + splitterCalc(range.getStartOffset - 1)
          var currentGroupIndex = -1
          val text = importInfos.map { info =>
            val index: Int = findGroupIndex(info.prefixQualifier, project)
            if (index <= currentGroupIndex) textCreator.getImportText(info, isUnicodeArrow, spacesInImports, sortImports)
            else {
              var blankLines = ""
              def iteration() {
                currentGroupIndex += 1
                while (groups(currentGroupIndex) == ScalaCodeStyleSettings.BLANK_LINE) {
                  blankLines += splitter
                  currentGroupIndex += 1
                }
              }
              while (currentGroupIndex != -1 && blankLines.isEmpty && currentGroupIndex < index) iteration()
              currentGroupIndex = index
              blankLines + textCreator.getImportText(info, isUnicodeArrow, spacesInImports, sortImports)
            }
          }.mkString(splitter).replaceAll("""\n[ \t]+\n""", "\n\n")
          val newRange: TextRange = if (text.isEmpty) {
            var start = range.getStartOffset
            while (start > 0 && documentText(start - 1).isWhitespace) start = start - 1
            val end = range.getEndOffset
            new TextRange(start, end)
          } else range
          document.replaceString(newRange.getStartOffset, newRange.getEndOffset, text)
        }
        documentManager.commitDocument(document)
      }
    }
  }

  protected def getImportTextCreator: ImportTextCreator = new ImportTextCreator

  protected def isImportDelimiter(psi: PsiElement) = psi.isInstanceOf[PsiWhiteSpace]

  private def checkTypeForExpression(expr: ScExpression): Set[ImportUsed] = {
    var res: scala.collection.mutable.HashSet[ImportUsed] =
      scala.collection.mutable.HashSet(expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType()).
        importsUsed.toSeq : _*)
    expr match {
      case call: ScMethodCall =>
        res ++= call.getImportsUsed
      case _ =>
    }
    expr.findImplicitParameters match {
      case Some(seq) =>
        for (rr <- seq if rr != null) {
          res ++= rr.importsUsed
        }
      case _ =>
    }
    expr match {
      case f: ScForStatement => res ++= ScalaPsiUtil.getExprImports(f)
      case _ =>
    }
    res
  }


  def supports(file: PsiFile): Boolean = file.isInstanceOf[ScalaFile] && file.getViewProvider.getAllFiles.size() < 3
}

object ScalaImportOptimizer {
  val NO_IMPORT_USED: Set[ImportUsed] = Set.empty

  /**
   * We can't just select ScalaImportOptimizer because of Play2 templates
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
      groupStrings ++= hidedNames.map(_ + s" $arrow _")
      val sortedGroupStrings = if (sortLexicografically) groupStrings.sorted else groupStrings
      if (hasWildcard) sortedGroupStrings += "_"
      val space = if (spacesInImports) " " else ""
      val root = if (rootUsed) "_root_." else ""
      val postfix =
        if (sortedGroupStrings.length > 1 || renames.nonEmpty || hidedNames.nonEmpty) sortedGroupStrings.mkString(s"{$space", ", ", s"$space}")
        else sortedGroupStrings(0)
      s"import $root${relative.getOrElse(prefixQualifier)}.$postfix"
    }
  }

  case class ImportInfo(importUsed: Set[ImportUsed], prefixQualifier: String,
                   relative: Option[String], allNames: Set[String],
                   singleNames: Set[String], renames: Map[String, String],
                   hidedNames: Set[String], hasWildcard: Boolean, rootUsed: Boolean) {
    def withoutRelative(holderNames: Set[String]): ImportInfo =
      if (relative.isDefined || rootUsed) copy(relative = None) else this
  }

  def name(s: String): String = {
    if (ScalaNamesUtil.isKeyword(s)) s"`$s`"
    else s
  }

  def getImportInfo(imp: ScImportExpr, isImportUsed: ImportUsed => Boolean): Option[ImportInfo] = {
    val res = new ArrayBuffer[ImportUsed]
    val allNames = new mutable.HashSet[String]()
    val singleNames = new mutable.HashSet[String]()
    val renames = new mutable.HashMap[String, String]()
    val hidedNames = new mutable.HashSet[String]()
    var hasWildcard = false

    def addAllNames(ref: ScStableCodeReferenceElement, nameToAdd: String): Unit = {
      if (ref.multiResolve(false).exists {
        case ScalaResolveResult(p: PsiPackage, _) => true
        case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => true
        case ScalaResolveResult(_: ScTypeDefinition, _) => false
        case ScalaResolveResult(_: PsiClass, _) => true
        case ScalaResolveResult(f: PsiField, _) => f.hasModifierProperty("final")
        case _ => false
      }) allNames += nameToAdd
    }

    if (!imp.singleWildcard && imp.selectorSet == None) {
      val importUsed: ImportExprUsed = ImportExprUsed(imp)
      if (isImportUsed(importUsed)) {
        res += importUsed
        imp.reference match {
          case Some(ref) =>
            singleNames += ref.refName
            addAllNames(ref, ref.refName)
          case None => //something is not valid
        }
      }
    } else if (imp.singleWildcard) {
      val importUsed =
        if (imp.selectorSet == None) ImportExprUsed(imp)
        else ImportWildcardSelectorUsed(imp)
      if (isImportUsed(importUsed)) {
        res += importUsed
        hasWildcard = true
        val refText = imp.qualifier.getText + ".someIdentifier"
        val reference = ScalaPsiElementFactory.createReferenceFromText(refText, imp.qualifier.getContext, imp.qualifier)
        reference.getResolveResultVariants.foreach {
          case ScalaResolveResult(p: PsiPackage, _) => allNames += name(p.getName)
          case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => allNames += td.name
          case ScalaResolveResult(_: ScTypeDefinition, _) =>
          case ScalaResolveResult(c: PsiClass, _) => allNames += name(c.getName)
          case ScalaResolveResult(f: PsiField, _) if f.hasModifierProperty("final") => allNames += name(f.getName)
          case _ =>
        }
      }
    }
    for (selector <- imp.selectors) {
      val importUsed: ImportSelectorUsed = ImportSelectorUsed(selector)
      if (isImportUsed(importUsed)) {
        res += importUsed
        val refName: String = selector.reference.refName
        if (selector.isAliasedImport) {
          val importedName: String = selector.importedName
          if (importedName == "_") {
            hidedNames += refName
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
    allNames --= hidedNames

    if (res.isEmpty) return None //all imports are empty

    val qualifier = imp.qualifier
    if (qualifier == null) return None //ignore invalid imports

    @tailrec
    def deepestQualifier(ref: ScStableCodeReferenceElement): ScStableCodeReferenceElement = {
      ref.qualifier match {
        case Some(q) => deepestQualifier(q)
        case None => ref
      }
    }
    val deepRef = deepestQualifier(qualifier)

    def packageQualifier(p: PsiPackage): String = {
      p.getParentPackage match {
        case null => name(p.getName)
        case parent if parent.getName == null => name(p.getName)
        case parent => packageQualifier(parent) + "." + name(p.getName)
      }
    }

    @tailrec
    def collectQualifierString(ref: ScStableCodeReferenceElement, withDeepest: Boolean,
                               res: String = ""): String = {
      ref.qualifier match {
        case Some(q) => collectQualifierString(q, withDeepest, ref.refName + withDot(res))
        case None if withDeepest && ref.refName != "_root_" => ref.refName + withDot(res)
        case None => res
      }
    }

    def withDot(s: String): String = {
      if (s.isEmpty) "" else "." + s
    }

    var isRelative = false
    var qualifierString = collectQualifierString(qualifier, withDeepest = false)

    def updateQualifierString(prefix: String): Unit = {
      qualifierString = prefix + withDot(qualifierString)
    }

    @tailrec
    def isRelativeObject(o: ScObject, res: Boolean = false): Boolean = {
      o.getContext match {
        case _: ScTemplateBody =>
          o.containingClass match {
            case containingObject: ScObject => isRelativeObject(containingObject, res = true)
            case _ => false //inner of some class/trait
          }
        case _: ScPackaging => true
        case _ => res //something in default package or in local object
      }
    }

    var rootUsed = false

    if (deepRef.getText != "_root_") {
      deepRef.bind() match {
        case Some(ScalaResolveResult(p: PsiPackage, _)) =>
          if (p.getParentPackage != null && p.getParentPackage.getName != null) {
            isRelative = true
            updateQualifierString(packageQualifier(p))
          } else updateQualifierString(deepRef.refName)
        case Some(ScalaResolveResult(o: ScObject, _)) =>
          if (isRelativeObject(o)) {
            isRelative = true
            updateQualifierString(o.qualifiedName)
          } else updateQualifierString(deepRef.refName)
        case Some(ScalaResolveResult(p: PsiClass, _)) =>
          val parts = p.getQualifiedName.split('.')
          if (parts.length > 1) {
            isRelative = true
            updateQualifierString(parts.map(name).mkString("."))
          } else updateQualifierString(deepRef.refName)
        case Some(ScalaResolveResult(td: ScTypedDefinition, _)) =>
          ScalaPsiUtil.nameContext(td) match {
            case m: ScMember =>
              m.containingClass match {
                case o: ScObject if isRelativeObject(o, res = true) =>
                  isRelative = true
                  updateQualifierString(deepRef.refName)
                  updateQualifierString(o.qualifiedName)
                case _ => updateQualifierString(deepRef.refName)
              }
            case _ => updateQualifierString(deepRef.refName)
          }
        case Some(ScalaResolveResult(f: PsiField, _)) =>
          isRelative = true
          updateQualifierString(deepRef.refName)
          f.getContainingClass match {
            case null => return None //somehting is wrong
            case clazz => updateQualifierString(clazz.getQualifiedName.split('.').map(name).mkString("."))
          }
        case _ => return None //do not process invalid import
      }
    } else rootUsed = true

    val relativeQualifier =
      if (isRelative) Some(collectQualifierString(qualifier, withDeepest = true))
      else None

    Some(new ImportInfo(res.toSet, qualifierString, relativeQualifier, allNames.toSet,
      singleNames.toSet, renames.toMap, hidedNames.toSet, hasWildcard, rootUsed))
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

  def findGroupIndex(info: String, project: Project): Int = {
    val groups = ScalaCodeStyleSettings.getInstance(project).getImportLayout
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

  def greater(l: String, r: String, lText: String, rText: String, project: Project): Boolean = {
    val lIndex = findGroupIndex(l, project)
    val rIndex = findGroupIndex(r, project)
    if (lIndex > rIndex) true
    else if (rIndex > lIndex) false
    else lText > rText
  }
}