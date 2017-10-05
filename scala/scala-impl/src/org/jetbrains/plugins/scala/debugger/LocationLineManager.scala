package org.jetbrains.plugins.scala.debugger

import java.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.{DebugProcess, SyntheticTypeComponentProvider}
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.sun.jdi.{AbsentInformationException, Location, Method, ReferenceType}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.Opcodes
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement, ScMatchStmt, ScTryStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * @author Nikolay.Tropin
  */
trait LocationLineManager {
  self: ScalaPositionManager =>

  import self.caches._

  private val syntheticProvider = SyntheticTypeComponentProvider.EP_NAME.findExtension(classOf[ScalaSyntheticProvider])

  def clearLocationLineCaches(): Unit = {
    customizedLocationsCache.clear()
    lineToCustomizedLocationCache.clear()
    seenRefTypes.clear()
  }

  def exactLineNumber(location: Location): Int = {
    checkAndUpdateCaches(location.declaringType())
    customizedLocationsCache.getOrElse(location, ScalaPositionManager.checkedLineNumber(location))
  }

  def shouldSkip(location: Location): Boolean = {
    if (!DebuggerUtil.isScala(location.declaringType(), default = false)) return false

    val synth = DebuggerSettings.getInstance().SKIP_SYNTHETIC_METHODS && syntheticProvider.isSynthetic(location.method())
    synth || exactLineNumber(location) < 0
  }

  def locationsOfLine(refType: ReferenceType, line: Int): Seq[Location] = {
    val jvmLocations: util.List[Location] =
      try {
        if (debugProcess.getVirtualMachineProxy.versionHigher("1.4"))
          refType.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line + 1)
        else refType.locationsOfLine(line + 1)
      } catch {
        case _: AbsentInformationException => return Seq.empty
      }

    checkAndUpdateCaches(refType)

