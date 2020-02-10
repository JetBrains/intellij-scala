package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{JavaDirectoryService, PsiElement, PsiErrorElement, PsiFile, _}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, _}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, _}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object WorksheetDefaultSourcePreprocessor {

  // TODO: it is probably enough just START_TOKEN_MARKER, without END_TOKEN_MARKER, leave just one
  //  BUT FIRST: finish covering worksheets with tests, due to rendering logic is quite complicated
  val START_TOKEN_MARKER   = "###worksheet###$$start$$"
  val END_TOKEN_MARKER     = "###worksheet###$$end$$"
  val END_OUTPUT_MARKER    = "###worksheet###$$end$$!@#$%^&*(("
  val END_GENERATED_MARKER = "/* ###worksheet### generated $$end$$ */ "

  private val GenericPrintMethodName = "println"
  private val ArrayPrintMethodName = "print$$$Worksheet$$$Array$$$"

  private def printArrayText(scalaLanguageLevel: ScalaLanguageLevel): String = {
    val arrayWrapperClass =
      if (scalaLanguageLevel < ScalaLanguageLevel.Scala_2_13)
        "WrappedArray"
      else
        "ArraySeq"
    s"""
       |def $ArrayPrintMethodName(an: Any): String =
       |  an match {
       |    case arr: Array[_] =>
       |      val a = scala.collection.mutable.$arrayWrapperClass.make(arr)
       |      "Array" + a.toString.stripPrefix("$arrayWrapperClass")
       |    case null => "null"
       |    case other => other.toString
       |  }""".stripMargin
  }

  case class PreprocessResult(code: String, mainClassName: String)

  def preprocess(srcFile: ScalaFile, document: Document): Either[PsiErrorElement, PreprocessResult] = {
    if (!srcFile.isWorksheetFile) return Left(null)

    implicit val languageLevel: ScalaLanguageLevel = languageLevelForFile(srcFile)

    val iterNumber = compilationAttemptForFile(srcFile)

    val macroPrinterName = withCompilerVersion(
      "MacroPrinter210",
      "MacroPrinter211",
      "MacroPrinter213",
      "MacroPrinter3_22",
      "MacroPrinter"
    )
    val packageOpt: Option[String] = packageForFile(srcFile)

    val sourceBuilder =
      if (languageLevel == ScalaLanguageLevel.Scala_3_0) {
        new Scala3SourceBuilder(iterNumber, srcFile, document, packageOpt, macroPrinterName)
      } else {
        new Scala2SourceBuilder(iterNumber, srcFile, document, packageOpt, macroPrinterName)
      }

    val (root, preDeclarations, postDeclarations) =
      if (isForObject(srcFile)) {
        rootForObject(srcFile, sourceBuilder)
      } else {
        (srcFile, Seq(), Seq())
      }

    val rootChildren = root match {
      case file: PsiFile => file.getChildren
      case null          => srcFile.getChildren
      case other         => other.getNode.getChildren(null).map(_.getPsi)
    }

    sourceBuilder.process(rootChildren.toIterable, preDeclarations, postDeclarations)
  }

  private def languageLevelForFile(srcFile: ScalaFile) = {
    val moduleOpt  = Option(WorksheetFileSettings(srcFile).getModuleFor)
    val maybeLevel = moduleOpt.flatMap(_.scalaLanguageLevel)
    maybeLevel.getOrElse(ScalaLanguageLevel.getDefault)
  }

  private def compilationAttemptForFile(srcFile: ScalaFile): Int = {
    val cache = WorksheetCache.getInstance(srcFile.getProject)
    val prevIterNumber = cache.peakCompilationIteration(srcFile.getViewProvider.getVirtualFile.getCanonicalPath)
    prevIterNumber + 1
  }

  private def packageForFile(srcFile: ScalaFile): Option[String] =
    for {
      dir <- srcFile.getContainingDirectory.toOption
      psiPackage <- JavaDirectoryService.getInstance().getPackage(dir).toOption
      packageName = psiPackage.getQualifiedName
      if !packageName.trim.isEmpty
    } yield packageName

  private def rootForObject(srcFile: ScalaFile, sourceBuilder: ScalaSourceBuilderBase): (PsiElement, Seq[PsiElement], Seq[PsiElement]) = {
    val preDeclarations  = mutable.ListBuffer.empty[PsiElement]
    val postDeclarations = mutable.ListBuffer.empty[PsiElement]
    var root: PsiElement = null
    srcFile.getChildren.foreach {
      case imp: ScImportStmt        => sourceBuilder.processImport(imp)
      case obj: ScObject            => root = obj.extendsBlock.templateBody.getOrElse(srcFile)
      case cl: ScTemplateDefinition => (if (root == null) preDeclarations else postDeclarations) += cl
      case _                        =>
    }
    (root, preDeclarations, postDeclarations)
  }

  private def isForObject(file: ScalaFile): Boolean = {
    // Eclipse-compatible worksheets proceeds with expressions inside first object found
    val isEclipseMode = ScalaProjectSettings.getInstance(file.getProject).isUseEclipseCompatibility
    val topLevelElements = file.children.filterNot(_.is[ScImportStmt, PsiWhiteSpace, PsiComment, ScPackaging])
    topLevelElements.forall {
      case obj: ScObject => obj.extendsBlock.templateParents.isEmpty
      case _: PsiClass   => isEclipseMode
      case _             => false
    }
  }

  @inline
  def withCompilerVersion[T](if210: => T, if211: => T, if213: => T, if3: => T, default: => T)
                            (implicit languageLevel: ScalaLanguageLevel): T  =
    languageLevel match {
      case ScalaLanguageLevel.Scala_2_10 => if210
      case ScalaLanguageLevel.Scala_2_11 => if211
      case ScalaLanguageLevel.Scala_2_13 => if213
      case ScalaLanguageLevel.Scala_3_0  => if3
      case _                             => default
    }

  private def calcContentLines(document: Document, range: TextRange): Int =
    document.getLineNumber(range.getEndOffset) - document.getLineNumber(range.getStartOffset) + 1

  //noinspection HardCodedStringLiteral
  private abstract class ScalaSourceBuilderBase(iterNumber: Int,
                                                srcFile: ScalaFile,
                                                document: Document,
                                                packageOpt: Option[String]) {

    protected val className   = s"A$$A$iterNumber"
    protected val instanceName = s"inst$$A$$A"

    protected val tempVarName = "$$temp$$"

    protected def replaceStr(s: String): String = s"""replace("$s", "")"""
    protected val erasePrefixName: String = s""".stripPrefix("$className$$$className$$")"""

    protected var assignCount = 0
    protected var resCount = 0
    protected val importStmts: ArrayBuffer[String] = mutable.ArrayBuffer[String]()
    protected val importsProcessed: mutable.HashSet[ScImportStmt] = mutable.HashSet[ScImportStmt]()

    private val debugLogEnabled = false

    private val classBuilder = new StringBuilder
    private val mainMethodBuilder = new StringBuilder

    protected def printMethodName: String = GenericPrintMethodName

    protected def extraGlobalImports: Seq[String]

    protected def varTypeInfo(varName: String): String

    protected def importInfoString(imp: ScImportStmt): String

    protected def funDefInfoString(fun: ScFunction): String

    def process(elements: Iterable[PsiElement],
                preDeclarations: Iterable[PsiElement],
                postDeclarations: Iterable[PsiElement])
               (implicit languageLevel: ScalaLanguageLevel): Either[PsiErrorElement, PreprocessResult] = {

      val (classStart, classEnd) = (
        s"final class $className { \n",
        s"\n}"
      )

      val (mainMethodStart, mainMethodEnd) = {
        val unitReturnType = ": Unit ="
        val mainReturnType = withCompilerVersion("", unitReturnType, unitReturnType, unitReturnType, unitReturnType)
        (
          s"""def main(ignored: java.io.PrintStream)$mainReturnType {
             |  val $instanceName = new $className
             |""".stripMargin,
          s"""  $printMethodName("$END_OUTPUT_MARKER")
             |}""".stripMargin
        )
      }

      classBuilder.append(classStart)
      insertUntouched(classBuilder, preDeclarations)

      mainMethodBuilder.append(mainMethodStart)

      for (e <- elements) e match {
        case tpe: ScTypeAlias             => processTypeAlias(tpe)
        case fun: ScFunction              => processFunDef(fun)
        case tpeDef: ScTypeDefinition     => processTypeDef(tpeDef)
        case valDef: ScPatternDefinition  => processValDef(valDef)
        case varDef: ScVariableDefinition => processVarDef(varDef)
        case assign: ScAssignment         => processAssign(assign)
        case imp: ScImportStmt            => if (!processLocalImport(imp)) processImport(imp)
        case comment: PsiComment          => appendCommentToClass(comment)
        case _: ScPackaging               => //skip
        case otherExpr: ScExpression      => processOtherExpr(otherExpr)
        case _: PsiWhiteSpace             => //skip
        case error: PsiErrorElement       => return Left(error)
        case null                         => logError(None)
        case unknown                      => processUnknownElement(unknown)
      }

      insertUntouched(classBuilder, postDeclarations)
      classBuilder.append(classEnd)

      mainMethodBuilder.append(mainMethodEnd)

      val (objectStart, objectEnd) = {
        val packStmt = packageOpt.map("package " + _)
        val imports = extraGlobalImports
        val packageAndImports = packStmt ++ imports
        (
          s"""${packageAndImports.mkString(";")}
             |
             |object $className {""".stripMargin,
          s"""${printArrayText(languageLevel)}
             |
             |}""".stripMargin
        )
      }

      val codeResult =
        s"""$objectStart
           |
           |${importStmts.mkString(";")}
           |
           |${classBuilder.toString()}
           |
           |${mainMethodBuilder.toString()}
           |
           |$objectEnd
           |""".stripMargin

      val mainClassName = (packageOpt.toSeq :+ className).mkString(".")

      Right(PreprocessResult(codeResult, mainClassName))
    }

    // see util methods
    protected def printVarKeyword: Boolean = false
    protected def printValKeyword: Boolean = false
    protected def printLineCommentBeforeTypeDef: Boolean = false
    protected def printImports: Boolean = true

    protected final def valPrefix: String = if (printValKeyword) "val " else ""
    protected final def varPrefix: String = if (printVarKeyword) "var " else ""

    // currently used for debug purposes only
    protected def logError(psiElementOpt: Option[PsiElement], message: Option[String] = None): Unit = {
      if (!debugLogEnabled) return

      def writeLog(finalMessage: String): Unit = println(finalMessage)

      val logMessage = (message, psiElementOpt) match {
        case (Some(msg), Some(psiElement)) => s"$msg ${psiElement.getText} ${psiElement.getClass}"
        case (Some(msg), None)             => s"$msg null"
        case (None, Some(psiElement))      => s"Unknown element: $psiElement"
        case (None, None)                  => "PsiElement is null"
        case _                             => "???"
      }
      writeLog(logMessage)
    }

    protected def prettyPrintTypeWithValue(callee: String): String = {
      val target = s"$instanceName."

      val varDecl  = s"""val $tempVarName = $target$callee"""
      val varType  = s"""${varTypeInfo(tempVarName)}.${replaceStr(target)}"""
      val varValue = s"""$ArrayPrintMethodName($tempVarName)$erasePrefixName"""

      s"""{$varDecl ; $varType + " = " + $varValue }"""
    }

    protected def processTypeAlias(tpe: ScTypeAlias): Unit =
      withPrecomputedLines(tpe) {
        mainMethodBuilder.append(withPrint(s"defined type alias ${tpe.name}"))
      }

    protected def processFunDef(fun: ScFunction): Unit =
      withPrecomputedLines(fun) {
        printInMain(s""""${fun.getName}: " + ${funDefInfoString(fun)}""")
      }

    protected def processTypeDef(tpeDef: ScTypeDefinition): Unit =
      withPrecomputedLines(tpeDef) {
        val keyword = tpeDef match {
          case _: ScEnum   => "enum"
          case _: ScClass  => "class"
          case _: ScTrait  => "trait"
          case _: ScObject => "object"
          case _           => "module"
        }
        val commentPrefix = if (printLineCommentBeforeTypeDef) "// " else ""
        mainMethodBuilder.append(withPrint(s"${commentPrefix}defined $keyword ${tpeDef.name}"))
      }

    protected def processValDef(valDef: ScPatternDefinition): Unit =
      withPrecomputedLines(valDef) {
        valDef.bindings.foreach { binding =>
          val pName = binding.name
          val defName = variableInstanceName(pName)

          classBuilder.append(s"def $defName = $pName;$END_GENERATED_MARKER")
          printInMain(s""""$valPrefix$pName: " + ${prettyPrintTypeWithValue(defName)}""")
        }
      }

    protected def processVarDef(varDef: ScVariableDefinition): Unit = {
      def writeTypedPatter(p: ScTypedPattern) =
        p.typePattern.map(typed => p.name + ":" + typed.typeElement.getText).getOrElse(p.name)

      def typeElement2Types(te: ScTypeElement) = te match {
        case tpl: ScTupleTypeElement => tpl.components
        case other => Seq(other)
      }

      def withOptionalBraces(s: Seq[String]): Option[String] = s match {
        case Seq()     => None
        case Seq(head) => Some(head)
        case seq       => Some(seq.mkString("(", ",", ")"))
      }

      val lineNum = psiToLineNumbers(varDef)

      def varDefText(names: String, expr: ScExpression): String =
        s"var $names = { ${expr.getText}; }"

      appendStartPsiLineInfo(lineNum)

      // TODO: fix for var a, b = 7 SCL-13307
      val txt = (varDef.typeElement, varDef.expr) match {
        case (Some(tpl: ScTypeElement), Some(expr)) =>
          val namesList = typeElement2Types(tpl).zip(varDef.declaredElements).map { case (tpe, el) =>
            el.name + ": " + tpe.getText
          }
          val names = withOptionalBraces(namesList)
          varDefText(names.getOrElse("_"), expr)
        case (_, Some(expr)) =>
          val namesList = varDef.declaredElements.map {
            case tpePattern: ScTypedPattern => writeTypedPatter(tpePattern)
            case a                          => a.name
          }
          val names = withOptionalBraces(namesList)
          varDefText(names.getOrElse("_"), expr)
        case _ =>
          varDef.getText
      }

      classBuilder.append(txt).append(";\n")
      varDef.declaredNames.foreach { pName =>
        printInMain(s""""$varPrefix$pName: " + ${prettyPrintTypeWithValue(pName)}""")
      }

      appendEndPsiLineInfo(lineNum)
    }

    protected def processAssign(assign: ScAssignment): Unit = {
      val pName = assign.leftExpression.getText
      val lineNums = psiToLineNumbers(assign)
      val defName = s"`get$$$$instance_$assignCount$$$$$pName`"

      appendStartPsiLineInfo(lineNums)

      classBuilder.append(s"""def $defName = { $END_GENERATED_MARKER${assign.getText}}${insertNlsFromWs(assign)}""")
      mainMethodBuilder.append(s"""$instanceName.$defName; """)
      printInMain(s""""$pName: " + ${prettyPrintTypeWithValue(pName)}""")

      appendEndPsiLineInfo(lineNums)

      assignCount += 1
    }

    protected def processLocalImport(imp: ScImportStmt): Boolean = {
      if (imp.importExprs.lengthCompare(1) < 0) return false

      var currentQual = imp.importExprs.head.qualifier
      var lastFound: Option[(ScStableCodeReference, PsiElement)] = None

      while (currentQual != null) {
        currentQual.resolve() match {
          case el: PsiElement if el.getContainingFile == srcFile =>
            lastFound = Some(currentQual, el)
          case _ =>
        }

        currentQual = currentQual.qualifier.orNull
      }

      lastFound exists {
        case (lastQualifier, el) =>
          val qualifierName = lastQualifier.qualName
          val lineNums = psiToLineNumbers(imp)
          val memberName = if (el.isInstanceOf[ScValue] || el.isInstanceOf[ScVariable]) //variable to avoid weird errors
          variableInstanceName(qualifierName) else qualifierName

          if (printImports) {
            appendStartPsiLineInfo(lineNums)
            mainMethodBuilder.append(s";{val $qualifierName = $instanceName.$memberName; $printMethodName(${importInfoString(imp)})}").append("\n")
            appendEndPsiLineInfo(lineNums)
          }

          classBuilder.append(s"${imp.getText}${insertNlsFromWs(imp)}")

          true
      }
    }

    def processImport(imp: ScImportStmt): Unit = {
      if (importsProcessed.contains(imp)) return

      val lineNums = psiToLineNumbers(imp)
      if (printImports) {
        appendStartPsiLineInfo(lineNums)
        printInMain(importInfoString(imp))
        appendEndPsiLineInfo(lineNums)
      }

      importStmts += imp.getText
      importsProcessed += imp
    }

    protected def appendCommentToClass(comment: PsiComment): Unit = {
      val range = comment.getTextRange
      if (comment.getNode.getElementType != ScalaTokenTypes.tLINE_COMMENT) return

      val count = calcContentLines(document, range)

      for (_ <- 0 until count)
        classBuilder.append("//\n")

      classBuilder.append(insertNlsFromWs(comment).stripPrefix("\n"))
    }

    protected def processOtherExpr(expr: ScExpression): Unit = {
      val resName = s"res$resCount"
      val resMethodName = s"get$$$$instance$$$$$resName"
      val lineNums = psiToLineNumbers(expr)

      // TODO: looks like this resN are not used anywhere and can be dropped (just print the value, except Unit type)
      classBuilder.append(s"""def $resMethodName = $END_GENERATED_MARKER${expr.getText}${insertNlsFromWs(expr)}""")

      appendStartPsiLineInfo(lineNums)
      printInMain(s""""$valPrefix$resName: " + ${prettyPrintTypeWithValue(resMethodName)}""")
      appendEndPsiLineInfo(lineNums)

      resCount += 1
    }

    protected def insertUntouched(builder: StringBuilder, exprs: Iterable[PsiElement]): Unit =
      exprs.foreach { expr =>
        builder.append(expr.getText).append(insertNlsFromWs(expr))
      }

    protected def processUnknownElement(element: PsiElement): Unit =
      logError(Option(element))

    //kinda utils stuff that shouldn't be overridden

    @inline final def withPrint(content: String): String = withPrintRaw("\"" + content + "\"")
    @inline final def withPrintRaw(stringLiteral: String): String = s"""$printMethodName($stringLiteral)""" + "\n"

    @inline final def variableInstanceName(name: String): String =
      if (name.startsWith("`")) {
        s"`get$$$$instance$$$$${name.stripPrefix("`")}"
      } else {
        s"get$$$$instance$$$$$name"
      }

    @inline final def countNewLines(str: String): Int = str.count(_ == '\n')

    @inline final def insertNlsFromWs(psi: PsiElement): String = {
      val newLines = psi.getNextSibling match {
        case ws: PsiWhiteSpace => countNewLines(ws.getText)
        case _                 => 0
      }
      if (newLines == 0) ";"
      else "\n" * newLines
    }

    @inline final def psiToLineNumbers(psi: PsiElement): String = {
      val actualPsi: PsiElement =
        psi.getFirstChild match {
          case comment: PsiComment =>
            val nonEmptyElements = comment.nextSiblings.filterNot(el => el.is[PsiComment, PsiWhiteSpace] || el.getTextLength == 0)
            nonEmptyElements.headOption.getOrElse(psi)
          case _ =>
            psi
        }

      val start = actualPsi.startOffset //actualPsi for start and psi for end - it is intentional
      val end = psi.endOffset
      s"${document.getLineNumber(start)}|${document.getLineNumber(end)}"
    }

    @inline def printInMain(text: String): Unit =
      mainMethodBuilder.append(withPrintRaw(text))

    @inline final def appendStartPsiLineInfo(numberStr: String): Unit =
      printInMain(s""""$START_TOKEN_MARKER$numberStr"""")

    @inline final def appendEndPsiLineInfo(numberStr: String): Unit =
      printInMain(s""""$END_TOKEN_MARKER$numberStr"""")

    @inline final def withPrecomputedLines(psi: ScalaPsiElement)(body: => Unit): Unit = {
      val lineNum = psiToLineNumbers(psi)
      appendStartPsiLineInfo(lineNum)
      body
      appendDeclaration(psi)
      appendEndPsiLineInfo(lineNum)
    }

    @inline final def appendDeclaration(psi: ScalaPsiElement): Unit = {
      psi match {
        case valDef: ScPatternDefinition if !valDef.getModifierList.isLazy =>
          classBuilder.append("lazy").append(" ")
        case _ =>
      }

      classBuilder.append(psi.getText)
        .append(insertNlsFromWs(psi))
    }

    @inline final def quoted(s: String): String = '"' + s + '"'

    @inline final def accessModifierText(fun: ScFunction): String =
      fun.getModifierList.accessModifier.map(PresentationUtil.accessModifierText).mkString
  }

  private class Scala2SourceBuilder(iterNumber: Int,
                                    srcFile: ScalaFile,
                                    document: Document,
                                    packageOpt: Option[String],
                                    typePrinterName: String)
    extends ScalaSourceBuilderBase(iterNumber, srcFile, document, packageOpt) {

    override protected def extraGlobalImports: Seq[String] =
      Seq(s"import _root_.org.jetbrains.plugins.scala.worksheet.$typePrinterName")

    override protected def varTypeInfo(varName: String): String =
      s"$typePrinterName.printDefInfo($varName)"

    override protected def importInfoString(imp: ScImportStmt): String =
      s"$typePrinterName.printImportInfo({${imp.getText};})"

    override protected def funDefInfoString(fun: ScFunction): String = {
      // TODO: do we need that import instanceName at all?
      s"$typePrinterName.printGeneric({import $instanceName._ ;${fun.getText.stripPrefix(accessModifierText(fun))} }).${replaceStr(instanceName)}"
    }
  }

  // TODO: do not display for resN: Unit, e.g. after println(42), () (not only display but do not create a resN for them)
  // see also dotty.tools.dotc.printing.ReplPrinter
  // see also dotty.tools.dotc.printing.RefinedPrinter
  // see quite interesting util method: dotty.tools.dotc.printing.Texts.Text#~~
  private class Scala3SourceBuilder(iterNumber: Int,
                                    srcFile: ScalaFile,
                                    document: Document,
                                    packageOpt: Option[String],
                                    typePrinterName: String)
    extends ScalaSourceBuilderBase(iterNumber, srcFile, document, packageOpt) {

    override protected def extraGlobalImports: Seq[String] =
      Seq(s"import _root_.org.jetbrains.plugins.scala.worksheet.$typePrinterName")

    override protected def varTypeInfo(varName: String): String =
      s"$typePrinterName.showType($varName)"

    override protected def importInfoString(imp: ScImportStmt): String =
      quoted(imp.getText)

    override protected def funDefInfoString(fun: ScFunction): String =
      s"$typePrinterName.showMethodDefinition({ ${fun.getText.stripPrefix(accessModifierText(fun))} })"

    override protected def processFunDef(fun: ScFunction): Unit =
      withPrecomputedLines(fun) {
        printInMain(funDefInfoString(fun))
      }

    // overridden to make PLAIN output as close to REPL output as possible
    override protected def printValKeyword: Boolean = true
    override protected def printVarKeyword: Boolean = true
    override protected def printLineCommentBeforeTypeDef: Boolean = true
    override protected def printImports: Boolean = false
  }

  def inputLinesRangeFromEnd(encodedLine: String): Option[(Int, Int)] =
    inputLinesRangeFrom(encodedLine, END_TOKEN_MARKER)

  def inputLinesRangeFromStart(encodedLine: String): Option[(Int, Int)] =
    inputLinesRangeFrom(encodedLine, START_TOKEN_MARKER)

  private def inputLinesRangeFrom(encodedLine: String, prefixMarker: String): Option[(Int, Int)] = {
    if (encodedLine.startsWith(prefixMarker)) {
      val startWithEnd = encodedLine.stripPrefix(prefixMarker).stripSuffix("\n").split('|')
      startWithEnd match {
        case Array(start, end) =>
          for {
            s <- start.toIntOpt
            e <- end.toIntOpt
            if s != -1 && e != -1
          } yield (s, e)
        case _ => None
      }
    } else None
  }
}
