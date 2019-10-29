package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.util.Base64
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, StringExt, inReadAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil.accessModifierText
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object WorksheetSourceProcessor {

  // TODO: it is probably enough just START_TOKEN_MARKER, without END_TOKEN_MARKER, leave just one
  //  BUT FIRST: finish covering worksheets with tests, due to rendering logic is quite complicated
  val START_TOKEN_MARKER   = "###worksheet###$$start$$"
  val END_TOKEN_MARKER     = "###worksheet###$$end$$"
  val END_OUTPUT_MARKER    = "###worksheet###$$end$$!@#$%^&*(("
  val END_GENERATED_MARKER = "/* ###worksheet### generated $$end$$ */ "

  private val WORKSHEET_PRE_CLASS_KEY = new Key[String]("WorksheetPreClassKey")

  private val REPL_DELIMITER = "\n$\n$\n"

  private val PRINT_ARRAY_NAME = "print$$$Worksheet$$$Array$$$"
  private val runPrinterName = "worksheet$$run$$printer"

  private val PRINT_ARRAY_TEXT =
    s"""
      |def $PRINT_ARRAY_NAME(an: Any): String = {
      |  an match {
      |    case arr: Array[_] =>
      |      val a = scala.collection.mutable.WrappedArray.make(arr)
      |      a.toString.stripPrefix("Wrapped")
      |    case null => "null"
      |    case other => other.toString
      |  }}
    """.stripMargin

  private val genericPrintMethodName = "println"

  def extractLineInfoFromEnd(encoded: String): Option[(Int, Int)] =
    extractLineInfoFrom(encoded, END_TOKEN_MARKER)

  def extractLineInfoFromStart(encoded: String): Option[(Int, Int)] =
    extractLineInfoFrom(encoded, START_TOKEN_MARKER)

  private def extractLineInfoFrom(encoded: String, marker: String): Option[(Int, Int)] = {
    if (encoded.startsWith(marker)) {
      val startWithEnd = encoded.stripPrefix(marker).stripSuffix("\n").split('|')
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

  def processIncremental(srcFile: ScalaFile, editor: Editor): Either[PsiErrorElement, String] = {
    val lastProcessed = WorksheetCache.getInstance(srcFile.getProject).getLastProcessedIncremental(editor)

    val glue = WorksheetPsiGlue()
    val iterator = new WorksheetInterpretExprsIterator(srcFile, Some(editor), lastProcessed)
    iterator.collectAll(x => inReadAction(glue.processPsi(x)), Some(e => return Left(e)))
    val elements = glue.result

    val texts = elements.map(_.getText)
    val allExprs = if (lastProcessed.isEmpty) ":reset" +: texts else texts

    val code = allExprs.mkString(REPL_DELIMITER)
    Right(Base64.encode(code.getBytes))
  }

  /**
   * @return (Code, Main class name)
   */
  def processDefault(srcFile: ScalaFile, document: Document): Either[PsiErrorElement, (String, String)] = {
    if (!srcFile.isWorksheetFile) return Left(null)

    val iterNumber = WorksheetCache.getInstance(srcFile.getProject)
      .peakCompilationIteration(srcFile.getViewProvider.getVirtualFile.getCanonicalPath) + 1

    val name = s"A$$A$iterNumber"
    val instanceName = s"inst$$A$$A"
    val moduleOpt = Option(WorksheetCommonSettings(srcFile).getModuleFor)

    val packOpt: Option[String] = for {
      dir         <- srcFile.getContainingDirectory.toOption
      psiPackage  <- JavaDirectoryService.getInstance().getPackage(dir).toOption
      packageName = psiPackage.getQualifiedName
      if !packageName.trim.isEmpty
    } yield packageName

    val packStmt = packOpt.map("package " + _ + " ; ").getOrElse("")

    @inline
    def withCompilerVersion[T](if210: => T, if211: => T, if213: => T, default: => T) =
      moduleOpt.flatMap(_.scalaLanguageLevel).collect {
        case ScalaLanguageLevel.Scala_2_10 => if210
        case ScalaLanguageLevel.Scala_2_11 => if211
        case ScalaLanguageLevel.Scala_2_13 => if213
      }.getOrElse(default)

    val macroPrinterName = withCompilerVersion("MacroPrinter210", "MacroPrinter211", "MacroPrinter213", "MacroPrinter")
    val classPrologue = name
    val objectPrologue =
      s"""$packStmt import _root_.org.jetbrains.plugins.scala.worksheet.$macroPrinterName
         |
         |object $name {
         |""".stripMargin

    val classRes = new StringBuilder(s"final class $classPrologue { \n")
    val unitReturnType = " : Unit = "

    val returnType = withCompilerVersion("", unitReturnType, unitReturnType, unitReturnType)
    val objectRes = new StringBuilder(
      s"""def main($runPrinterName: java.io.PrintStream) $returnType {
         |  val $instanceName = new $name
         |  """.stripMargin
    )

    val sourceBuilder = new ScalaSourceBuilder(
      classRes, objectRes, iterNumber, srcFile,
      moduleOpt, document, macroPrinterName, packOpt, objectPrologue
    )

    val preDeclarations = mutable.ListBuffer.empty[PsiElement]
    val postDeclarations = mutable.ListBuffer.empty[PsiElement]

    val root: PsiElement = if (!isForObject(srcFile)) srcFile else {
      ((null: PsiElement) /: srcFile.getChildren) {
        case (a, imp: ScImportStmt) =>
          sourceBuilder.processImport(imp)
          a
        case (null, obj: ScObject) =>
          obj.putCopyableUserData(WORKSHEET_PRE_CLASS_KEY, "+")
          obj.extendsBlock.templateBody getOrElse srcFile
        case (null, cl: ScTemplateDefinition) =>
          cl.putCopyableUserData(WORKSHEET_PRE_CLASS_KEY, "+")
          preDeclarations += cl
          null
        case (a: PsiElement, cl: ScTemplateDefinition) =>
          postDeclarations += cl
          a
        case (a, _) => a
      }
    }

    val rootChildren = root match {
      case file: PsiFile => file.getChildren
      case null          => srcFile.getChildren
      case other         => other.getNode.getChildren(null).map(_.getPsi)
    }

    sourceBuilder.process(rootChildren.toIterator, preDeclarations, postDeclarations)
  }

  private def isForObject(file: ScalaFile): Boolean = {
    val isEclipseMode = ScalaProjectSettings.getInstance(file.getProject).isUseEclipseCompatibility

    @tailrec
    def isObjectOk(psi: PsiElement): Boolean = psi match {
      case _: ScImportStmt | _: PsiWhiteSpace | _: PsiComment | _: ScPackaging =>
        isObjectOk(psi.getNextSibling)
      case obj: ScObject =>
        //isOk(psi.getNextSibling) - for compatibility with Eclipse. Its worksheet proceeds with expressions inside first object found
        obj.extendsBlock.templateParents.isEmpty && isObjectOk(obj.getNextSibling)
      case _: PsiClass if isEclipseMode =>
        isObjectOk(psi.getNextSibling)
      case null => true
      case _ => false
    }

    isObjectOk(file.getFirstChild)
  }

  private abstract class SourceBuilderBase(classBuilder: mutable.StringBuilder,
                                           objectBuilder: mutable.StringBuilder,
                                           iterNumber: Int, srcFile: ScalaFile,
                                           moduleOpt: Option[Module],
                                           document: Document,
                                           tpePrinterName: String,
                                           packageOpt: Option[String],
                                           objectPrologue: String) {
    protected val name = s"A$$A$iterNumber"
    protected val tempVarName = "$$temp$$"
    protected val instanceName = s"inst$$A$$A"

    protected val eraseClassName: String = s""".replace("$instanceName.", "")"""
    protected val erasePrefixName: String = s""".stripPrefix("$name$$$name$$")"""
    protected val plusInfoDef = " + "

    protected var assignCount = 0
    protected var resCount = 0
    protected val importStmts: ArrayBuffer[String] = mutable.ArrayBuffer[String]()
    protected val importsProcessed: mutable.HashSet[ScImportStmt] = mutable.HashSet[ScImportStmt]()

    protected def getTypePrinterName: String = tpePrinterName

    protected def prettyPrintType(tpeString: String): String = s""": " + ${withTempVar(tpeString)}"""

    // FIXME: it does nothing now
    protected def logError(psiElement: PsiElement, message: Option[String] = None): Unit = {
      def writeLog(ms: String): Unit = {}

      val logMessage = message match {
        case Some(msg) if psiElement != null => s"$msg ${psiElement.getText} ${psiElement.getClass}"
        case Some(msg)                       => s"$msg null"
        case None if psiElement == null      => "PsiElement is null"
        case _                               => s"Unknown element: $psiElement"
      }
      writeLog(logMessage)
    }

    protected def getTempVarInfo: String = s"$getTypePrinterName.printDefInfo($$$$temp$$$$)"

    protected def getImportInfoString(imp: ScImportStmt): String = {
      val text = imp.getText
      s"$getTypePrinterName.printImportInfo({$text;})"
    }

    protected def getFunDefInfoString(fun: ScFunction): String = {
      val accessModifier = fun.getModifierList.accessModifier
      val hadMods = accessModifier.map(accessModifierText).getOrElse("")
      getTypePrinterName + s".printGeneric({import $instanceName._ ;" + fun.getText.stripPrefix(hadMods) + " })" + eraseClassName
    }

    protected def getObjectPrologue: String = objectPrologue

    protected def getPrintMethodName: String = genericPrintMethodName

    protected def processTypeAlias(tpe: ScTypeAlias): Unit =
      withPrecomputedLines(tpe) {
        objectBuilder.append(withPrint(s"defined type alias ${tpe.name}"))
      }

    protected def processFunDef(fun: ScFunction): Unit =
      withPrecomputedLines(fun) {
        objectBuilder.append(s"""$getPrintMethodName("${fun.getName}: " + ${getFunDefInfoString(fun)})""").append("\n")
      }

    protected def processTypeDef(tpeDef: ScTypeDefinition): Unit =
      withPrecomputedLines(tpeDef) {
        val keyword = tpeDef match {
          case _: ScClass => "class"
          case _: ScTrait => "trait"
          case _ => "module" // TODO: change to `object`
        }

        objectBuilder.append(withPrint(s"defined $keyword ${tpeDef.name}"))
      }

    protected def processValDef(valDef: ScPatternDefinition): Unit =
      withPrecomputedLines(valDef) {
        valDef.bindings.foreach { binding =>
          val pName = binding.name
          val defName = variableInstanceName(pName)

          classBuilder.append(s"def $defName = $pName;$END_GENERATED_MARKER")

          objectBuilder.append(s"""$getPrintMethodName("$pName${prettyPrintType(defName)})""").append("\n")
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
        objectBuilder.append(s"""$getPrintMethodName("$pName${prettyPrintType(pName)})""").append("\n")
      }

      appendEndPsiLineInfo(lineNum)
    }

    protected def processAssign(assign: ScAssignment): Unit = {
      val pName = assign.leftExpression.getText
      val lineNums = psiToLineNumbers(assign)
      val defName = s"`get$$$$instance_$assignCount$$$$$pName`"

      appendStartPsiLineInfo(lineNums)

      classBuilder.append(s"""def $defName = { $END_GENERATED_MARKER${assign.getText}}${insertNlsFromWs(assign)}""")
      objectBuilder.append(s"""$instanceName.$defName; """)
      objectBuilder.append(s"""$getPrintMethodName("$pName${prettyPrintType(pName)})""").append("\n")

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

          objectBuilder.append(s";{val $qualifierName = $instanceName.$memberName; $getPrintMethodName(${getImportInfoString(imp)})}").append("\n")
          classBuilder.append(s"${imp.getText}${insertNlsFromWs(imp)}")

          appendEndPsiLineInfo(lineNums)
          true
      }
    }

    def processImport(imp: ScImportStmt): Unit = {
      if (importsProcessed contains imp) return

      val lineNums = psiToLineNumbers(imp)

      appendStartPsiLineInfo(lineNums)

      objectBuilder.append(s"$getPrintMethodName(${getImportInfoString(imp)})").append("\n")

      importStmts += (imp.getText + insertNlsFromWs(imp))
      appendEndPsiLineInfo(lineNums)
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
      objectBuilder.append(s"""$getPrintMethodName("res$resCount${prettyPrintType(resName)})""").append("\n")
      appendEndPsiLineInfo(lineNums)

      resCount += 1
    }

    protected def insertUntouched(exprs: Iterable[PsiElement]): Unit =
      exprs.foreach(expr => classBuilder.append(expr.getText).append(insertNlsFromWs(expr)))

    protected def processUnknownElement(element: PsiElement): Unit =
      logError(element)

    def process(elements: Iterator[PsiElement],
                preDeclarations: Iterable[PsiElement],
                postDeclarations: Iterable[PsiElement]): Either[PsiErrorElement, (String, String)] = {
      insertUntouched(preDeclarations)

      for (e <- elements) e match {
        case tpe: ScTypeAlias             => processTypeAlias(tpe)
        case fun: ScFunction              => processFunDef(fun)
        case tpeDef: ScTypeDefinition     => processTypeDef(tpeDef)
        case valDef: ScPatternDefinition  => processValDef(valDef)
        case varDef: ScVariableDefinition => processVarDef(varDef)
        case assign: ScAssignment         => processAssign(assign)
        case imp: ScImportStmt            => if (!processLocalImport(imp)) processImport(imp)
        case comment: PsiComment          =>
          appendCommentToClass(comment)
        case _: ScPackaging               => //skip
        case otherExpr: ScExpression      => processOtherExpr(otherExpr)
        case _: PsiWhiteSpace             => //skip
        case error: PsiErrorElement       => return Left(error)
        case null                         => logError(null)
        case unknown                      => processUnknownElement(unknown)
      }

      insertUntouched(postDeclarations)

      classBuilder.append("}")
      objectBuilder.append(
        s"""$getPrintMethodName("$END_OUTPUT_MARKER")
           |}
           |$PRINT_ARRAY_TEXT
           |}""".stripMargin
      )

      val codeResult =
        s"""$getObjectPrologue${importStmts.mkString(";")}${classBuilder.toString()}
           |
           |
           |${objectBuilder.toString()}""".stripMargin
      val mainClassName = s"${packageOpt.fold("")(_ + ".")}$name"
      Right((codeResult, mainClassName))
    }

    //kinda utils stuff that shouldn't be overridden

    @inline final def withTempVar(callee: String, withInstance: Boolean = true): String = {
      val target = if (withInstance) instanceName + "." else ""
      s"""{val $tempVarName = $target$callee ; $getTempVarInfo$eraseClassName$plusInfoDef" = " + ( $PRINT_ARRAY_NAME($tempVarName) )$erasePrefixName}"""
    }

    @inline final def withPrint(text: String): String = s"""$getPrintMethodName("$text")""" + "\n"

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

    @inline final def insertNlsFromWs(psi: PsiElement): String = psi.getNextSibling match {
      case ws: PsiWhiteSpace =>
        val c = countNewLines(ws.getText)
        if (c == 0) ";"
        else StringUtil.repeat("\n", c)
      case _ => ";"
    }

    @inline final def psiToLineNumbers(psi: PsiElement): String = {
      var actualPsi = psi

      actualPsi.getFirstChild match {
        case _: PsiComment =>
          @tailrec
          def iter(wsOrComment: PsiElement): PsiElement = wsOrComment match {
            case ct: PsiComment                            => iter(ct.getNextSibling)
            case ws: PsiWhiteSpace                         => iter(ws.getNextSibling)
            case el: PsiElement if el.getTextRange.isEmpty => iter(el.getNextSibling)
            case a: PsiElement                             => a
            case _                                         => psi
          }

          actualPsi = iter(actualPsi.getFirstChild)
        case _ =>
      }


      val start = actualPsi.startOffset //actualPsi for start and psi for end - it is intentional
      val end = psi.endOffset
      s"${document.getLineNumber(start)}|${document.getLineNumber(end)}"
    }

    @inline final def appendStartPsiLineInfo(numberStr: String): Unit =
      objectBuilder.append(s"""$getPrintMethodName("$START_TOKEN_MARKER$numberStr")""").append("\n")

    @inline final def appendEndPsiLineInfo(numberStr: String): Unit =
      objectBuilder.append(s"""$getPrintMethodName("$END_TOKEN_MARKER$numberStr")""").append("\n")

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

  private def calcContentLines(document: Document, range: TextRange): Int =
    document.getLineNumber(range.getEndOffset) - document.getLineNumber(range.getStartOffset) + 1

  private class ScalaSourceBuilder(classBuilder: mutable.StringBuilder,
                                   objectBuilder: mutable.StringBuilder,
                                   iterNumber: Int, srcFile: ScalaFile,
                                   moduleOpt: Option[Module],
                                   document: Document,
                                   tpePrinterName: String,
                                   packageOpt: Option[String],
                                   objectPrologue: String)
    extends SourceBuilderBase(
      classBuilder, objectBuilder, iterNumber, srcFile,
      moduleOpt, document, tpePrinterName, packageOpt, objectPrologue
    )
}