    val nonCustomized = jvmLocations.asScala.filterNot(customizedLocationsCache.contains)
    val customized = customizedLocations(refType, line)
    (nonCustomized ++ customized).filter(!shouldSkip(_))
  }

  private def customizedLocations(refType: ReferenceType, line: Int): Seq[Location] = {
    lineToCustomizedLocationCache.getOrElse((refType, line), Seq.empty)
  }

  private def checkAndUpdateCaches(refType: ReferenceType) = {
    if (!seenRefTypes.contains(refType)) inReadAction(computeCustomizedLocationsFor(refType))
  }

  private def cacheCustomLine(location: Location, customLine: Int): Unit = {
    customizedLocationsCache.put(location, customLine)

    val key = (location.declaringType(), customLine)
    val old = lineToCustomizedLocationCache.getOrElse(key, Seq.empty)
    lineToCustomizedLocationCache.update(key, (old :+ location).sortBy(_.codeIndex()))
  }

  private def computeCustomizedLocationsFor(refType: ReferenceType): Unit = {
    seenRefTypes += refType

    val generatingElem = findElementByReferenceType(refType).orNull
    if (generatingElem == null) return
    val containingFile = generatingElem.getContainingFile
    if (containingFile == null) return
    val document = PsiDocumentManager.getInstance(debugProcess.getProject).getDocument(containingFile)
    if (document == null) return

    def elementStartLine(e: PsiElement) = document.getLineNumber(e.getTextOffset)
    def locationsOfLine(m: Method, line: Int) = Try(m.locationsOfLine(line + 1).asScala).getOrElse(Seq.empty)

    //scalac sometimes generates very strange line numbers for <init> method
    def customizeLineForConstructors(): Unit = {
      //2.12 generates line number for return of constructor, it has no use in debugger
      def isReturnInstr(location: Location): Boolean = {
        try {
          val bytecodes = location.method().bytecodes()
          val index = location.codeIndex()
          bytecodes(index.toInt) == Opcodes.voidReturn
        } catch {
          case _: Throwable => false
        }
      }

      def shouldPointAtStartLine(location: Location): Boolean = {
        if (location.codeIndex() != 0) return false

        val lineNumber = ScalaPositionManager.checkedLineNumber(location)
        if (lineNumber < 0) return true

        val linePosition = SourcePosition.createFromLine(containingFile, lineNumber)
        val elem = nonWhitespaceElement(linePosition)
        val parent = PsiTreeUtil.getParentOfType(elem, classOf[ScBlockStatement], classOf[ScEarlyDefinitions])
        parent == null || !PsiTreeUtil.isAncestor(generatingElem, parent, false)
      }

      val methods = refType.methodsByName("<init>").asScala.filter(_.declaringType() == refType)
      for {
        location <- methods.flatMap(_.allLineLocations().asScala)
      } {
        if (shouldPointAtStartLine(location)) {
          val significantElem = DebuggerUtil.getSignificantElement(generatingElem)
          val lineNumber = elementStartLine(significantElem)
          if (lineNumber != ScalaPositionManager.checkedLineNumber(location))
            cacheCustomLine(location, lineNumber)
        }
        else if (isReturnInstr(location)) {
          cacheCustomLine(location, -1)
        }
      }
    }

    def customizeCaseClauses(): Unit = {

      def skipTypeCheckOptimization(method: Method, caseLineLocations: Seq[Location]): Unit = {
        val bytecodes =
          try method.bytecodes()
          catch {case _: Throwable => return }

        def cacheCorrespondingIloadLocations(iconst_0Loc: Location): Unit = {
          val codeIndex = iconst_0Loc.codeIndex().toInt
          val iloadCode = BytecodeUtil.readIstore(codeIndex + 1, bytecodes) match {
            case Seq() => Nil
            case istoreCode => BytecodeUtil.iloadCode(istoreCode)
          }
          if (iloadCode.isEmpty) return

          method.allLineLocations().asScala.foreach {
            case loc if BytecodeUtil.readIload(loc.codeIndex().toInt, bytecodes) == iloadCode =>
              cacheCustomLine(loc, -1)
            case _ =>
          }
        }

        val iconst_0Locations = caseLineLocations.filter(l => BytecodeUtil.isIconst_0(l.codeIndex().toInt, bytecodes))

        iconst_0Locations.foreach { l =>
          cacheCustomLine(l, -1)
          cacheCorrespondingIloadLocations(l)
        }
      }

      def skipReturnValueAssignment(method: Method, caseLinesLocations: Seq[Seq[Location]]): Unit = {
        val bytecodes =
          try method.bytecodes()
          catch {case _: Throwable => return }

        def storeCode(location: Location): Option[Seq[Byte]] = {
          val codeIndex = location.codeIndex().toInt
          val code = BytecodeUtil.readStoreCode(codeIndex, bytecodes)
          if (code.nonEmpty) Some(code) else None
        }

        val notCustomizedYet = caseLinesLocations.map(_.filter(!customizedLocationsCache.contains(_)))
        val repeating = notCustomizedYet.filter(_.size > 1)
        val lastLocations = repeating.map(_.last)
        val withStoreCode = for (loc <- lastLocations; code <- storeCode(loc)) yield (loc, code)
        val (locationsToSkip, codes) = withStoreCode.unzip
        if (codes.distinct.size != 1) return

        locationsToSkip.foreach(cacheCustomLine(_, -1))

        val loadCode = BytecodeUtil.loadCode(codes.head)
        if (loadCode.isEmpty) return

        val loadLocations = method.allLineLocations().asScala.filter { l =>
          BytecodeUtil.readLoadCode(l.codeIndex().toInt, bytecodes) == loadCode
        }
        loadLocations.foreach(cacheCustomLine(_, -1))
      }

      def skipBaseLineExtraLocations(method: Method, locations: Seq[Location]): Unit = {
        val filtered = locations.filter(!customizedLocationsCache.contains(_))
        if (filtered.size <= 1) return

        val bytecodes =
          try method.bytecodes()
          catch {
            case _: Throwable => return
          }

        val tail: Seq[Location] = filtered.tail

        val loadExpressionValueLocations = tail.filter { l =>
          BytecodeUtil.readLoadCode(l.codeIndex().toInt, bytecodes).nonEmpty
        }

        val returnLocations = tail.filter { l =>
          BytecodeUtil.returnCodes.contains(bytecodes(l.codeIndex().toInt))
        }

        (loadExpressionValueLocations ++ returnLocations).foreach(cacheCustomLine(_, -1))
      }

      def skipGotoLocations(method: Method, possibleLocations: Seq[Location]): Unit = {
        val bytecodes =
          try method.bytecodes()
          catch {case _: Throwable => return }

        val gotos = possibleLocations.filter(loc => BytecodeUtil.isGoto(loc.codeIndex().toInt, bytecodes))
        gotos.foreach(cacheCustomLine(_, -1))
      }

      def customizeFor(caseClauses: ScCaseClauses): Unit = {
        def tooSmall(m: Method) = {
          try m.allLineLocations().size() <= 3
          catch {
            case _: AbsentInformationException => true
          }
        }

        val baseLine = caseClauses.getParent match {
          case ms: ScMatchStmt => ms.expr.map(elementStartLine)
          case (_: ScBlock) childOf (_: ScTryStmt) => return //todo: handle try statements
          case (b: ScBlock) => Some(elementStartLine(b))
          case _ => None
        }
        val caseLines = caseClauses.caseClauses.map(elementStartLine)
        val methods = refType.methods().asScala.filterNot(tooSmall)

        for {
          m <- methods
          caseLinesLocations = caseLines.map(locationsOfLine(m, _))
          if caseLinesLocations.exists(_.nonEmpty)
        } {
          val flattenCaseLines = caseLinesLocations.flatten
          skipTypeCheckOptimization(m, flattenCaseLines)
          skipGotoLocations(m, flattenCaseLines)
          skipReturnValueAssignment(m, caseLinesLocations)
        }

        for {
          m <- methods
          line <- baseLine
          locations = locationsOfLine(m, line)
          if locations.size > 1
        } {
          skipBaseLineExtraLocations(m, locations)
          skipGotoLocations(m, locations)
        }
      }

      val allCaseClauses = generatingElem.breadthFirst().collect {
        case cc: ScCaseClauses => cc
      }
      allCaseClauses.foreach(customizeFor)
    }

    customizeLineForConstructors()
    customizeCaseClauses()
  }
}
