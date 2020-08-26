package org.jetbrains.plugins.scala.debugger

import java.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.{DebugProcess, SyntheticTypeComponentProvider}
import com.intellij.debugger.jdi.GeneratedLocation
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.sun.jdi.{AbsentInformationException, Location, Method, ReferenceType}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement, ScMatch, ScTry}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
  * @author Nikolay.Tropin
  */
trait LocationLineManager {
  self: ScalaPositionManager =>

  import LocationLineManager._
  import self.caches._

  private val syntheticProvider = SyntheticTypeComponentProvider.EP_NAME.findExtension(classOf[ScalaSyntheticProvider])

  def clearLocationLineCaches(): Unit = {
    customizedLocationsCache.clear()
    lineToCustomizedLocationCache.clear()
    seenRefTypes.clear()
  }

  def exactLineNumber(location: Location): Int = location match {
    case gen: GeneratedLocation =>
      gen.lineNumber()
    case _ =>
      checkAndUpdateCaches(location.declaringType())
      customizedLocationsCache.getOrElse(location, ScalaPositionManager.checkedLineNumber(location))
  }

  def shouldSkip(location: Location): Boolean = {
    if (!DebuggerUtil.isScala(location.declaringType(), default = false)) return false

    val synth = DebuggerSettings.getInstance().SKIP_SYNTHETIC_METHODS && syntheticProvider.isSynthetic(location.method())
    synth || exactLineNumber(location) < 0
  }

