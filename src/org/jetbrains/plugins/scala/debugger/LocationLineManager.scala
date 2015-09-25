package org.jetbrains.plugins.scala.debugger

import java.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.{DebugProcess, SyntheticTypeComponentProvider}
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.sun.jdi.{AbsentInformationException, Location, Method, ReferenceType}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
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
    customizedLocationsCache.getOrElse(location, location.lineNumber() - 1)
  }

  def shouldSkip(location: Location): Boolean = {
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
        case aie: AbsentInformationException => return Seq.empty
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
      def shouldCustomize(location: Location) = {
        val linePosition = SourcePosition.createFromLine(containingFile, location.lineNumber() - 1)
        val elem = nonWhitespaceElement(linePosition)
        val parent = PsiTreeUtil.getParentOfType(elem, classOf[ScBlockStatement], classOf[ScEarlyDefinitions])
        parent == null || !PsiTreeUtil.isAncestor(generatingElem, parent, false)
      }

      val methods = refType.methodsByName("<init>").asScala.filter(_.declaringType() == refType)
      for {
        location <- methods.map(_.location())
        if shouldCustomize(location)
      } {
        val significantElem = DebuggerUtil.getSignificantElement(generatingElem)
        val lineNumber = elementStartLine(significantElem)
        cacheCustomLine(location, lineNumber)
      }
    }

    def customizeCaseClauses(): Unit = {

      def skipTypeCheckOptimization(method: Method, caseLineLocations: Seq[Location]): Unit = {
        val bytecodes = method.bytecodes()

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
        val bytecodes = method.bytecodes()

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

      def skipLoadExpressionValue(method: Method, baseLine: Int): Unit = {
        val bytecodes = method.bytecodes()
        val locations = locationsOfLine(method, baseLine).filter(!customizedLocationsCache.contains(_))
        if (locations.isEmpty) return

        val loadLocations = locations.filter {l =>
          BytecodeUtil.readLoadCode(l.codeIndex().toInt, bytecodes).nonEmpty
        }
        val toSkip = if (locations.size == loadLocations.size) loadLocations.tail else loadLocations
        toSkip.foreach(cacheCustomLine(_, -1))
      }

      def customizeFor(caseClauses: ScCaseClauses): Unit = {
        def tooSmall(m: Method) = {
          try m.allLineLocations().size() <= 3
          catch {
            case ae: AbsentInformationException => true
          }
        }

        val baseLine = caseClauses.getParent match {
          case ms: ScMatchStmt => ms.expr.map(elementStartLine)
          case (b: ScBlock) childOf (tr: ScTryStmt) => return //todo: handle try statements
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
          skipTypeCheckOptimization(m, caseLinesLocations.flatten)
          skipReturnValueAssignment(m, caseLinesLocations)
        }

        for {
          m <- methods
          line <- baseLine
          if locationsOfLine(m, line).size > 1
        } {
          skipLoadExpressionValue(m, line)
        }
      }

      val allCaseClauses = generatingElem.breadthFirst.collect {
        case cc: ScCaseClauses => cc
      }
      allCaseClauses.foreach(customizeFor)
    }

    customizeLineForConstructors()
    customizeCaseClauses()
  }
}

private object BytecodeUtil {
  import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.Opcodes._

  private val oneByteCodes = Map(
    istore_0 -> iload_0,
    istore_1 -> iload_1,
    istore_2 -> iload_2,
    istore_3 -> iload_3,
    astore_0 -> aload_0,
    astore_1 -> aload_1,
    astore_2 -> aload_2,
    astore_3 -> aload_3,
    dstore_0 -> dload_0,
    dstore_1 -> dload_1,
    dstore_2 -> dload_2,
    dstore_3 -> dload_3,
    fstore_0 -> fload_0,
    fstore_1 -> fload_1,
    fstore_2 -> fload_2,
    fstore_3 -> fload_3,
    lstore_0 -> lload_0,
    lstore_1 -> lload_1,
    lstore_2 -> lload_2,
    lstore_3 -> lload_3
  )

  private val oneByteLoadCodes = oneByteCodes.values.toSet
  private val oneByteStoreCodes = oneByteCodes.keySet

  private val twoBytesCodes = Map(
    istore -> iload,
    astore -> aload,
    dstore -> dload,
    fstore -> fload,
    lstore -> lload
  )

  private val twoBytesLoadCodes = twoBytesCodes.values.toSet
  private val twoBytesStoreCodes = twoBytesCodes.keySet

  def iloadCode(istoreCode: Seq[Byte]): Seq[Byte] = {
    istoreCode match {
      case Seq(`istore_0`) =>  Seq(iload_0)
      case Seq(`istore_1`) =>  Seq(iload_1)
      case Seq(`istore_2`) =>  Seq(iload_2)
      case Seq(`istore_3`) =>  Seq(iload_3)
      case Seq(`istore`, b) => Seq(iload, b)
      case _ => Nil
    }
  }

  def readIstore(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil
    bytecodes(codeIndex) match {
      case c @ (`istore_0` | `istore_1` | `istore_2` | `istore_3`) => Seq(c)
      case `istore` => Seq(istore, bytecodes(codeIndex + 1))
      case _ => Nil
    }
  }

  def readIload(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil
    bytecodes(codeIndex) match {
      case c @ (`iload_0` | `iload_1` | `iload_2` | `iload_3`) => Seq(c)
      case `iload` => Seq(iload, bytecodes(codeIndex + 1))
      case _ => Nil
    }
  }

  def readStoreCode(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil

    val bytecode = bytecodes(codeIndex)
    if (oneByteStoreCodes contains bytecode) Seq(bytecode)
    else if (twoBytesStoreCodes contains bytecode) Seq(bytecode, bytecodes(codeIndex + 1))
    else Nil
  }

  def readLoadCode(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil

    val bytecode = bytecodes(codeIndex)
    if (oneByteLoadCodes contains bytecode) Seq(bytecode)
    else if (twoBytesLoadCodes contains bytecode) Seq(bytecode, bytecodes(codeIndex + 1))
    else Nil
  }

  def loadCode(storeCode: Seq[Byte]): Seq[Byte] = {
    storeCode match {
      case Seq(b) => oneByteCodes.get(b).map(b => Seq(b)).getOrElse(Nil)
      case Seq(code, addr) => twoBytesCodes.get(code).map(b => Seq(b, addr)).getOrElse(Nil)
      case _ => Nil
    }
  }

  def isIconst_0(codeIndex: Int, bytecodes: Array[Byte]) = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) false
    else bytecodes(codeIndex) == iconst_0
  }
}
