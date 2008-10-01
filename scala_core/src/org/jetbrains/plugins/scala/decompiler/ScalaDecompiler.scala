package org.jetbrains.plugins.scala.decompiler

import _root_.scala.tools.nsc.symtab.classfile.ClassfileParser
import _root_.scala.tools.scalap.ByteArrayReader
import _root_.scala.tools.scalap.Classfile
import _root_.scala.tools.scalap.JavaWriter
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.lang.{PsiBuilderFactory, ASTNode, PsiBuilder}
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{VirtualFileManager, VirtualFile}
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.{TokenSet, IElementType}
import com.intellij.psi.{PsiFileFactory, PsiFile}
import com.intellij.util.io.StringRef
import java.io._
import lang.lexer.{ScalaLexer, ScalaTokenTypes}
import lang.parser.{ScalaElementTypes}
import lang.psi.ScalaFile
import lang.psi.stubs.impl.{ScFileStubImpl, ScExtendsBlockStubImpl, ScTypeDefinitionStubImpl}
import lang.psi.stubs.{ScExtendsBlockStub, ScTypeDefinitionStub, ScFileStub}
import parsec.{Memo, Parser, ParserConversions}

/**
 * @author ilyas
 */

object ScalaDecompiler {

  private val DUMMY = "dummy."

  def isScalaFile(bytes: Array[Byte]) = {
    val reader = new ByteArrayReader(bytes)
    val clazz = new Classfile(reader)
    clazz.attribs.find(a => a.toString() == "ScalaSig") match {
      case Some(sig) => true
      case None => false
    }
  }

  private def getFileText(bytes: Array[Byte]): String = {
    //Write to temp file
    val reader = new ByteArrayReader(bytes)
    val clazz = new Classfile(reader)
    val tempFile = File.createTempFile("scalap_temp", ".scala")
    val out = new FileWriter(tempFile)
    val writer = new JavaWriter(clazz, out)
    writer.printClass
    out.flush

    //reading file to string
    val buffer = new StringBuffer()
    val fis = new FileInputStream(tempFile)
    // Here BufferedInputStream is added for fast reading.
    val bis = new BufferedInputStream(fis);
    val dis = new DataInputStream(bis);

    // dis.available() returns 0 if the file does not have more lines.
    while (dis.available() != 0) {
      buffer.append(dis.readLine).append("\n")
    }
    fis.close();
    bis.close();
    dis.close();
    buffer.toString
  }

  private def declareGrammar = {
    import ScalaTokenTypes._, ScalaElementTypes._, ParserConversions._

    val WS = (tWHITE_SPACE_IN_LINE | tNON_SIGNIFICANT_NEWLINE | tLINE_TERMINATOR) ?
    val MODIFIERS = (kABSTRACT | kCASE | kFINAL | kSEALED | kPRIVATE | kPROTECTED) ?

    val REFERENCE_NT = ((tIDENTIFIER > tDOT) *) > tIDENTIFIER |> REFERENCE

    val PACKAGE_NT = kPACKAGE > REFERENCE_NT > tSEMICOLON |> PACKAGE_STMT

    val TYPES_NT = (REFERENCE_NT ?) > ((tCOMMA > REFERENCE_NT) *)
    val TYPE_PARAMS_NT = tLSQBRACKET > REFERENCE_NT > tRSQBRACKET
    val PARAM_NT = REFERENCE_NT > (TYPE_PARAMS_NT ?) |> TYPE //Parameter type
    val PARAMETERS_NT = (PARAM_NT ?) > ((tCOMMA > PARAM_NT) *) |> PARAM_CLAUSES // Function parameters

    val FUNCTION_NT = MODIFIERS > kDEF > tIDENTIFIER > tLPARENTHESIS > PARAMETERS_NT > tRPARENTHESIS >
        WS > tCOLON > PARAM_NT |> FUNCTION_DECLARATION // Function declaration

    val VAR_NT = MODIFIERS > kVAR > tIDENTIFIER > tCOLON > PARAM_NT |> VARIABLE_DECLARATION
    val VAL_NT = MODIFIERS > kVAL > tIDENTIFIER > tCOLON > PARAM_NT |> VALUE_DECLARATION

    val MEMBER_DECL_NT = (FUNCTION_NT | VAR_NT | VAL_NT) > tSEMICOLON

    val mockParser = (new Parser {
      def do_parse(builder: PsiBuilder) = {
        if (builder.getTokenType != tRBRACE) {
          builder.advanceLexer
          true
        } else false
      }
    }) *

    val TYPE_DEFINITION_BODY_NT = WS > tLBRACE > mockParser > tRBRACE // Type definition body

    val TYPE_DEFINITION_NT = MODIFIERS > (kCLASS | kTRAIT | kOBJECT) > tIDENTIFIER >
        ((((kEXTENDS > REFERENCE_NT) ?) > ((WS > kWITH > REFERENCE_NT) *) |> EXTENDS_BLOCK) ?) > // 'extends' block
        TYPE_DEFINITION_BODY_NT |> TYPE_DEFINITION

    val OTHER = PLAIN_PARSER
    val FILE = PACKAGE_NT > (TYPE_DEFINITION_NT *) > OTHER |> ScalaElementTypes.FILE
    FILE
  }

