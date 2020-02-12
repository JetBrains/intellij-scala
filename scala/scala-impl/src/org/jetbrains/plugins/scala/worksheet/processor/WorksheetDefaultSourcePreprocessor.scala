package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{JavaDirectoryService, PsiElement, PsiErrorElement, PsiFile, _}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil.accessModifierText
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
    s"""def $ArrayPrintMethodName(an: Any): String =
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
    val macroPrinterName = withCompilerVersion("MacroPrinter210", "MacroPrinter211", "MacroPrinter213", "MacroPrinter")
    val packageOpt: Option[String] = packageForFile(srcFile)

    val sourceBuilder = new ScalaSourceBuilder(
      iterNumber,
      srcFile,
      document,
      macroPrinterName,
      packageOpt
    )

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

  private def rootForObject(srcFile: ScalaFile, sourceBuilder: ScalaSourceBuilder): (PsiElement, Seq[PsiElement], Seq[PsiElement]) = {
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
  def withCompilerVersion[T](if210: => T, if211: => T, if213: => T, default: => T)
                            (implicit languageLevel: ScalaLanguageLevel): T  =
    languageLevel match {
      case ScalaLanguageLevel.Scala_2_10 => if210
      case ScalaLanguageLevel.Scala_2_11 => if211
      case ScalaLanguageLevel.Scala_2_13 => if213
      case _                             => default
    }

  private def calcContentLines(document: Document, range: TextRange): Int =
    document.getLineNumber(range.getEndOffset) - document.getLineNumber(range.getStartOffset) + 1

  //noinspection HardCodedStringLiteral
  private class ScalaSourceBuilder(iterNumber: Int,
                                   srcFile: ScalaFile,
                                   document: Document,
                                   tpePrinterName: String,
                                   packageOpt: Option[String]) {

    protected val className   = s"A$$A$iterNumber"
    protected val instanceName = s"inst$$A$$A"

    protected val tempVarName = "$$temp$$"

    protected val eraseClassName: String = s""".replace("$instanceName.", "")"""
    protected val erasePrefixName: String = s""".stripPrefix("$className$$$className$$")"""
    protected val plusInfoDef = " + "

    protected var assignCount = 0
    protected var resCount = 0
    protected val importStmts: ArrayBuffer[String] = mutable.ArrayBuffer[String]()
    protected val importsProcessed: mutable.HashSet[ScImportStmt] = mutable.HashSet[ScImportStmt]()

    private val debugLogEnabled = false

    private val classBuilder = new StringBuilder
    private val mainMethodBuilder = new StringBuilder

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
        val mainReturnType = withCompilerVersion("", unitReturnType, unitReturnType, unitReturnType)
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
        val macroImport = s"import _root_.org.jetbrains.plugins.scala.worksheet.$tpePrinterName"
        val packageAndImports = packStmt.toSeq :+ macroImport
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


    protected def getTypePrinterName: String = tpePrinterName

    protected def prettyPrintType(tpeString: String): String = s""": " + ${withTempVar(tpeString)}"""

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

    protected def getTempVarInfo(varName: String): String = s"$getTypePrinterName.printDefInfo($varName)"

    protected def getImportInfoString(imp: ScImportStmt): String = {
      val text = imp.getText
      s"$getTypePrinterName.printImportInfo({$text;})"
    }

    protected def getFunDefInfoString(fun: ScFunction): String = {
      val accessModifier = fun.getModifierList.accessModifier
      val hadMods = accessModifier.map(accessModifierText).getOrElse("")
      getTypePrinterName + s".printGeneric({import $instanceName._ ;" + fun.getText.stripPrefix(hadMods) + " })" + eraseClassName
    }

    protected def printMethodName: String = GenericPrintMethodName

    protected def processTypeAlias(tpe: ScTypeAlias): Unit =
      withPrecomputedLines(tpe) {
        mainMethodBuilder.append(withPrint(s"defined type alias ${tpe.name}"))
      }

    protected def processFunDef(fun: ScFunction): Unit =
      withPrecomputedLines(fun) {
        mainMethodBuilder.append(s"""$printMethodName("${fun.getName}: " + ${getFunDefInfoString(fun)})""").append("\n")
      }

    protected def processTypeDef(tpeDef: ScTypeDefinition): Unit =
      withPrecomputedLines(tpeDef) {
        val keyword = tpeDef match {
          case _: ScClass => "class"
          case _: ScTrait => "trait"
          case _          => "module" // TODO: change to `object`?
        }

        mainMethodBuilder.append(withPrint(s"defined $keyword ${tpeDef.name}"))
      }

    protected def processValDef(valDef: ScPatternDefinition): Unit =
      withPrecomputedLines(valDef) {
        valDef.bindings.foreach { binding =>
          val pName = binding.name
          val defName = variableInstanceName(pName)

          classBuilder.append(s"def $defName = $pName;$END_GENERATED_MARKER")

          mainMethodBuilder.append(s"""$printMethodName("$pName${prettyPrintType(defName)})""").append("\n")
        }
      }

    protected def processVarDef(varDef: ScVariableDefinition): Unit = {
      def writeTypedPatter(p: ScTypedPattern) =
        p.typePattern map (typed => p.name + ":" + typed.typeElement.getText) getOrElse p.name

      def typeElement2Types(te: ScTypeElement) = te match {
        case tpl: ScTupleTypeElement => tpl.components
        case other => Seq(other)
      }

      def withOptionalBraces(s: Iterable[String]) = if (s.size == 1) s.head else s.mkString("(", ",", ")")

      val lineNum = psiToLineNumbers(varDef)

      def varDefText(names: String, expr: ScExpression): String =
        s"var $names = { ${expr.getText}; }"

      appendStartPsiLineInfo(lineNum)

      val txt = (varDef.typeElement, varDef.expr) match {
        case (Some(tpl: ScTypeElement), Some(expr)) =>
          val names = withOptionalBraces {
            typeElement2Types(tpl).zip(varDef.declaredElements).map { case (tpe, el) =>
              el.name + ": " + tpe.getText
            }
          }

          varDefText(names, expr)
        case (_, Some(expr)) =>
          val names = withOptionalBraces {
            varDef.declaredElements.map {
              case tpePattern: ScTypedPattern => writeTypedPatter(tpePattern)
              case a => a.name
            }
          }

          varDefText(names, expr)
        case _ => varDef.getText
      }

      classBuilder.append(txt).append(";")
      varDef.declaredNames.foreach { pName =>
        mainMethodBuilder.append(s"""$printMethodName("$pName${prettyPrintType(pName)})""").append("\n")
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
      mainMethodBuilder.append(s"""$printMethodName("$pName${prettyPrintType(pName)})""").append("\n")

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

          appendStartPsiLineInfo(lineNums)

          mainMethodBuilder.append(s";{val $qualifierName = $instanceName.$memberName; $printMethodName(${getImportInfoString(imp)})}").append("\n")
          classBuilder.append(s"${imp.getText}${insertNlsFromWs(imp)}")

          appendEndPsiLineInfo(lineNums)
          true
      }
    }

    def processImport(imp: ScImportStmt): Unit = {
      if (importsProcessed.contains(imp)) return

      val lineNums = psiToLineNumbers(imp)
      appendStartPsiLineInfo(lineNums)
      mainMethodBuilder.append(s"$printMethodName(${getImportInfoString(imp)})").append("\n")
      appendEndPsiLineInfo(lineNums)

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
      val resName = s"get$$$$instance$$$$res$resCount"
      val lineNums = psiToLineNumbers(expr)

      appendStartPsiLineInfo(lineNums)
      classBuilder.append(s"""def $resName = $END_GENERATED_MARKER${expr.getText}${insertNlsFromWs(expr)}""")
      mainMethodBuilder.append(s"""$printMethodName("res$resCount${prettyPrintType(resName)})""").append("\n")
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

    @inline final def withTempVar(callee: String, withInstance: Boolean = true): String = {
      val target = if (withInstance) instanceName + "." else ""
      s"""{val $tempVarName = $target$callee ; ${getTempVarInfo(tempVarName)}$eraseClassName$plusInfoDef" = " + ( $ArrayPrintMethodName($tempVarName) )$erasePrefixName}"""
    }

    @inline final def withPrint(text: String): String = s"""$printMethodName("$text")""" + "\n"

    @inline final def withPrecomputedLines(psi: ScalaPsiElement)(body: => Unit): Unit = {
      val lineNum = psiToLineNumbers(psi)
      appendStartPsiLineInfo(lineNum)
      body
      appendDeclaration(psi)
      appendEndPsiLineInfo(lineNum)
    }

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

    @inline final def appendStartPsiLineInfo(numberStr: String): Unit =
      mainMethodBuilder.append(s"""$printMethodName("$START_TOKEN_MARKER$numberStr")""").append("\n")

    @inline final def appendEndPsiLineInfo(numberStr: String): Unit =
      mainMethodBuilder.append(s"""$printMethodName("$END_TOKEN_MARKER$numberStr")""").append("\n")

    @inline final def appendDeclaration(psi: ScalaPsiElement): Unit = {
      psi match {
        case valDef: ScPatternDefinition if !valDef.getModifierList.isLazy =>
          classBuilder.append("lazy").append(" ")
        case _ =>
      }

      classBuilder.append(psi.getText)
        .append(insertNlsFromWs(psi))
    }
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