  def locationsOfLine(refType: ReferenceType, line: Int): collection.Seq[Location] = {
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

  private def checkAndUpdateCaches(refType: ReferenceType): Unit = {
    if (!seenRefTypes.contains(refType)) inReadAction(computeCustomizedLocationsFor(refType))
  }

  private def cacheCustomLine(location: Location, customLine: Int): Unit = location match {
    case _: GeneratedLocation => //don't cache, equals is broken
    case _ =>
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

    def elementStartLine(e: PsiElement): Int = document.getLineNumber(e.getTextOffset)
    def locationsOfLine(m: Method, line: Int): collection.Seq[Location] =
      Try(m.locationsOfLine(line + 1).asScala).getOrElse(Seq.empty)

    //scalac sometimes generates very strange line numbers for <init> method
    def customizeLineForConstructors(): Unit = {
      //2.12 generates line number for return of constructor, it has no use in debugger
      def isReturnInstr(location: Location): Boolean = {
        try {
          val bytecodes = location.method().bytecodes()
          val index = location.codeIndex()
          bytecodes(index.toInt) == voidReturn
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
          val bytes = readIstore(codeIndex + 1, bytecodes) match {
            case Seq() => Nil
            case istoreCode => iloadCode(istoreCode)
          }
          if (bytes.isEmpty) return

          method.allLineLocations().asScala.foreach {
            case loc if readIload(loc.codeIndex().toInt, bytecodes) == bytes =>
              cacheCustomLine(loc, -1)
            case _ =>
          }
        }

        val iconst_0Locations = caseLineLocations.filter(l => isIconst_0(l.codeIndex().toInt, bytecodes))

        iconst_0Locations.foreach { l =>
          cacheCustomLine(l, -1)
          cacheCorrespondingIloadLocations(l)
        }
      }

      def skipReturnValueAssignment(method: Method, caseLinesLocations: collection.Seq[collection.Seq[Location]]): Unit = {
        val bytecodes =
          try method.bytecodes()
          catch {case _: Throwable => return }

        def storeCode(location: Location): Option[Seq[Byte]] = {
          val codeIndex = location.codeIndex().toInt
          val code = readStoreCode(codeIndex, bytecodes)
          if (code.nonEmpty) Some(code) else None
        }

        val notCustomizedYet = caseLinesLocations.map(_.filter(!customizedLocationsCache.contains(_)))
        val repeating = notCustomizedYet.filter(_.size > 1)
        val lastLocations = repeating.map(_.last)
        val withStoreCode = for (loc <- lastLocations; code <- storeCode(loc)) yield (loc, code)
        val (locationsToSkip, codes) = withStoreCode.unzip
        if (codes.distinct.size != 1) return

        locationsToSkip.foreach(cacheCustomLine(_, -1))

        val bytes = loadCode(codes.head)
        if (bytes.isEmpty) return

        val loadLocations = method.allLineLocations().asScala.filter { l =>
          readLoadCode(l.codeIndex().toInt, bytecodes) == bytes
        }
        loadLocations.foreach(cacheCustomLine(_, -1))
      }

      def skipBaseLineExtraLocations(method: Method, locations: collection.Seq[Location]): Unit = {
        val filtered = locations.filter(!customizedLocationsCache.contains(_))
        if (filtered.size <= 1) return

        val bytecodes =
          try method.bytecodes()
          catch {
            case _: Throwable => return
          }

        val tail: collection.Seq[Location] = filtered.tail

        val loadExpressionValueLocations = tail.filter { l =>
          readLoadCode(l.codeIndex().toInt, bytecodes).nonEmpty
        }

        val returnLocations = tail.filter { l =>
          returnCodes.contains(bytecodes(l.codeIndex().toInt))
        }

        (loadExpressionValueLocations ++ returnLocations).foreach(cacheCustomLine(_, -1))
      }

      def skipGotoLocations(method: Method, possibleLocations: collection.Seq[Location]): Unit = {
        val bytecodes =
          try method.bytecodes()
          catch {case _: Throwable => return }

        val gotos = possibleLocations.filter(loc => isGoto(loc.codeIndex().toInt, bytecodes))
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
          case ms: ScMatch => ms.expression.map(elementStartLine)
          case (_: ScBlock) childOf (_: ScTry) => return //todo: handle try statements
          case b: ScBlock => Some(elementStartLine(b))
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

object LocationLineManager {

  val iconst_0: Byte = 0x03.toByte

  val istore_0: Byte = 0x3b.toByte
  val istore_1: Byte = 0x3c.toByte
  val istore_2: Byte = 0x3d.toByte
  val istore_3: Byte = 0x3e.toByte
  val istore: Byte = 0x36.toByte

  val iload_0: Byte = 0x1a.toByte
  val iload_1: Byte = 0x1b.toByte
  val iload_2: Byte = 0x1c.toByte
  val iload_3: Byte = 0x1d.toByte
  val iload: Byte = 0x15.toByte

  val aload: Byte = 0x19.toByte
  val aload_0: Byte = 0x2a.toByte
  val aload_1: Byte = 0x2b.toByte
  val aload_2: Byte = 0x2c.toByte
  val aload_3: Byte = 0x2d.toByte

  val astore: Byte = 0x3a.toByte
  val astore_0: Byte = 0x4b.toByte
  val astore_1: Byte = 0x4c.toByte
  val astore_2: Byte = 0x4d.toByte
  val astore_3: Byte = 0x4e.toByte

  val dload: Byte = 0x18.toByte
  val dload_0: Byte = 0x26.toByte
  val dload_1: Byte = 0x27.toByte
  val dload_2: Byte = 0x28.toByte
  val dload_3: Byte = 0x29.toByte

  val dstore: Byte = 0x39.toByte
  val dstore_0: Byte = 0x47.toByte
  val dstore_1: Byte = 0x48.toByte
  val dstore_2: Byte = 0x49.toByte
  val dstore_3: Byte = 0x4a.toByte

  val fload: Byte = 0x17.toByte
  val fload_0: Byte = 0x22.toByte
  val fload_1: Byte = 0x23.toByte
  val fload_2: Byte = 0x24.toByte
  val fload_3: Byte = 0x25.toByte

  val fstore: Byte = 0x38.toByte
  val fstore_0: Byte = 0x43.toByte
  val fstore_1: Byte = 0x44.toByte
  val fstore_2: Byte = 0x45.toByte
  val fstore_3: Byte = 0x46.toByte

  val lload: Byte = 0x16.toByte
  val lload_0: Byte = 0x1e.toByte
  val lload_1: Byte = 0x1f.toByte
  val lload_2: Byte = 0x20.toByte
  val lload_3: Byte = 0x21.toByte

  val lstore: Byte = 0x37.toByte
  val lstore_0: Byte = 0x3f.toByte
  val lstore_1: Byte = 0x40.toByte
  val lstore_2: Byte = 0x41.toByte
  val lstore_3: Byte = 0x42.toByte

  val invokeStatic: Byte = 0xB8.toByte

  val areturn: Byte = 0xB0.toByte
  val dreturn: Byte = 0xAF.toByte
  val freturn: Byte = 0xAE.toByte
  val ireturn: Byte = 0xAC.toByte
  val lreturn: Byte = 0xAD.toByte
  val voidReturn: Byte = 0xB1.toByte

  val goto: Byte = 0xA7.toByte

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

  val oneByteLoadCodes: Set[Byte] = oneByteCodes.values.toSet
  val oneByteStoreCodes: Set[Byte] = oneByteCodes.keySet

  private val twoBytesCodes = Map(
    istore -> iload,
    astore -> aload,
    dstore -> dload,
    fstore -> fload,
    lstore -> lload
  )

  val twoBytesLoadCodes: Set[Byte] = twoBytesCodes.values.toSet
  val twoBytesStoreCodes: Set[Byte] = twoBytesCodes.keySet

  val returnCodes = Set(areturn, dreturn, freturn, ireturn, lreturn, voidReturn)

  private def iloadCode(istoreCode: Seq[Byte]): Seq[Byte] = {
    istoreCode match {
      case Seq(`istore_0`) => Seq(iload_0)
      case Seq(`istore_1`) => Seq(iload_1)
      case Seq(`istore_2`) => Seq(iload_2)
      case Seq(`istore_3`) => Seq(iload_3)
      case Seq(`istore`, b) => Seq(iload, b)
      case _ => Nil
    }
  }

  private def readIstore(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil
    bytecodes(codeIndex) match {
      case c@(`istore_0` | `istore_1` | `istore_2` | `istore_3`) => Seq(c)
      case `istore` => Seq(istore, bytecodes(codeIndex + 1))
      case _ => Nil
    }
  }

  private def readIload(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil
    bytecodes(codeIndex) match {
      case c@(`iload_0` | `iload_1` | `iload_2` | `iload_3`) => Seq(c)
      case `iload` => Seq(iload, bytecodes(codeIndex + 1))
      case _ => Nil
    }
  }

  private def readStoreCode(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil

    val bytecode = bytecodes(codeIndex)
    if (oneByteStoreCodes contains bytecode) Seq(bytecode)
    else if (twoBytesStoreCodes contains bytecode) Seq(bytecode, bytecodes(codeIndex + 1))
    else Nil
  }

  private def readLoadCode(codeIndex: Int, bytecodes: Array[Byte]): Seq[Byte] = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) return Nil

    val bytecode = bytecodes(codeIndex)
    if (oneByteLoadCodes contains bytecode) Seq(bytecode)
    else if (twoBytesLoadCodes contains bytecode) Seq(bytecode, bytecodes(codeIndex + 1))
    else Nil
  }

  private def loadCode(storeCode: Seq[Byte]): Seq[Byte] = {
    storeCode match {
      case Seq(b) => oneByteCodes.get(b).map(b => Seq(b)).getOrElse(Nil)
      case Seq(code, addr) => twoBytesCodes.get(code).map(b => Seq(b, addr)).getOrElse(Nil)
      case _ => Nil
    }
  }

  private def isIconst_0(codeIndex: Int, bytecodes: Array[Byte]): Boolean = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) false
    else bytecodes(codeIndex) == iconst_0
  }

  private def isGoto(codeIndex: Int, bytecodes: Array[Byte]): Boolean = {
    if (codeIndex < 0 || codeIndex > bytecodes.length - 1) false
    else bytecodes(codeIndex) == goto
  }
}
