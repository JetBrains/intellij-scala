package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.util.Base64
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetIncrementalEditorPrinter.QueuedPsi

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Dmitry Naydanov
 * Date: 1/15/14
 */
object WorksheetSourceProcessor {
  val END_TOKEN_MARKER = "###worksheet###$$end$$"
  val END_OUTPUT_MARKER = "###worksheet###$$end$$!@#$%^&*(("
  val END_GENERATED_MARKER = "/* ###worksheet### generated $$end$$ */ "

  val WORKSHEET_PRE_CLASS_KEY = new Key[String]("WorksheetPreClassKey")
  
  val REPL_DELIMITER = "\n$\n$\n"

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
  
  def extractLineInfoFrom(encoded: String): Option[(Int, Int)] = {
    if (encoded startsWith END_TOKEN_MARKER) { 
      val nums = encoded stripPrefix END_TOKEN_MARKER stripSuffix "\n" split '|'
      if (nums.length == 2) {
        try {
          val (a, b) = (Integer parseInt nums(0), Integer parseInt nums(1))
          if (a > -1 && b > -1) Some((a,b)) else None
        } catch {
          case _: NumberFormatException => None
        }
      } else None
    } else None
  }

  /**
    * @return (Code, Main class name)
    */
  def process(srcFile: ScalaFile, ifEditor: Option[Editor], iNum: Int, isRepl: Boolean = false): Either[(String, String), PsiErrorElement] = 
    if (!isRepl) processDefaultInner(srcFile, ifEditor.map(_.getDocument), iNum) else processIncrementalInner(srcFile, ifEditor)
  
  
  def processIncrementalInner(srcFile: ScalaFile, ifEditor: Option[Editor]): Either[(String, String), PsiErrorElement] = {
    val exprsPsi = mutable.ListBuffer[QueuedPsi]()
    val lastProcessed = ifEditor.flatMap(
      e => WorksheetCache.getInstance(srcFile.getProject).getLastProcessedIncremental(e))

    val glue = new WorksheetPsiGlue(exprsPsi)
    new WorksheetInterpretExprsIterator(srcFile, ifEditor, lastProcessed).collectAll(
      glue.processPsi, Some(e => return Right(e)))
    
    val texts = exprsPsi.map(_.getText)
    val allExprs = if (lastProcessed.isEmpty) ":reset" +: texts else texts
    
    Left((Base64.encode((allExprs mkString REPL_DELIMITER).getBytes), ""))
  }
  