  def createFileStub(file: VirtualFile, bytes: Array[Byte]): PsiFileStub[ScalaFile] = {
    val fileText = getFileText(bytes)
    val builder = new PsiBuilderImpl(new ScalaLexer(),
      TokenSet.create(Array(ScalaTokenTypes.tWHITE_SPACE_IN_LINE, ScalaTokenTypes.tNON_SIGNIFICANT_NEWLINE)),
      TokenSet.create(Array[IElementType]()), fileText)
    val g: Parser = declareGrammar
    g.parse(builder)
    val tree = builder.getTreeBuilt();
    createStubByAST(tree, file)
  }

  private def createStubByAST(tree: ASTNode, file: VirtualFile): PsiFileStub[ScalaFile] = {
    val children = tree.getChildren(null)
    if (children.length == 0) return null;
    var pName: String = ""
    var first = children(0)
    first.getElementType match {
      case ScalaElementTypes.PACKAGE_STMT => {
        val ref = first.findChildByType(ScalaElementTypes.REFERENCE)
        if (ref != null) pName = ref.getText
        first = first.getTreeNext
      }
      case _ =>
    }
    val fileStub = new ScFileStubImpl(null, StringRef.fromString(pName), StringRef.fromString(file.getName), true)
    while (first != null) {
      if (first.getElementType == ScalaElementTypes.TYPE_DEFINITION) {
        createTypeDefintionStub(first, fileStub, pName)
      }
      first = first.getTreeNext
    }
    fileStub
  }

  /**
  Create stub for type definitions
   */
  private def createTypeDefintionStub(td: ASTNode, fileStub: ScFileStub, pName: String) = {
    import ScalaTokenTypes._

    val kinds = td.getChildren(TokenSet.create(Array(kCLASS, kOBJECT, kTRAIT)))
    def kindToElemType(k: IElementType) = k match {
      case ScalaTokenTypes.kOBJECT => ScalaElementTypes.OBJECT_DEF
      case ScalaTokenTypes.kTRAIT => ScalaElementTypes.TRAIT_DEF
      case _ => ScalaElementTypes.CLASS_DEF
    }
    if (kinds.length > 0) {
      val id = td.findChildByType(ScalaTokenTypes.tIDENTIFIER)
      val name = id.getText()
      val fqn = if (pName != null && pName.length > 0) pName + "." + name else name
      val elemType = kindToElemType(kinds(0).getElementType)
      if (elemType.toString.equals("trait definition")) {
        println(elemType + " " + fqn)
      }
      if (elemType.toString.equals("object definition")) {
        println(elemType + " " + fqn)
      }
      val typeDefStub = new ScTypeDefinitionStubImpl(fileStub, elemType, name, fqn, fileStub.getFileName);
      val eb = td.findChildByType(ScalaElementTypes.EXTENDS_BLOCK)
      if (eb != null) {
        createExtendsBlockStub(eb, typeDefStub)
      }
    }
  }

  private def createExtendsBlockStub(eb: ASTNode, tdStub: ScTypeDefinitionStub): ScExtendsBlockStub = {
    val stub = new ScExtendsBlockStubImpl(tdStub, ScalaElementTypes.EXTENDS_BLOCK)
    stub
  }

}