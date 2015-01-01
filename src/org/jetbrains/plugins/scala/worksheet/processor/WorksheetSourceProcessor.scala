package org.jetbrains.plugins.scala
package worksheet.processor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.actions.RunWorksheetAction
import org.jetbrains.plugins.scala.project._

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * User: Dmitry Naydanov
 * Date: 1/15/14
 */
object WorksheetSourceProcessor {
  val END_TOKEN_MARKER = "###worksheet###$$end$$"
  val END_OUTPUT_MARKER = "###worksheet###$$end$$!@#$%^&*(("
  val END_GENERATED_MARKER = "/* ###worksheet### generated $$end$$ */"

  val WORKSHEET_PRE_CLASS_KEY = new Key[String]("WorksheetPreClassKey")

  private val PRINT_ARRAY_NAME = "print$$$Worksheet$$$Array$$$"

  private val PRINT_ARRAY_TEXT =
    s"""
      |def $PRINT_ARRAY_NAME(an: Any): String = {
      |  an match {
      |    case arr: Array[_] => scala.collection.mutable.WrappedArray.make(arr).toString().stripPrefix("Wrapped")
      |    case null => "null"
      |    case other => other.toString
      |  }}
    """.stripMargin

  
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
  def process(srcFile: ScalaFile, ifEditor: Option[Editor], iterNumber: Int): Either[(String, String), PsiErrorElement] = {
    if (!srcFile.isWorksheetFile) return Right(null)
    
    val name = s"A$$A$iterNumber"
    val instanceName = s"inst$$A$$A"
    val packOpt = Option(srcFile.getContainingDirectory) flatMap {
      case dir => Option(JavaDirectoryService.getInstance().getPackage(dir))
    } collect {
      case psiPackage: PsiPackage if !psiPackage.getQualifiedName.trim.isEmpty =>
        psiPackage.getQualifiedName
    }

    val packStmt = packOpt map ("package " + _ + " ; ") getOrElse ""

    val importStmts = mutable.ArrayBuffer[String]()

    @inline def withCompilerVersion[T](if210: =>T, if211: => T, dflt: =>T) = Option(RunWorksheetAction getModuleFor srcFile) flatMap {
      case module => module.scalaSdk.flatMap(_.compilerVersion).collect {
        case v if v.startsWith("2.10") => if210
        case v if v.startsWith("2.11") => if211
      }
    } getOrElse dflt

    val macroPrinterName = withCompilerVersion("MacroPrinter210", "MacroPrinter211", "MacroPrinter")
    
    val runPrinterName = "worksheet$$run$$printer"

    val printMethodName = "println"

    val ifDocument = ifEditor map (_.getDocument)
    val classPrologue = name // s"$name ${if (iterNumber > 0) s"extends A${iterNumber - 1}" }" //todo disabled until I implement incremental code generation
    val objectPrologue = s"${packStmt}import _root_.org.jetbrains.plugins.scala.worksheet.$macroPrinterName\n\n object $name { \n"
    
    val startText = ""
    
    val classRes = new StringBuilder(s"final class $classPrologue { \n")
    val objectRes = new StringBuilder(s"def main($runPrinterName: Any) ${withCompilerVersion("", " : Unit = ", "")} { \n val $instanceName = new $name \n")
    
    var resCount = 0
    var assignCount = 0
    
    val eraseClassName = ".replace(\"" + instanceName + ".\", \"\")"
    val erasePrefixName = ".stripPrefix(\"" + name + "$" + name + "$\")"
    
    @inline def insertNlsFromWs(psi: PsiElement) = psi.getNextSibling match {
      case ws: PsiWhiteSpace =>
        val c = ws.getText count (_ == '\n')
        if (c == 0) ";" else StringUtil.repeat("\n", c)
      case _ => ";"
    }
    
    @inline def psiToLineNumbers(psi: PsiElement): Option[String] = ifDocument map {
      case document =>
        var actualPsi = psi
        
        actualPsi.getFirstChild match {
          case _: PsiComment =>
            @tailrec
            def iter(wsOrComment: PsiElement): PsiElement = {
              wsOrComment match {
                case comment: PsiComment => 
                  appendPsiComment(comment)
                  iter(comment.getNextSibling)
                case ws: PsiWhiteSpace =>
                  appendPsiWhitespace(ws)
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
    
    @inline def appendPsiLineInfo(psi: PsiElement, numberStr: Option[String] = None) {
      val lineNumbers = numberStr getOrElse psiToLineNumbers(psi)
      
      objectRes append printMethodName append "(\"" append END_TOKEN_MARKER append lineNumbers append "\")\n"
    }
    
    @inline def appendDeclaration(psi: ScalaPsiElement) {
      val txt = psi match {
        case valDef: ScPatternDefinition if !valDef.getModifierList.has(ScalaTokenTypes.kLAZY) =>
          "lazy " + valDef.getText
        case a => a.getText
      }

      classRes append txt append insertNlsFromWs(psi)
    }
    
    @inline def appendPsiComment(comment: PsiComment) {
      val range = comment.getTextRange
      ifDocument map {
        document => document.getLineNumber(range.getEndOffset) - document.getLineNumber(range.getStartOffset) + 1
      } map {
        case differ => for (_ <- 0 until differ) objectRes append printMethodName append "()\n"
      } getOrElse {
        val count = comment.getText count (_ == '\n')
        for (_ <- 0 until count) objectRes append printMethodName append "()\n"
      }
    }

    def appendCommentToClass(comment: PsiComment) {
      val range = comment.getTextRange
      if (comment.getNode.getElementType != ScalaTokenTypes.tLINE_COMMENT) return

      val count = ifDocument map {
        case d => d.getLineNumber(range.getEndOffset) - d.getLineNumber(range.getStartOffset) + 1
      } getOrElse comment.getText.count(_ == '\n')

      for (_ <- 0 until count) classRes append "//\n"
      classRes append insertNlsFromWs(comment).stripPrefix("\n")
    }
    
    @inline def appendPsiWhitespace(ws: PsiWhiteSpace) {
      val count = ws.getText count (_ == '\n')
      for (_ <- 1 until count) objectRes append printMethodName append "()\n"
    }
    
    @inline def appendAll(psi: ScalaPsiElement, numberStr: Option[String] = None) {
      appendDeclaration(psi)
      appendPsiLineInfo(psi, numberStr)
    }
    
    @inline def withPrint(text: String) = printMethodName + "(\"" + startText + text + "\")\n" 
    
    @inline def withPrecomputeLines(psi: ScalaPsiElement, body: => Unit) {
      val lineNum = psiToLineNumbers(psi)
      body
      appendAll(psi, lineNum)
    }
    
    @inline def processImport(imp: ScImportStmt) = {
      val text = imp.getText
      val lineNums = psiToLineNumbers(imp)

      objectRes append s"$printMethodName($macroPrinterName.printImportInfo({$text;}))\n"

      importStmts += (text + insertNlsFromWs(imp))
      appendPsiLineInfo(imp, lineNums)
    }

    def processLocalImport(imp: ScImportStmt): Boolean = {
      if (imp.importExprs.length < 1) return false

      var currentQual = imp.importExprs(0).qualifier
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
          val text = imp.getText
          val qualifierName = lastQualifier.qualName
          val lineNums = psiToLineNumbers(imp)
          val memberName = if (el.isInstanceOf[ScValue] || el.isInstanceOf[ScVariable]) //variable to avoid weird errors
            s"get$$$$instance$$$$$qualifierName" else qualifierName

          objectRes append
            s";{val $qualifierName = $instanceName.$memberName; $printMethodName($macroPrinterName.printImportInfo({$text;}))}\n"
          classRes append s"$text${insertNlsFromWs(imp)}"

          appendPsiLineInfo(imp, lineNums)
          true
      }
    }

    def withTempVar(callee: String, withInstance: Boolean = true) =
      "{val $$temp$$ = " + (if (withInstance) instanceName + "." else "") + callee + s"; $macroPrinterName.printDefInfo(" + "$$temp$$" + ")" +
        eraseClassName + " + \" = \" + ( " + PRINT_ARRAY_NAME + "($$temp$$) )" + erasePrefixName + "}"

    def insertUntouched(exprs: mutable.Iterable[PsiElement]) {
      exprs foreach {
        case expr => classRes append expr.getText append insertNlsFromWs(expr)
      }
    }

    val preDeclarations = mutable.ListBuffer.empty[PsiElement]
    val postDeclarations = mutable.ListBuffer.empty[PsiElement]

    val root  = if (!isForObject(srcFile)) srcFile else {
      ((null: PsiElement) /: srcFile.getChildren) {
        case (a, imp: ScImportStmt) => 
          processImport(imp)
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

    insertUntouched(preDeclarations)

    val rootChildren = root match {
      case file: PsiFile => file.getChildren
      case null => srcFile.getChildren
      case other => other.getNode.getChildren(null) map (_.getPsi)
    }

    rootChildren foreach {
      case tpe: ScTypeAlias =>
        withPrecomputeLines(tpe, {
          objectRes append withPrint(s"defined type alias ${tpe.name}")
        } )
      case fun: ScFunction =>
        val hadMods = fun.getModifierList.accessModifier map (_.modifierFormattedText) getOrElse ""

        withPrecomputeLines(fun, {
          objectRes append (printMethodName + "(\"" + fun.getName + ": \" + " + macroPrinterName +
            s".printGeneric({import $instanceName._ ;" + fun.getText.stripPrefix(hadMods) + " })" + eraseClassName + ")\n")
        })
      case tpeDef: ScTypeDefinition =>
        withPrecomputeLines(tpeDef, {
          val keyword = tpeDef match {
            case _: ScClass => "class"
            case _: ScTrait => "trait"
            case _ => "module"
          }

          objectRes append withPrint(s"defined $keyword ${tpeDef.name}")
        })
      case valDef: ScPatternDefinition =>
        withPrecomputeLines(valDef, {
          valDef.bindings foreach {
            case p =>
              val pName = p.name
              val defName = s"get$$$$instance$$$$$pName"

              classRes append s"def $defName = $pName;$END_GENERATED_MARKER"
              objectRes append (printMethodName + "(\"" + startText + pName + ": \" + " + withTempVar(defName) + ")\n")
          }
        })
      case varDef: ScVariableDefinition =>
        def writeTypedPatter(p: ScTypedPattern) = {
          p.typePattern map {
            case typed => p.name + ":" + typed.typeElement.getText
          } getOrElse p.name
        }

        def typeElement2Types(te: ScTypeElement) = te match {
          case tpl: ScTupleTypeElement => tpl.components
          case other => Seq(other)
        }

        val lineNum = psiToLineNumbers(varDef)

        val txt = (varDef.typeElement, varDef.expr) match {
          case (Some(tpl: ScTypeElement), Some(expr)) => "var " + (typeElement2Types(tpl) zip varDef.declaredElements map {
              case (tpe, el) => el.name + ": " + tpe.getText
            }).mkString("(", ",", ")")  + " = { " + expr.getText + ";}"
          case (_, Some(expr)) =>
            "var " + varDef.declaredElements.map {
              case tpePattern: ScTypedPattern => writeTypedPatter(tpePattern)
              case a => a.name
            }.mkString("(", ",", ")") + " = { " + expr.getText + ";}"
          case _ => varDef.getText
        }

        classRes append txt append ";"
        varDef.declaredNames foreach {
          case pName =>
            objectRes append (
              printMethodName + "(\"" + startText + pName + ": \" + " + withTempVar(pName /*, withInstance = false*/) + ")\n"
            )
        }

        appendPsiLineInfo(varDef, lineNum)
      case assign: ScAssignStmt if !assign.getLExpression.isInstanceOf[ScMethodCall] =>
        val pName = assign.getLExpression.getText
        val lineNums = psiToLineNumbers(assign)
        val defName = s"`get$$$$instance_$assignCount$$$$$pName`"
        
        classRes append s"def $defName = { $END_GENERATED_MARKER${assign.getText}}${insertNlsFromWs(assign)}"
        objectRes append s"$instanceName.$defName; " append (printMethodName + "(\"" + startText + pName + ": \" + " + 
          withTempVar(pName) + ")\n")

        appendPsiLineInfo(assign, lineNums)
        
        assignCount += 1
      case imp: ScImportStmt =>
        if (!processLocalImport(imp)) processImport(imp)
      case comm: PsiComment =>
        appendPsiComment(comm)
        appendCommentToClass(comm)
      case expr: ScExpression =>
        val resName = s"get$$$$instance$$$$res$resCount"
        val lineNums = psiToLineNumbers(expr)

        classRes append s"def $resName = $END_GENERATED_MARKER${expr.getText}${insertNlsFromWs(expr)}" 
        objectRes append (printMethodName + "(\"res" + startText + resCount + ": \" + " + withTempVar(resName) + ")\n")
        appendPsiLineInfo(expr, lineNums)
        
        resCount += 1
      case ws: PsiWhiteSpace => appendPsiWhitespace(ws)
      case error: PsiErrorElement => return Right(error)
      case a => 
    }

    insertUntouched(postDeclarations)

    classRes append "}"
    objectRes append (printMethodName + "(\"" + END_OUTPUT_MARKER + "\")\n") append s"} \n $PRINT_ARRAY_TEXT \n }"

    val codeResult = objectPrologue + importStmts.mkString(";") + classRes.toString() + "\n\n\n" + objectRes.toString()
    Left(
      (codeResult, packOpt.map(_ + ".").getOrElse("") + name)
    )
  }
  
  private def isForObject(file: ScalaFile) = {
    val isEclipseMode = ScalaProjectSettings.getInstance(file.getProject).isUseEclipseCompatibility

    @tailrec
    def isObjectOk(psi: PsiElement): Boolean = psi match {
      case _: ScImportStmt | _: PsiWhiteSpace | _: PsiComment  => isObjectOk(psi.getNextSibling)
      case obj: ScObject => obj.extendsBlock.templateParents.isEmpty && isObjectOk(obj.getNextSibling)//isOk(psi.getNextSibling) - for compatibility with Eclipse. Its worksheet proceeds with expressions inside first object found
      case _: PsiClass if isEclipseMode => isObjectOk(psi.getNextSibling)
      case null => true
      case _ => false
    }
    
    isObjectOk(file.getFirstChild)
  }
}
