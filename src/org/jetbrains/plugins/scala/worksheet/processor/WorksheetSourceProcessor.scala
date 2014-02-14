package org.jetbrains.plugins.scala
package worksheet.processor

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScFunction, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import com.intellij.openapi.util.text.StringUtil

/**
 * User: Dmitry Naydanov
 * Date: 1/15/14
 */
object WorksheetSourceProcessor {
  val END_TOKEN_MARKER = "###worksheet###$$end$$"
  val END_OUTPUT_MARKER = "###worksheet###$$end$$!@#$%^&*(("
  val END_GENERATED_MARKER = "/* ###worksheet### generated $$end$$ */"
  
  
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
  def process(srcFile: ScalaFile, ifEditor: Option[Editor], iterNumber: Int): Option[(String, String)] = {
    if (!srcFile.isWorksheetFile) return None
    
    val name = s"A$$A$iterNumber"
    val instanceName = s"inst$$A$$A"
    
    val macroPrinterName = "worksheet$$macro$$printer"
    val runPrinterName = "worksheet$$run$$printer"

    val printMethodName = "println"

    val ifDocument = ifEditor map (_.getDocument)
    val classPrologue = name // s"$name ${if (iterNumber > 0) s"extends A${iterNumber - 1}" }" //todo disabled until I implement incremental code generation
    val objectPrologue = s"import _root_.org.jetbrains.plugins.scala.worksheet.MacroPrinter\n\n object $name {val $macroPrinterName = MacroPrinter  \n"
    
    val startText = ""
    
    val classRes = new StringBuilder(s"class $classPrologue { \n")
    val objectRes = new StringBuilder(s"def main($runPrinterName: Any) { \n val $instanceName = new $name \n")
    
    var resCount = 0
    
    val eraseClassName = ".replace(\"" + instanceName + ".\", \"\")"
    val erasePrefixName = ".stripPrefix(\"" + name + "$" + name + "$\")"
    
    @inline def insertNlsFromWs(psi: PsiElement) = psi.getNextSibling match {
      case ws: PsiWhiteSpace =>
        val c = ws.getText count (_ == '\n')
        if (c == 0) ";" else StringUtil.repeat("\n", c)
      case _ => ";"
    }
    
    @inline def psiToLineNumbers(psi: PsiElement): Option[(Int, Int)] = ifDocument map {
      case document =>
        val range = psi.getTextRange
        val start = range.getStartOffset
        val end = range.getEndOffset
        (document getLineNumber start, document getLineNumber end)
    }
    
    @inline def appendPsiLineInfo(psi: PsiElement) {
      objectRes append printMethodName append "(\"" append END_TOKEN_MARKER append (psiToLineNumbers(psi) map {
        case (a, b) => s"$a|$b"
      } getOrElse "") append "\")\n"
    }
    
    @inline def appendDeclaration(psi: ScalaPsiElement) {
      classRes append psi.getText append insertNlsFromWs(psi)
    }
    
    @inline def appendAll(psi: ScalaPsiElement) {
      appendDeclaration(psi)
      appendPsiLineInfo(psi)
    }
    
    @inline def withPrint(text: String) = printMethodName + "(\"" + startText + text + "\")\n" 
    
    def withTempVar(callee: String) = 
      "{val $$temp$$ = " + instanceName + "." + callee + s"; $macroPrinterName.printDefInfo(" + "$$temp$$" + ")" + 
        eraseClassName + " + \" = \" + $$temp$$.toString" + erasePrefixName + "}"
    
    srcFile.getChildren foreach {
      case tpe: ScTypeAlias =>
        objectRes append withPrint(s"defined type alias ${tpe.name}")
        
        appendAll(tpe)
      case fun: ScFunction =>
        objectRes append (printMethodName + "(\"" + fun.getName + ": \" + " + macroPrinterName + 
          ".printDefInfo(" + instanceName + "." + fun.getName + " _)" + eraseClassName + ")\n") 
        
        appendAll(fun)
      case tpeDef: ScTypeDefinition =>
        val keyword = tpeDef match {
          case _: ScClass => "class"
          case _: ScTrait => "trait"
          case _ => "module"
        }

        objectRes append withPrint(s"defined $keyword ${tpeDef.name}")

        appendAll(tpeDef)
      case valDef: ScPatternDefinition =>
        valDef.bindings foreach {
          case p => 
            val pName = p.name
            val defName = s"get$$$$instance$$$$$pName"
            
            classRes append s"def $defName = $pName;$END_GENERATED_MARKER"
            objectRes append (printMethodName + "(\"" + startText + pName + ": \" + " + withTempVar(defName) + ")\n")
        }
        
        appendAll(valDef)
      case comm: PsiComment =>
        val range = comm.getTextRange
        ifDocument map {
          document => document.getLineNumber(range.getEndOffset) - document.getLineNumber(range.getStartOffset) + 1
        } map {
          case differ => for (_ <- 0 until differ) objectRes append printMethodName append "()\n"
        } getOrElse {
          val count = comm.getText count (_ == '\n')
          for (_ <- 0 until count) objectRes append printMethodName append "()\n"
        }
      case imp: ScImportStmt => 
        val text = imp.getText
        
        objectRes append s"$printMethodName($macroPrinterName.printImportInfo({$text;}))\n"

        classRes append  "import _root_."  append text.substring("import ".length) append insertNlsFromWs(imp)
        appendPsiLineInfo(imp)
      case expr: ScExpression =>
        val resName = s"get$$$$instance$$$$res$resCount"

        classRes append s"def $resName = $END_GENERATED_MARKER${expr.getText}${insertNlsFromWs(expr)}" 
        objectRes append (printMethodName + "(\"res" + startText + resCount + ": \" + " + withTempVar(resName) + ")\n")
        appendPsiLineInfo(expr)
        
        resCount += 1
      case ws: PsiWhiteSpace =>
        val count = ws.getText count (_ == '\n')
        for (_ <- 1 until count) objectRes append printMethodName append "()\n"
      case scalaPsi: ScalaPsiElement =>
        
      case _ => 
    }
    
    classRes append "}"
    objectRes append (printMethodName + "(\"" + END_OUTPUT_MARKER + "\")\n") append "} \n }"
    
    Some((objectPrologue + classRes.toString() + "\n\n\n" + objectRes.toString(), name))
  }
}
