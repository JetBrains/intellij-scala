package org.jetbrains.plugins.scala.lang.psi.impl

import types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import com.intellij.psi.PsiFile
import com.intellij.lang.ParserDefinition

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElementImpl, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.impl.source.CharTableImpl
import api.toplevel.typedef.ScMember

object ScalaPsiElementFactory {

  private val DUMMY = "dummy."

  def createExpressionFromText(buffer: String, manager: PsiManager): ScExpression = {
    val text = "class a {val b = " + buffer + "}"

    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.getTypeDefinitions()(0)
    val p = classDef.members()(0).asInstanceOf[ScPatternDefinition]
    p.expr
  }

  def createDummyParams(manager: PsiManager): ScParameters = {
    val text = "class a {def foo()}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.getTypeDefinitions()(0)
    val function = classDef.functions()(0)
    return function.paramClauses
  }

  def createImportStatementFromClass(file: ScalaFile, clazz: PsiClass, manager: PsiManager): ScImportStmt = {
    val qualifiedName = clazz.getQualifiedName
    val packageName = file.packageStatement match {
      case Some(x) => x.getPackageName
      case None => null
    }
    val name = getShortName(qualifiedName, packageName)
    val text = "import " + (if (isResolved(name, clazz, packageName, manager)) name else "_root_." + qualifiedName)
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    dummyFile.getFirstImportStmt match {
      case Some(x) => return x
      case None => {
        //cannot be
        return null
      }
    }
  }

  def createScalaFile(text: String, manager: PsiManager) =
    PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]

  def createStableReferenceElement(name: String, manager: PsiManager) = {
    val file = createScalaFile("class A extends B with " + name, manager)
    val classDef = file.getTypeDefinitions()(0)
    val extendsBlock = classDef.extendsBlock
    val parents = extendsBlock.templateParents
    parents match {
      case Some(p) => {                     
        val elements = p.typeElements
        elements.first.asInstanceOf[ScSimpleTypeElement].reference.asInstanceOf[ScStableCodeReferenceElement]
      }
      case _ => throw new com.intellij.util.IncorrectOperationException()
    }
  }

  def createDeclaration(typez: ScType, name: String, isVariable: Boolean, expr: ScExpression, manager: PsiManager): ScMember = {
    val text = "class a {" + (if (isVariable) "var " else "val ") +
            (if (typez != null) ":" /*todo: + typez.getPresentableText*/ + " " else "") + name + " = " + expr.getText + "}"
    val dummyFile = createScalaFile(text, manager)
    val classDef = dummyFile.getTypeDefinitions()(0)
    if (!isVariable) classDef.members()(0).asInstanceOf[ScPatternDefinition]
    else classDef.members()(0).asInstanceOf[ScVariableDefinition]
  }

  def createNewLineNode(manager: PsiManager): ASTNode = {
    val text = "\n"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    return dummyFile.getNode.getFirstChildNode
  }

  def createBlockFromExpr(expr: ScExpression, manager: PsiManager): ScExpression = {
    val text = "class a {\nval b = {\n" + expr.getText + "\n}\n}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.getTypeDefinitions()(0)
    val p = classDef.members()(0).asInstanceOf[ScPatternDefinition]
    p.expr
  }

  private def isResolved(name: String, clazz: PsiClass, packageName: String, manager: PsiManager): Boolean = {
    if (packageName == null) return true
    val text = "package " + packageName + "\nimport " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val imp: ScStableCodeReferenceElement = (dummyFile.getFirstImportStmt match {
      case Some(x) => x
      case None =>
        //cannot be
        null
    }).importExprs(0).reference match {
      case Some(x) => x
      case None => return false
    }
    imp.resolve match {
      case x: PsiClass => {
        return x.getQualifiedName == clazz.getQualifiedName
      }
      case _ => return false
    }
  }

  private def getShortName(qualifiedName: String, packageName: String): String = {
    if (packageName == null) return qualifiedName
    val qArray = qualifiedName.split("[.]")
    val pArray = packageName.split("[.]")
    var i = 0
    while (i < qArray.length - 1 && i < pArray.length && qArray(i) == pArray(i)) i += 1
    var res = ""
    while (i < qArray.length) {
      res += qArray(i)
      res += "."
      i += 1
    }
    return res.substring(0, res.length - 1)
  }
}