  /**
   * @return (Code, Main class name)
   */
  def processDefaultInner(srcFile: ScalaFile, ifDoc: Option[Document], iterNumber: Int = 0): Either[(String, String), PsiErrorElement] = {
    if (!srcFile.isWorksheetFile) return Right(null)
    
    val name = s"A$$A$iterNumber"
    val instanceName = s"inst$$A$$A"
    val project = srcFile.getProject
    val moduleOpt = Option(WorksheetCommonSettings.getInstance(srcFile).getModuleFor)
    
    val packOpt: Option[String] = Option(srcFile.getContainingDirectory) flatMap (
      dir => Option(JavaDirectoryService.getInstance().getPackage(dir))
      ) collect {
      case psiPackage: PsiPackage if !psiPackage.getQualifiedName.trim.isEmpty =>
        psiPackage.getQualifiedName
    }

    val packStmt = packOpt map ("package " + _ + " ; ") getOrElse ""

    @inline def withCompilerVersion[T](if210: =>T, if211: => T, ifDotty: => T, dflt: =>T) = moduleOpt flatMap {
      module => if (project.hasDotty) Option(ifDotty) else module.scalaSdk.flatMap(_.compilerVersion).collect {
        case v if v.startsWith("2.10") => if210
        case v if v.startsWith("2.11") => if211
      }
    } getOrElse dflt

    val macroPrinterName = withCompilerVersion("MacroPrinter210", "MacroPrinter211", "", "MacroPrinter")
    val classPrologue = name
    val objectPrologue = s"$packStmt ${if (project.hasDotty) "" else s" import _root_.org.jetbrains.plugins.scala.worksheet.$macroPrinterName\n\n"} object $name { \n"

    val classRes = new StringBuilder(s"final class $classPrologue { \n")
    val objectRes = new StringBuilder(s"def main($runPrinterName: java.io.PrintStream) ${withCompilerVersion("", " : Unit = ", " : Unit = ", " : Unit = ")} { \n val $instanceName = new $name \n")
    
    val mySourceBuilder = if (moduleOpt exists (_.hasDotty)) new DottySourceBuilder(classRes, objectRes, iterNumber, srcFile,
      moduleOpt, ifDoc, macroPrinterName, packOpt, objectPrologue)
    else new ScalaSourceBuilder(classRes, objectRes, iterNumber, srcFile,
      moduleOpt, ifDoc, macroPrinterName, packOpt, objectPrologue)
    
    val preDeclarations = mutable.ListBuffer.empty[PsiElement]
    val postDeclarations = mutable.ListBuffer.empty[PsiElement]

    val root  = if (!isForObject(srcFile)) srcFile else {
      ((null: PsiElement) /: srcFile.getChildren) {
        case (a, imp: ScImportStmt) =>
          mySourceBuilder.processImport(imp)
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
      case null => srcFile.getChildren
      case other => other.getNode.getChildren(null) map (_.getPsi)
    }

    
    mySourceBuilder.process(rootChildren.toIterator, preDeclarations, postDeclarations)
  }
  
  private def isForObject(file: ScalaFile) = {
    val isEclipseMode = ScalaProjectSettings.getInstance(file.getProject).isUseEclipseCompatibility

    @tailrec
    def isObjectOk(psi: PsiElement): Boolean = psi match {
      case _: ScImportStmt | _: PsiWhiteSpace | _: PsiComment | _: ScPackaging => isObjectOk(psi.getNextSibling)
      case obj: ScObject => obj.extendsBlock.templateParents.isEmpty && isObjectOk(obj.getNextSibling)//isOk(psi.getNextSibling) - for compatibility with Eclipse. Its worksheet proceeds with expressions inside first object found
      case _: PsiClass if isEclipseMode => isObjectOk(psi.getNextSibling)
      case null => true
      case _ => false
    }
    
    isObjectOk(file.getFirstChild)
  }
  
  private abstract class SourceBuilderBase(classBuilder: mutable.StringBuilder, objectBuilder: mutable.StringBuilder, iterNumber: Int, srcFile: ScalaFile,
                                           moduleOpt: Option[Module], ifDoc: Option[Document], tpePrinterName: String, 
                                           packOpt: Option[String], objectPrologue: String) {
    protected val documentOpt: Option[Document] = ifDoc
    protected val name = s"A$$A$iterNumber"
    protected val tempVarName = "$$temp$$"
    protected val instanceName = s"inst$$A$$A"
    
    protected val eraseClassName: String = ".replace(\"" + instanceName + ".\", \"\")"
    protected val erasePrefixName: String = ".stripPrefix(\"" + name + "$" + name + "$\")"
    protected val plusInfoDef = " + "
    
    protected var assignCount = 0
    protected var resCount = 0
    protected val importStmts: ArrayBuffer[String] = mutable.ArrayBuffer[String]()
    protected val importsProcessed: mutable.HashSet[ScImportStmt] = mutable.HashSet[ScImportStmt]()
    
    protected def getTypePrinterName: String = tpePrinterName
    
    protected def prettyPrintType(tpeString: String): String = ": \" + " + withTempVar(tpeString)
    
    protected def logError(psiElement: PsiElement, message: Option[String] = None) {
      def writeLog(ms: String) {}
      
      message match {
        case Some(msg) => writeLog(s"$msg ${if (psiElement != null) s"${psiElement.getText}  ${psiElement.getClass}" else "null" }  ")
        case None if psiElement == null =>  writeLog("PsiElement is null")
        case _ => writeLog("Unknown element: " + psiElement)
      }
    }

    protected def getTempVarInfo: String = getTypePrinterName + ".printDefInfo($$temp$$)"

    protected def getImportInfoString(imp: ScImportStmt): String = {
      val text = imp.getText
      s"$getTypePrinterName.printImportInfo({$text;})"
    }

    protected def getFunDefInfoString(fun: ScFunction): String = {
      val hadMods = fun.getModifierList.accessModifier map (_.modifierFormattedText) getOrElse ""
      getTypePrinterName + s".printGeneric({import $instanceName._ ;" + fun.getText.stripPrefix(hadMods) + " })" + eraseClassName
    }

    protected def getStartText = ""

    protected def getObjectPrologue: String = objectPrologue


    protected def getPrintMethodName: String = genericPrintMethodName
    

    protected def processTypeAlias(tpe: ScTypeAlias) {
      withPrecomputeLines(tpe, {
        objectBuilder append withPrint(s"defined type alias ${tpe.name}")
      } )
    }

    protected def processFunDef(fun: ScFunction) {
      withPrecomputeLines(fun, {
        objectBuilder append (getPrintMethodName + "(\"" + fun.getName + ": \" + " + getFunDefInfoString(fun) + ")\n")
      })
    }

    protected def processTypeDef(tpeDef: ScTypeDefinition) {
      withPrecomputeLines(tpeDef, {
        val keyword = tpeDef match {
          case _: ScClass => "class"
          case _: ScTrait => "trait"
          case _ => "module"
        }

        objectBuilder append withPrint(s"defined $keyword ${tpeDef.name}")
      })
    }

    protected def processValDef(valDef: ScPatternDefinition) {
      withPrecomputeLines(valDef, {
        valDef.bindings foreach {
          p =>
            val pName = p.name
            val defName = variableInstanceName(pName)

            classBuilder append s"def $defName = $pName;$END_GENERATED_MARKER"
            
            objectBuilder append (getPrintMethodName + "(\"" + getStartText + pName + prettyPrintType(defName) + ")\n")
        }
      })
    }

    protected def processVarDef(varDef: ScVariableDefinition) {
      def writeTypedPatter(p: ScTypedPattern) = 
        p.typePattern map (typed => p.name + ":" + typed.typeElement.getText) getOrElse p.name

      def typeElement2Types(te: ScTypeElement) = te match {
        case tpl: ScTupleTypeElement => tpl.components
        case other => Seq(other)
      }
      
      def withOptionalBraces(s: Iterable[String]) = if (s.size == 1) s.head else s.mkString("(", ",", ")")

      val lineNum = psiToLineNumbers(varDef)

      val txt = (varDef.typeElement, varDef.expr) match {
        case (Some(tpl: ScTypeElement), Some(expr)) => 
          val names = withOptionalBraces {
            typeElement2Types(tpl) zip varDef.declaredElements map {
              case (tpe, el) => el.name + ": " + tpe.getText
            }
          }
          
          "var " + names  + " = { " + expr.getText + ";}"
        case (_, Some(expr)) =>
          val names = withOptionalBraces {
            varDef.declaredElements.map {
              case tpePattern: ScTypedPattern => writeTypedPatter(tpePattern)
              case a => a.name
            }
          }
          
          "var " + names + " = { " + expr.getText + ";}"
        case _ => varDef.getText
      }

      classBuilder append txt append ";"
      varDef.declaredNames foreach {
        pName =>
          objectBuilder append (
            getPrintMethodName + "(\"" + getStartText + pName + prettyPrintType(pName /*, withInstance = false*/) + ")\n"
          )
      }

      appendPsiLineInfo(varDef, lineNum)
    }

    protected def processAssign(assign: ScAssignStmt){
      val pName = assign.getLExpression.getText
      val lineNums = psiToLineNumbers(assign)
      val defName = s"`get$$$$instance_$assignCount$$$$$pName`"

      classBuilder append s"def $defName = { $END_GENERATED_MARKER${assign.getText}}${insertNlsFromWs(assign)}"
      objectBuilder append s"$instanceName.$defName; " append (getPrintMethodName + "(\"" + getStartText + pName + prettyPrintType(pName) + ")\n")

      appendPsiLineInfo(assign, lineNums)

      assignCount += 1
    }

    protected def processLocalImport(imp: ScImportStmt): Boolean = {
      if (imp.importExprs.lengthCompare(1) < 0) return false

      var currentQual = imp.importExprs.head.qualifier
      var lastFound: Option[(ScStableCodeReferenceElement, PsiElement)] = None

      while (currentQual != null) {
        currentQual.resolve() match {
          case el: PsiElement if el.getContainingFile == srcFile => lastFound = Some(currentQual, el)
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

          objectBuilder append
            s";{val $qualifierName = $instanceName.$memberName; $getPrintMethodName(${getImportInfoString(imp)})}\n"
          classBuilder append s"${imp.getText}${insertNlsFromWs(imp)}"

          appendPsiLineInfo(imp, lineNums)
          true
      }
    }

    def processImport(imp: ScImportStmt) {
      if (importsProcessed contains imp) return

      val lineNums = psiToLineNumbers(imp)

      objectBuilder append s"$getPrintMethodName(${getImportInfoString(imp)})\n"

      importStmts += (imp.getText + insertNlsFromWs(imp))
      appendPsiLineInfo(imp, lineNums)
      importsProcessed += imp
    }

    protected def processComment(comment: PsiComment) {
      val range = comment.getTextRange
      
      @scala.annotation.tailrec
      def getBackOffset(from: PsiElement): Int = {
        if (from == null) 1 else from.getPrevSibling match {
          case ws: PsiWhiteSpace if countNls(ws.getText) > 0 => 0
          case null => getBackOffset(from.getParent)
          case _ => 1
        }
      }

      val backOffset = getBackOffset(comment)
      
      documentOpt match {
        case Some(document) => 
          val differ = document.getLineNumber(range.getEndOffset) - document.getLineNumber(range.getStartOffset) + (1 - backOffset)
          for (_ <- 0 until differ) objectBuilder append getPrintMethodName append "()\n"
        case _ =>
          val count = countNls(comment.getText) - backOffset
          for (_ <- 0 until count) objectBuilder append getPrintMethodName append "()\n"
      }
    }

    protected def appendCommentToClass(comment: PsiComment) {
      val range = comment.getTextRange
      if (comment.getNode.getElementType != ScalaTokenTypes.tLINE_COMMENT) return

      val count = documentOpt map (
        d => d.getLineNumber(range.getEndOffset) - d.getLineNumber(range.getStartOffset) + 1) getOrElse countNls(comment.getText)

      for (_ <- 0 until count) classBuilder append "//\n"
      classBuilder append insertNlsFromWs(comment).stripPrefix("\n")
    }

    protected def processOtherExpr(expr: ScExpression) {
      val resName = s"get$$$$instance$$$$res$resCount"
      val lineNums = psiToLineNumbers(expr)

      classBuilder append s"def $resName = $END_GENERATED_MARKER${expr.getText}${insertNlsFromWs(expr)}"
      objectBuilder append (getPrintMethodName + "(\"res" + getStartText + resCount + prettyPrintType(resName) + ")\n")
      appendPsiLineInfo(expr, lineNums)

      resCount += 1
    }

    protected def processWhiteSpace(ws: PsiElement) {
      val count = countNls(ws.getText)
      for (_ <- 1 until count) objectBuilder append getPrintMethodName append "()\n"
    }


    protected def insertUntouched(exprs: Iterable[PsiElement]) {
      exprs foreach (expr => classBuilder append expr.getText append insertNlsFromWs(expr))
    }
    
    protected def processUnknownElement(element: PsiElement) {
      logError(element)
    }

    
    def process(elements: Iterator[PsiElement], preDeclarations: Iterable[PsiElement], 
                postDeclarations: Iterable[PsiElement]): Either[(String, String), PsiErrorElement] = {
      insertUntouched(preDeclarations)
      
      for (e <- elements) e match {
        case tpe: ScTypeAlias => processTypeAlias(tpe)
        case fun: ScFunction => processFunDef(fun)
        case tpeDef: ScTypeDefinition => processTypeDef(tpeDef)
        case valDef: ScPatternDefinition => processValDef(valDef)
        case varDef: ScVariableDefinition => processVarDef(varDef)
        case assign: ScAssignStmt => processAssign(assign)
        case imp: ScImportStmt => if (!processLocalImport(imp)) processImport(imp)
        case comment: PsiComment => 
          processComment(comment)
          appendCommentToClass(comment)
        case pack: ScPackaging => processWhiteSpace(pack)
        case otherExpr: ScExpression => processOtherExpr(otherExpr)
        case ws: PsiWhiteSpace => processWhiteSpace(ws)
        case error: PsiErrorElement => return Right(error)
        case null => logError(null)
        case unknown => processUnknownElement(unknown)
      }

      insertUntouched(postDeclarations)

      classBuilder append "}"
      objectBuilder append (getPrintMethodName + "(\"" + END_OUTPUT_MARKER + "\")\n") append s"} \n $PRINT_ARRAY_TEXT \n }"
      
      val codeResult = getObjectPrologue + importStmts.mkString(";") + classBuilder.toString() + "\n\n\n" + objectBuilder.toString()
      Left(
        (codeResult, packOpt.map(_ + ".").getOrElse("") + name)
      )
    }
    
    
    //kinda utils stuff that shouldn't be overridden
    
    @inline final def withTempVar(callee: String, withInstance: Boolean = true): String =
      s"{val $tempVarName = " + (if (withInstance) instanceName + "." else "") + callee + " ; " + getTempVarInfo +
        eraseClassName + plusInfoDef + "\" = \" + ( " + PRINT_ARRAY_NAME + s"($tempVarName) )" + erasePrefixName + "}"

    @inline final def withPrint(text: String): String = getPrintMethodName + "(\"" + getStartText + text + "\")\n"
    
    @inline final def withPrecomputeLines(psi: ScalaPsiElement, body: => Unit) {
      val lineNum = psiToLineNumbers(psi)
      body
      appendAll(psi, lineNum)
    }

    @inline final def appendAll(psi: ScalaPsiElement, numberStr: Option[String] = None) {
      appendDeclaration(psi)
      appendPsiLineInfo(psi, numberStr)
    }

    @inline final def variableInstanceName(name: String): String = if (name startsWith "`") s"`get$$$$instance$$$$${name.stripPrefix("`")}" else s"get$$$$instance$$$$$name"

    @inline final def countNls(str: String): Int = str.count(_ == '\n')

    @inline final def insertNlsFromWs(psi: PsiElement): String = psi.getNextSibling match {
      case ws: PsiWhiteSpace =>
        val c = countNls(ws.getText)
        if (c == 0) ";" else StringUtil.repeat("\n", c)
      case _ => ";"
    }

    @inline final def psiToLineNumbers(psi: PsiElement): Option[String] = documentOpt map {
      document =>
        var actualPsi = psi

        actualPsi.getFirstChild match {
          case _: PsiComment =>
            @tailrec
            def iter(wsOrComment: PsiElement): PsiElement = {
              wsOrComment match {
                case comment: PsiComment =>
                  processComment(comment)
                  iter(comment.getNextSibling)
                case ws: PsiWhiteSpace =>
                  processWhiteSpace(ws) 
                  iter(ws.getNextSibling)
                case a: PsiElement if a.getTextRange.isEmpty => iter(a.getNextSibling)
                case a: PsiElement => a
                case _ => psi
              }
            }

            actualPsi = iter(actualPsi.getFirstChild)
          case _ =>
        }


        val start = actualPsi.getTextRange.getStartOffset //actualPsi for start and psi for end - it is intentional
      val end = psi.getTextRange.getEndOffset
        s"${document getLineNumber start}|${document getLineNumber end}"
    }

    @inline final def appendPsiLineInfo(psi: PsiElement, numberStr: Option[String] = None) {
      val lineNumbers = numberStr getOrElse psiToLineNumbers(psi)

      objectBuilder append getPrintMethodName append "(\"" append END_TOKEN_MARKER append lineNumbers append "\")\n"
    }

    @inline final def appendDeclaration(psi: ScalaPsiElement) {
      val txt = psi match {
        case valDef: ScPatternDefinition if !valDef.getModifierList.has(ScalaTokenTypes.kLAZY) =>
          "lazy " + valDef.getText
        case a => a.getText
      }

      classBuilder append txt append insertNlsFromWs(psi)
    }
  }
  
  private class ScalaSourceBuilder(classBuilder: mutable.StringBuilder, objectBuilder: mutable.StringBuilder, iterNumber: Int, srcFile: ScalaFile,
                                   moduleOpt: Option[Module], ifDoc: Option[Document], tpePrinterName: String,
                                   packOpt: Option[String], objectPrologue: String) 
    extends SourceBuilderBase(classBuilder, objectBuilder, iterNumber, srcFile, moduleOpt, ifDoc, tpePrinterName, packOpt, objectPrologue) {
  }

  private class DottySourceBuilder(classBuilder: mutable.StringBuilder, objectBuilder: mutable.StringBuilder, iterNumber: Int, srcFile: ScalaFile,
                                   moduleOpt: Option[Module], ifDoc: Option[Document], tpePrinterName: String,
                                   packOpt: Option[String], objectPrologue: String)
    extends SourceBuilderBase(classBuilder, objectBuilder, iterNumber, srcFile, moduleOpt, ifDoc, tpePrinterName, packOpt, objectPrologue) {
    override protected val eraseClassName: String = ""
    override protected val erasePrefixName: String = ""
    override protected val plusInfoDef: String = ""

    override protected def prettyPrintType(tpeString: String): String = "\" + " + withTempVar(tpeString)

    override protected def getPrintMethodName: String = "println" // s"$runPrinterName.println"

    override protected def getTypePrinterName: String = ""

    override protected def getTempVarInfo: String = ""

    override protected def getImportInfoString(imp: ScImportStmt): String = ""

    override protected def getFunDefInfoString(fun: ScFunction): String = ""
  }
}
