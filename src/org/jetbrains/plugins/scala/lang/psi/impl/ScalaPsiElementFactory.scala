package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.util

import com.intellij.lang.{ASTNode, PsiBuilderFactory}
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.{FileElement, TreeElement}
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Constructor, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ImplicitParamClause, ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{ConstrExpr, Dcl, Def}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.{ClassParamClause, ClassParamClauses, ImplicitClassParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScIdList, ScPatternList, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScTypeUtil, ScalaNamesUtil}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocInnerCodeElement, ScDocResolvableCodeReference, ScDocSyntaxElement}

import scala.collection.mutable

class ScalaPsiElementFactoryImpl(manager: PsiManager) extends JVMElementFactory {
  def createDocCommentFromText(text: String): PsiDocComment = ???

  def isValidClassName(name: String): Boolean = ScalaNamesUtil.isIdentifier(name)

  def isValidMethodName(name: String): Boolean = ScalaNamesUtil.isIdentifier(name)

  def isValidParameterName(name: String): Boolean = ScalaNamesUtil.isIdentifier(name)

  def isValidFieldName(name: String): Boolean = ScalaNamesUtil.isIdentifier(name)

  def isValidLocalVariableName(name: String): Boolean = ScalaNamesUtil.isIdentifier(name)

  def createConstructor(name: String, context: PsiElement): PsiMethod = ???

  def createParameter(name: String, `type`: PsiType, context: PsiElement): PsiParameter = ???

  def createClass(name: String): PsiClass = throw new IncorrectOperationException

  def createInterface(name: String): PsiClass = throw new IncorrectOperationException

  def createEnum(name: String): PsiClass = throw new IncorrectOperationException

  def createField(name: String, `type`: PsiType): PsiField = throw new IncorrectOperationException

  def createMethod(name: String, returnType: PsiType): PsiMethod = throw new IncorrectOperationException

  def createMethod(name: String, returnType: PsiType, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  def createConstructor(): PsiMethod = {
    ScalaPsiElementFactory.createMethodFromText("def this() {\nthis()\n}", manager)
  }

  def createConstructor(name: String): PsiMethod = {
    ScalaPsiElementFactory.createMethodFromText("def this() {\nthis()\n}", manager)
  }

  def createClassInitializer(): PsiClassInitializer = throw new IncorrectOperationException

  def createParameter(name: String, `type`: PsiType): PsiParameter = {
    val scType = `type`.toScType(manager.getProject)
    ScalaPsiElementFactory.createParameterFromText(s"$name : ${scType.canonicalText}", manager)
  }

  def createParameterList(names: Array[String], types: Array[PsiType]): PsiParameterList = throw new IncorrectOperationException

  def createMethodFromText(text: String, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  def createAnnotationFromText(annotationText: String, context: PsiElement): PsiAnnotation = throw new IncorrectOperationException

  def createReferenceElementByType(`type`: PsiClassType): PsiElement = ???

  def createTypeParameterList(): PsiTypeParameterList = ???

  def createTypeParameter(name: String, superTypes: Array[PsiClassType]): PsiTypeParameter = ???

  def createType(aClass: PsiClass): PsiClassType = ???

  def createAnnotationType(name: String): PsiClass = ???

  def createType(resolve: PsiClass, substitutor: PsiSubstitutor): PsiClassType = ???

  def createType(resolve: PsiClass, substitutor: PsiSubstitutor, languageLevel: LanguageLevel): PsiClassType = ???

  def createType(resolve: PsiClass, substitutor: PsiSubstitutor, languageLevel: LanguageLevel, annotations: Array[PsiAnnotation]): PsiClassType = ???

  def createType(aClass: PsiClass, parameters: PsiType): PsiClassType = ???

  def createRawSubstitutor(owner: PsiTypeParameterListOwner): PsiSubstitutor = ???

  def createSubstitutor(map: util.Map[PsiTypeParameter, PsiType]): PsiSubstitutor = ???

  def createPrimitiveType(text: String): PsiPrimitiveType = ???

  def createTypeByFQClassName(qName: String): PsiClassType = ???

  def createTypeByFQClassName(qName: String, resolveScope: GlobalSearchScope): PsiClassType = ???

  def createType(aClass: PsiClass, parameters: PsiType*): PsiClassType = ???

  def createExpressionFromText(text: String, context: PsiElement): PsiElement = {
    try {
      ScalaPsiElementFactory.createExpressionWithContextFromText(text, context, context)
    } catch {
      case e: Throwable => throw new IncorrectOperationException
    }
  }
}

object ScalaPsiElementFactory {

  def createExpressionFromText(text: String, context: PsiElement): PsiElement = {
    try {
      createExpressionWithContextFromText(text, context, context)
    } catch {
      case e: Throwable => throw new IncorrectOperationException(s"Cannot create expression from text $text with context ${context.getText}", e)
    }
  }

  private val DUMMY = "dummy."

  def createScalaFileFromText(text: String, project: Project): ScalaFile = {
    PsiFileFactory.getInstance(project).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
  }

  def createWildcardNode(manager : PsiManager): ASTNode = {
    val text = "import a._"

    val dummyFile: ScalaFile =
      PsiFileFactory.getInstance(manager.getProject).
              createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
        ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getLastChild.getLastChild.getLastChild.getNode
  }

  def createClauseFromText(clauseText: String, manager: PsiManager): ScParameterClause = {
    val text = "def foo" + clauseText + " = null"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val fun = dummyFile.getFirstChild.asInstanceOf[ScFunction]
    fun.paramClauses.clauses.apply(0)
  }

  def createClauseForFunctionExprFromText(clauseText: String, manager: PsiManager): ScParameterClause = {
    val text = clauseText + " => null"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val fun = dummyFile.getFirstChild.asInstanceOf[ScFunctionExpr]
    fun.params.clauses(0)
  }

  def createImplicitClauseFromTextWithContext(clauseText: String, manager: PsiManager,
                                              context: PsiElement): ScParameterClause = {
    createElementWithContext(clauseText, context, contextLastChild(context), ImplicitParamClause.parse(_)) match {
      case clause: ScParameterClause => clause
      case _ => null
    }
  }

  def createImplicitClassParamClauseFromTextWithContext(clauseText: String, manager: PsiManager,
                                                        context: PsiElement): ScParameterClause = {
    createElementWithContext(clauseText, context, contextLastChild(context), ImplicitClassParamClause.parse(_)) match {
      case clause: ScParameterClause => clause
      case _ => null
    }
  }

  def createEmptyClassParamClauseWithContext(manager: PsiManager, context: PsiElement): ScParameterClause = {
    createElementWithContext("()", context, contextLastChild(context), ClassParamClause.parse(_)) match {
      case clause: ScParameterClause => clause
      case _ => null
    }
  }

  def createClassParamClausesWithContext(text: String, context: PsiElement): ScParameters = {
    createElementWithContext(text, context, contextLastChild(context), ClassParamClauses.parse(_)) match {
      case parameters: ScParameters => parameters
      case _ => null
    }
  }

  def createConstructorFromText(text: String, context: PsiElement, child: PsiElement): ScConstructor = {
    createElementWithContext(text, context, child, Constructor.parse(_)) match {
      case c: ScConstructor => c
      case _ => null
    }
  }


  def createParamClausesWithContext(text: String, context: PsiElement, child: PsiElement): ScParameters = {
    createElementWithContext(text, context, child, ParamClauses.parse(_)) match {
      case parameters: ScParameters => parameters
      case _ => null
    }
  }

  private def contextLastChild(context: PsiElement): PsiElement = {
    context match {
      case s: StubBasedPsiElement[_] if s.getStub != null=>
        val stub = s.getStub.asInstanceOf[StubElement[_ <: PsiElement]]
        val children = stub.getChildrenStubs
        val size = children.size()
        if (size == 0) null
        else children.get(size - 1).getPsi
      case _ => context.getLastChild
    }
  }

  def createParameterFromText(paramText: String, manager: PsiManager): ScParameter = {
    val text = "def foo(" + paramText + ") = null"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val fun = dummyFile.getFirstChild.asInstanceOf[ScFunction]
    fun.parameters(0)
  }

  def createClassParameterFromText(paramText: String, manager: PsiManager): ScParameter = {
    val text = "class A(" + paramText + ")"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
              ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val clazz = dummyFile.getFirstChild.asInstanceOf[ScClass]
    clazz.parameters(0)
  }


  def createCaseClauseFromText(clauseText: String, manager: PsiManager): ScCaseClause= {
    val text = "x match { " + clauseText + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val matchStmt = dummyFile.getFirstChild.asInstanceOf[ScMatchStmt]
    matchStmt.caseClauses.head
  }

  def createPatternFromText(patternText: String, manager: PsiManager): ScPattern= {
    val text = "x match { case " + patternText + " => }"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val matchStmt = dummyFile.getFirstChild.asInstanceOf[ScMatchStmt]
    matchStmt.caseClauses.head.pattern.get
  }

  def createTypeParameterFromText(name: String, manager: PsiManager): ScTypeParam = {
    val text = s"def foo[$name]() {}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val fun = dummyFile.getFirstChild.asInstanceOf[ScFunction]
    fun.typeParameters(0)
  }

  def createMatch(scrutinee: String, caseClauses: Seq[String], manager: PsiManager): ScMatchStmt = {
    val text = "%s match { %s }".format(scrutinee, caseClauses.mkString("\n"))
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getFirstChild.asInstanceOf[ScMatchStmt]
  }

  def createAnAnnotation(name: String, manager: PsiManager): ScAnnotation = {
    val code = "@%s\ndef foo".format(name)
    val element = parseElement(code, manager)
    element.getFirstChild.getFirstChild.asInstanceOf[ScAnnotation]
  }

  def createScalaFile(manager: PsiManager): ScalaFile = ScalaPsiElementFactory.parseFile("", manager)

  def parseFile(text: String, manager: PsiManager): ScalaFile = {
    val factory = PsiFileFactory.getInstance(manager.getProject)
    val name = DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension
    factory.createFileFromText(name, ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
  }

  def parseElements(text: String, manager: PsiManager): Seq[PsiElement] = {
    parseFile(text, manager).children.toList
  }

  def parseElement(text: String, manager: PsiManager): PsiElement = {
    parseFile(text, manager).getFirstChild
  }

  def createPackaging(name: String, manager: PsiManager): PsiElement = {
    parseFile("package %s".format(name), manager).getFirstChild
  }

  def createMethodFromText(text: String, manager: PsiManager): ScFunction = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getFirstChild.asInstanceOf[ScFunction]
  }

  def createExpressionFromText(buffer: String, manager: PsiManager): ScExpression = {
    val text = "class a {val b = (" + buffer + ")}"

    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions(0)
    val p = classDef.members(0).asInstanceOf[ScPatternDefinition]
    p.expr.getOrElse(throw new IllegalArgumentException("Expression not found")) match {
      case x: ScParenthesisedExpr =>
        x.expr match {
          case Some(y) => y
          case _ => x
        }
      case x => x
    }
  }

  def createBlockExpressionWithoutBracesFromText(text: String, manager: PsiManager): ScBlockImpl = {
    createElement(text, manager, Block.parse(_, hasBrace = false, needNode = true)) match {
      case b: ScBlockImpl => b
      case _ => null
    }
  }


  def createOptionExpressionFromText(text: String, manager: PsiManager): Option[ScExpression] = {
    val dummyFile: ScalaFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text.trim).asInstanceOf[ScalaFile]
    val child = dummyFile.getFirstChild
    child match {
      case expr: ScExpression =>
        if (expr.getNextSibling == null && !PsiTreeUtil.hasErrorElements(dummyFile)) Some(expr) else None
      case _ => None
    }
  }

  def createIdentifier(name: String, manager: PsiManager): ASTNode = {
    val text = "package " + (if (!ScalaNamesUtil.isKeyword(name)) name else "`" + name + "`")
    try {
      val dummyFile = PsiFileFactory.getInstance(manager.getProject).
              createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
                ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
      dummyFile.getNode.getLastChildNode.getLastChildNode.getLastChildNode
    }
    catch {
      case t: Throwable => throw new IllegalArgumentException(s"Cannot create identifier from text $name")
    }
  }

  def createModifierFromText(name: String, manager: PsiManager): ASTNode = {
    val text = name + " class a"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.typeDefinitions(0).getModifierList.getFirstChild.getNode
  }

  def createImportExprFromText(name: String, manager: PsiManager): ScImportExpr = {
    val text = "import " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getLastChild.getLastChild.asInstanceOf[ScImportExpr]
  }

  def createImportFromText(text: String, manager: PsiManager): ScImportStmt = {
    val dummyFile: ScalaFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getFirstChild.asInstanceOf[ScImportStmt]
  }

  def createReferenceFromText(name: String, manager: PsiManager): ScStableCodeReferenceElement = {
    val text = "import " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    try {
      val imp: ScImportStmt = dummyFile.getFirstChild.asInstanceOf[ScImportStmt]
      val expr: ScImportExpr = imp.importExprs.apply(0)
      val ref = expr.reference match {
        case Some(x) => x
        case None => return null
      }
      ref
    }
    catch {
      case t: Throwable => throw new IllegalArgumentException(s"Cannot create reference with text $name")
    }
  }

  def createImportStatementFromClass(holder: ScImportsHolder, clazz: PsiClass, manager: PsiManager): ScImportStmt = {
    val qualifiedName = clazz.qualifiedName
    val packageName = holder match {
      case packaging: ScPackaging => packaging.getPackageName
      case _ =>
        var element: PsiElement = holder
        while (element != null && !element.isInstanceOf[ScalaFile] && !element.isInstanceOf[ScPackaging])
          element = element.getParent
        element match {
          case packaging: ScPackaging => packaging.getPackageName
          case _ => null
        }
    }
    val name = getShortName(qualifiedName, packageName)
    val text = "import " + (if (isResolved(name, clazz, packageName, manager)) name else "_root_." + qualifiedName)
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getImportStatements.headOption match {
      case Some(x) => x
      case None =>
        //cannot be
        null
    }
  }

  def createBigImportStmt(expr: ScImportExpr, exprs: Array[ScImportExpr], manager: PsiManager): ScImportStmt = {
    val qualifier = expr.qualifier.getText
    var text = "import " + qualifier
    val names = new mutable.HashSet[String]
    names ++= expr.getNames
    for (expr <- exprs) names ++= expr.getNames
    val arrow = ScalaPsiUtil.functionArrow(manager.getProject)
    if ((names("_") ||
            ScalaCodeStyleSettings.getInstance(manager.getProject).getClassCountToUseImportOnDemand <=
                    names.size) &&
            names.filter(_.indexOf(arrow) != -1).toSeq.size == 0) text = text + "._"
    else {
      text = text + ".{"
      for (string <- names) {
        text = text + string
        text = text + ", "
      }
      text = text.substring(0, text.length - 2)
      text = text + "}"
    }
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getImportStatements.headOption match {
      case Some(x) => x
      case None =>
        //cannot be
        null
    }
  }

  def createScalaFile(text: String, manager: PsiManager): ScalaFile =
    PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]

  def createStableReferenceElement(name: String, manager: PsiManager) = {
    val file = createScalaFile("class A extends B with " + name, manager)
    val classDef = file.typeDefinitions(0)
    val extendsBlock = classDef.extendsBlock
    val parents = extendsBlock.templateParents
    (parents: @unchecked) match {
      case Some(p) =>
        val elements = p.typeElements
        (elements.head.asInstanceOf[ScSimpleTypeElement].reference: @unchecked) match {case Some(r) => r}
      case _ => throw new com.intellij.util.IncorrectOperationException()
    }
  }

  def createDeclaration(typez: ScType, name: String, isVariable: Boolean,
                        expr: ScExpression, manager: PsiManager, isPresentableText: Boolean): ScMember = {
    val typeText =
      if (typez == null) ""
      else if(isPresentableText) typez.presentableText
      else typez.canonicalText
    createDeclaration(name, typeText, isVariable, expr, manager)
  }

  def createDeclaration(typez: ScType, name: String, isVariable: Boolean,
                        exprText: String, manager: PsiManager, isPresentableText: Boolean = false): ScMember = {
    val expr = createExpressionFromText(exprText, manager)
    createDeclaration(typez, name, isVariable, expr, manager, isPresentableText)
  }

  def createDeclaration(name: String, typeName: String, isVariable: Boolean, expr: ScExpression, manager: PsiManager): ScMember = {
    def stmtText(stmt: ScBlockStatement): String =  stmt match {
      case block @ ScBlock(st) if !block.hasRBrace => stmtText(st)
      case fun @ ScFunctionExpr(parSeq, Some(result)) =>
        val paramText =
          if (parSeq.size == 1) {
            val par = parSeq.head
            if (par.typeElement.isDefined && par.getPrevSiblingNotWhitespace == null) s"(${par.getText})"
            else fun.params.getText
          } else fun.params.getText
        val resultText = result match {
          case block: ScBlock if !block.hasRBrace && block.statements.size != 1 => s"{\n${block.getText}\n}"
          case block @ ScBlock(st) if !block.hasRBrace => stmtText(st)
          case _ => result.getText
        }
        val arrow = ScalaPsiUtil.functionArrow(manager.getProject)
        s"$paramText $arrow $resultText"
      case null => ""
      case _ => stmt.getText
    }
    val beforeColon = if (ScalaNamesUtil.isOpCharacter(name.last)) " " else ""
    val typeText =
      if (typeName != null && typeName != ""){
        createTypeElementFromText(typeName, manager) //throws an exception if type name is incorrect
        s"$beforeColon: $typeName"
      }  else ""
    val keyword: String = if (isVariable) "var" else "val"
    val text = s"class a {$keyword $name$typeText = ${stmtText(expr)}"
    val dummyFile = createScalaFile(text, manager)
    val classDef = dummyFile.typeDefinitions(0)
    if (!isVariable) classDef.members(0).asInstanceOf[ScValue]
    else classDef.members(0).asInstanceOf[ScVariable]
  }

  def createValFromVarDefinition(varDef: ScVariableDefinition, manager: PsiManager): ScValue = {
    val varKeyword = varDef.varKeyword
    val startOffset = varKeyword.getStartOffsetInParent
    val varText = varDef.getText
    val text = "class a {" + varText.substring(0, startOffset) + "val" + varText.substring(startOffset + 3) + " }"
    val dummyFile = createScalaFile(text, manager)
    val classDef = dummyFile.typeDefinitions(0)
    classDef.members(0).asInstanceOf[ScValue]
  }

  def createVarFromValDeclaration(valDef: ScValue, manager: PsiManager): ScVariable = {
    val valKeyword = valDef.valKeyword
    val startOffset = valKeyword.getStartOffsetInParent
    val valText = valDef.getText
    val text = "class a {" + valText.substring(0, startOffset) + "var" + valText.substring(startOffset + 3) + " }"
    val dummyFile = createScalaFile(text, manager)
    val classDef = dummyFile.typeDefinitions(0)
    classDef.members(0).asInstanceOf[ScVariable]
  }

  def createEnumerator(name: String, expr: ScExpression, manager: PsiManager, scType: ScType = null): ScEnumerator = {
    val typeName = if (scType == null) null else scType.presentableText
    createEnumerator(name, expr, manager, typeName)
  }

  def createEnumerator(name: String, expr: ScExpression, manager: PsiManager, typeName: String): ScEnumerator = {
    val typeText = if (typeName == null || typeName == "") "" else ": " + typeName
    val enumText = s"$name$typeText = ${expr.getText}"
    val text = s"for {\n  i <- 1 to 239\n  $enumText\n}"
    val dummyFile = createScalaFile(text, manager)
    val forStmt: ScForStatement = dummyFile.getFirstChild.asInstanceOf[ScForStatement]
    forStmt.enumerators.flatMap(_.enumerators.headOption).getOrElse {
      throw new IllegalArgumentException(s"Could not create enumerator from text:\n $enumText")
    }
  }

  def createNewLine(manager: PsiManager): PsiElement = createNewLineNode(manager, "\n").getPsi

  def createNewLine(manager: PsiManager, text: String): PsiElement = createNewLineNode(manager, text).getPsi

  def createNewLineNode(manager: PsiManager): ASTNode = createNewLineNode(manager, "\n")
  def createNewLineNode(manager: PsiManager, text: String): ASTNode = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.getNode.getFirstChildNode
  }

  def createBlockFromExpr(expr: ScExpression, manager: PsiManager): ScExpression =
    getExprFromFirstDef("class a {\nval b = {\n" + expr.getText + "\n}\n}", manager)

  def createAnonFunBlockFromFunExpr(expr: ScFunctionExpr, manager: PsiManager): ScExpression =
    getExprFromFirstDef("class a {\nval b = {" + expr.params.getText + "=> \n" +
      expr.result.map(_.getText).getOrElse("") + "\n}}", manager)

  private def getExprFromFirstDef(text: String, manager: PsiManager): ScExpression = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
        ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions(0)
    val p = classDef.members(0).asInstanceOf[ScPatternDefinition]
    p.expr.getOrElse(throw new IllegalArgumentException("Expression not found"))
  }

  def createBodyFromMember(element: PsiElement, manager: PsiManager): ScTemplateBody = {
    val text = "class a {\n" + element.getText + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef: ScTypeDefinition = dummyFile.typeDefinitions(0)
    val body = classDef.extendsBlock.templateBody match {
      case Some(x) => x
      case None => return null
    }
    body
  }

  def createTemplateBody(manager: PsiManager): ScTemplateBody = {
    val text = "class a {\n}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    dummyFile.typeDefinitions.head.extendsBlock.templateBody.get
  }

  def createClassTemplateParents(superName: String, manager: PsiManager): (PsiElement, ScTemplateParents) = {
    val text = "class a extends %s {\n}".format(superName)
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val extendsBlock = dummyFile.typeDefinitions.head.extendsBlock
    val extendToken = extendsBlock.findFirstChildByType(ScalaTokenTypes.kEXTENDS)
    val templateParents = extendsBlock.templateParents.get
    (extendToken, templateParents)
  }

  def createMethodFromSignature(sign: PhysicalSignature, manager: PsiManager, needsInferType: Boolean, body: String): ScFunction = {
    val text = "class a {\n  " + methodFromSignatureText(sign, body, needsInferType) + "\n}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions(0)
    val function = classDef.functions(0)
    function
  }

  def createOverrideImplementMethod(sign: PhysicalSignature, manager: PsiManager, needsOverrideModifier: Boolean,
                                    needsInferType: Boolean, body: String): ScFunction = {
    val fun = createMethodFromSignature(sign, manager, needsInferType, body)
    addModifiersFromSignature(fun, sign, needsOverrideModifier)
  }

  def createOverrideImplementType(alias: ScTypeAlias, substitutor: ScSubstitutor, manager: PsiManager,
                                  needsOverrideModifier: Boolean): ScTypeAlias = {
    val text = "class a {" + getOverrideImplementTypeSign(alias, substitutor, "this.type", needsOverrideModifier) + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions(0)
    val al = classDef.aliases(0)
    al
  }

  def createOverrideImplementVariable(variable: ScTypedDefinition, substitutor: ScSubstitutor, manager: PsiManager,
                                      needsOverrideModifier: Boolean, isVal: Boolean, needsInferType: Boolean): ScMember = {
    val text = "class a {" + getOverrideImplementVariableSign(variable, substitutor, "_", needsOverrideModifier, isVal, needsInferType) + "}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.typeDefinitions(0)
    classDef.members(0) match {case member : ScMember => member}
  }

  def createSemicolon(manager: PsiManager): PsiElement = {
    val text = ";"
    val dummyFile = createScalaFile(text, manager)
    dummyFile.findElementAt(0)
  }

  private def isResolved(name: String, clazz: PsiClass, packageName: String, manager: PsiManager): Boolean = {
    if (packageName == null) return true
    val text = "package " + packageName + "\nimport " + name
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val imp: ScStableCodeReferenceElement = (dummyFile.getImportStatements.headOption match {
      case Some(x) => x
      case None =>
        //cannot be
        null
    }).importExprs(0).reference match {
      case Some(x) => x
      case None => return false
    }
    imp.resolve() match {
      case x: PsiClass =>
        x.qualifiedName == clazz.qualifiedName
      case _ => false
    }
  }

  private def addModifiersFromSignature(function: ScFunction, sign: PhysicalSignature, addOverride: Boolean): ScFunction = {
    sign.method match {
      case fun: ScFunction =>
        val res = function.getModifierList.replace(fun.getModifierList)
        if (res.getText.nonEmpty) res.getParent.addAfter(createWhitespace(fun.getManager), res)
        if (!fun.hasModifierProperty("override") && addOverride) function.setModifierProperty("override", value = true)
      case m: PsiMethod =>
        var hasOverride = false
        if (m.getModifierList.getNode != null)
          for (modifier <- m.getModifierList.getNode.getChildren(null); modText = modifier.getText) {
            modText match {
              case "override" => hasOverride = true; function.setModifierProperty("override", value = true)
              case "protected" => function.setModifierProperty("protected", value = true)
              case "final" => function.setModifierProperty("final", value = true)
              case _ =>
            }
          }
        if (addOverride && !hasOverride) function.setModifierProperty("override", value = true)
    }
    function
  }

  private def methodFromSignatureText(sign: PhysicalSignature, body: String, needsInferType: Boolean): String = {
    val builder = mutable.StringBuilder.newBuilder
    val method = sign.method
    // do not substitute aliases
    val substitutor = sign.substitutor
    method match {
      case method: ScFunction =>
        builder ++= method.getFirstChild.getText
        if (builder.nonEmpty) builder ++= "\n"
        builder ++= "def " + method.name
        //adding type parameters
        if (method.typeParameters.nonEmpty) {
          def buildText(typeParam: ScTypeParam): String = {
            val variance = if (typeParam.isContravariant) "-" else if (typeParam.isCovariant) "+" else ""
            val clauseText = typeParam.typeParametersClause match {
              case None => ""
              case Some(x) => x.typeParameters.map(buildText).mkString("[", ",", "]")
            }
            val lowerBoundText = typeParam.lowerBound.toOption collect {
              case psi.types.Nothing => ""
              case x => " >: " + substitutor.subst(x).canonicalText
            }
            val upperBoundText = typeParam.upperBound.toOption collect {
              case psi.types.Any => ""
              case x => " <: " + substitutor.subst(x).canonicalText
            }
            val viewBoundText = typeParam.viewBound map {
              x => " <% " + substitutor.subst(x).canonicalText
            }
            val contextBoundText = typeParam.contextBound collect {
              case tp: ScType => " : " + ScTypeUtil.stripTypeArgs(substitutor.subst(tp)).canonicalText
            }
            val boundsText = (lowerBoundText.toSeq ++ upperBoundText.toSeq ++ viewBoundText ++ contextBoundText).mkString
            s"$variance${typeParam.name}$clauseText$boundsText"
          }

          val typeParamTexts = for (t <- method.typeParameters) yield buildText(t)
          builder ++= typeParamTexts.mkString("[", ", ", "]")
        }
        if (method.paramClauses != null) {
          for (paramClause <- method.paramClauses.clauses) {
            def buildText(param: ScParameter): String = {
              val name = param.name
              param.typeElement match {
                case Some(x) =>
                  val colon = if (ScalaNamesUtil.isIdentifier(name + ":")) " : " else ": "
                  val typeText = substitutor.subst(x.getType(TypingContext.empty).getOrAny).canonicalText
                  val arrow = ScalaPsiUtil.functionArrow(param.getProject)
                  name + colon + (if (param.isCallByNameParameter) arrow else "") + typeText + (if (param.isRepeatedParameter) "*" else "")
                case _ => name
              }
            }
            val params = for (t <- paramClause.parameters) yield buildText(t)
            builder ++= params.mkString(if (paramClause.isImplicit) "(implicit " else "(", ", ", ")")
          }
        }

        val retType = method.returnType.toOption.map(t => substitutor.subst(t))
        val retAndBody = (needsInferType, retType) match {
          case (true, Some(scType)) =>
            var text = scType.canonicalText
            if (text == "_root_.java.lang.Object") text = "AnyRef"
            val needWhitespace = method.paramClauses.clauses.isEmpty && method.typeParameters.isEmpty && ScalaNamesUtil.isIdentifier(method.name + ":")
            val colon = if (needWhitespace) " : " else ": "
            s"$colon$text = $body"
          case _ =>
            " = " + body
        }
        builder ++= retAndBody
      case _ =>
        builder ++= "def " + ScalaNamesUtil.changeKeyword(method.name)
        if (method.hasTypeParameters) {
          val params = method.getTypeParameters
          val strings = for (param <- params) yield {
            val extendsTypes = param.getExtendsListTypes
            val extendsTypesText = if (extendsTypes.nonEmpty) {
              val typeTexts = extendsTypes.map((t: PsiClassType) =>
                substitutor.subst(t.toScType(method.getProject)).canonicalText)
              typeTexts.mkString(" <: "," with ", "")
            } else ""
            param.name + extendsTypesText
          }
          builder ++= strings.mkString("[", ", ", "]")
        }

        import org.jetbrains.plugins.scala.extensions._

        val paramCount = method.getParameterList.getParametersCount
        val omitParamList = paramCount == 0 && method.hasQueryLikeName

        if (!omitParamList) {
          val params = for (param <- method.getParameterList.getParameters) yield {
            val paramName = param.name match {
              case null => param match {case param: ClsParameterImpl => param.getStub.getName case _ => null}
              case x => x
            }
            val pName: String = ScalaNamesUtil.changeKeyword(paramName)
            val colon = if (pName.endsWith("_")) " : " else ": "
            val scType: ScType = substitutor.subst(param.getTypeElement.getType.toScType(method.getProject))
            val typeText = scType match {
              case types.AnyRef => "scala.Any"
              case JavaArrayType(arg: ScType) if param.isVarArgs => arg.canonicalText + "*"
              case _ => scType.canonicalText
            }
            s"$pName$colon$typeText"
          }
          builder ++= params.mkString("(", ", ", ")")
        }

        val retType = substitutor.subst(method.getReturnType.toScType(method.getProject))
        val retAndBody =
          if (needsInferType) {
            val typeText = if (retType == types.Any) "AnyRef" else retType.canonicalText
            s": $typeText = $body"
          } else " = " + body
        builder ++= retAndBody
    }
    builder.toString()
  }

  def getOverrideImplementTypeSign(alias: ScTypeAlias, substitutor: ScSubstitutor, body: String,
                                   needsOverride: Boolean): String = {
    try {
      alias match {
        case alias: ScTypeAliasDefinition =>
          val overrideText = if (needsOverride && !alias.hasModifierProperty("override")) "override " else ""
          val modifiersText = alias.getModifierList.getText
          val typeText = substitutor.subst(alias.aliasedType(TypingContext.empty).getOrAny).canonicalText
          s"$overrideText$modifiersText type ${alias.name} = $typeText"
        case alias: ScTypeAliasDeclaration =>
          val overrideText = if (needsOverride) "override " else ""
          s"$overrideText${alias.getModifierList.getText} type ${alias.name} = $body"
      }
    }
    catch {
      case e: Exception => e.printStackTrace()
      ""
    }
  }

  def getOverrideImplementVariableSign(variable: ScTypedDefinition, substitutor: ScSubstitutor,
                                       body: String, needsOverride: Boolean,
                                       isVal: Boolean, needsInferType: Boolean): String = {
    val modOwner: ScModifierListOwner = ScalaPsiUtil.nameContext(variable) match {case m: ScModifierListOwner => m case _ => null}
    val overrideText = if (needsOverride && (modOwner == null || !modOwner.hasModifierProperty("override"))) "override " else ""
    val modifiersText = if (modOwner != null) modOwner.getModifierList.getText + " " else ""
    val keyword = if (isVal) "val " else "var "
    val name = variable.name
    val colon = if (ScalaNamesUtil.isIdentifier(name + ":")) " : " else ": "
    val typeText = substitutor.subst(variable.getType(TypingContext.empty).getOrAny).canonicalText
    s"$overrideText$modifiersText$keyword$name$colon$typeText = $body"
  }

  private def getShortName(qualifiedName: String, packageName: String): String = {
    if (packageName == null) return qualifiedName
    val qArray = qualifiedName.split("[.]")
    val pArray = packageName.split("[.]")
    var i = 0
    while (i < qArray.length - 1 && i < pArray.length && qArray(i) == pArray(i)) i = i + 1
    var res = ""
    while (i < qArray.length) {
      res = res + qArray(i)
      res = res + "."
      i = i + 1
    }
    res.substring(0, res.length - 1)
  }

  def getStandardValue(typez: ScType): String = {
    typez match {
      case ValType("Unit") => "()"
      case ValType("Boolean") => "false"
      case ValType("Char" | "Int" | "Byte") => "0"
      case ValType("Long") => "0L"
      case ValType("Float" | "Double") => "0.0"
      case ScDesignatorType(c: PsiClass) if c.qualifiedName == "java.lang.String" => "\"\""
      case _ => "null"
    }
  }

  def createTypeFromText(text: String, context: PsiElement, child: PsiElement): ScType = {
    val te = createTypeElementFromText(text, context, child)
    if (te == null) null
    else te.getType(TypingContext.empty).getOrAny
  }

  def createMethodWithContext(text: String, context: PsiElement, child: PsiElement): ScFunction = {
    createElementWithContext(text, context, child, Def.parse(_)) match {
      case fun: ScFunction => fun
      case _ => null
    }
  }

  def createDefinitionWithContext(text: String, context: PsiElement, child: PsiElement): ScMember = {
    createElementWithContext(text, context, child, Def.parse(_)) match {
      case memb: ScMember => memb
      case _ => null
    }
  }

  def createObjectWithContext(text: String, context: PsiElement, child: PsiElement): ScObject = {
    createElementWithContext(text, context, child, TmplDef.parse(_)) match {
      case o: ScObject => o
      case _ => null
    }
  }

  def createTypeDefinitionWithContext(text: String, context: PsiElement, child: PsiElement): ScTypeDefinition = {
    createElementWithContext(text, context, child, TmplDef.parse(_)) match {
      case td: ScTypeDefinition => td
      case _ => null
    }
  }

  def createReferenceFromText(text: String, context: PsiElement, child: PsiElement): ScStableCodeReferenceElement = {
    createElementWithContext(text, context, child, StableId.parse(_, ScalaElementTypes.REFERENCE)) match {
      case ref: ScStableCodeReferenceElement => ref
      case _ => null
    }
  }

  def createExpressionWithContextFromText(text: String, context: PsiElement, child: PsiElement): ScExpression = {
    createElementWithContext(s"foo($text)", context, child, Expr.parse(_)) match {
      case call: ScMethodCall =>
        val res = if (call.argumentExpressions.nonEmpty) call.argumentExpressions.head else null
        if (res != null) res.setContext(context, child)
        res
      case _ => null
    }
  }

  def createConstructorBodyWithContextFromText(text: String, context: PsiElement, child: PsiElement): ScExpression = {
    createElementWithContext(s"$text", context, child, ConstrExpr.parse(_)) match {
      case expr: ScExpression =>
        expr.setContext(context, child)
        expr
      case _ => null
    }
  }

  def createElement(text: String, manager: PsiManager, parse: ScalaPsiBuilder => Unit): PsiElement = {
    val context = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
              ScalaFileType.SCALA_FILE_TYPE, "").asInstanceOf[ScalaFile]
    val holder: FileElement = DummyHolderFactory.createHolder(manager, context).getTreeElement
    val builder: ScalaPsiBuilderImpl =
      new ScalaPsiBuilderImpl(PsiBuilderFactory.getInstance.createBuilder(manager.getProject, holder,
        new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, text.trim))
    val marker = builder.mark()
    parse(builder)
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    marker.done(ScalaElementTypes.FILE)
    val fileNode = builder.getTreeBuilt
    val node = fileNode.getFirstChildNode
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    psi
  }

  def createElementWithContext(text: String, context: PsiElement, child: PsiElement,
                                                parse: ScalaPsiBuilder => Unit): PsiElement = {
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: ScalaPsiBuilderImpl =
      new ScalaPsiBuilderImpl(PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder,
        new ScalaLexer, ScalaFileType.SCALA_LANGUAGE, text.trim))
    val marker = builder.mark()
    parse(builder)
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    marker.done(ScalaElementTypes.FILE)
    val fileNode = builder.getTreeBuilt
    val node = fileNode.getFirstChildNode
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    val psi = node.getPsi
    psi match {
      case element: ScalaPsiElement =>
        element.setContext(context, child)
        element
      case _ => null
    }
  }

  def createImportFromTextWithContext(text: String, context: PsiElement, child: PsiElement): ScImportStmt = {
    createElementWithContext(text, context, child, Import.parse) match {
      case imp: ScImportStmt => imp
      case _ => null
    }
  }

  def createTypeElementFromText(text: String, manager: PsiManager): ScTypeElement = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "var f: " + text).asInstanceOf[ScalaFile]
    try {
      val child = dummyFile.getLastChild.getLastChild
      if (child == null) throw new IncorrectOperationException("wrong type element to parse: " + text)
      child.asInstanceOf[ScTypeElement]
    }
    catch {
      case cce: ClassCastException => throw new IncorrectOperationException("wrong type element to parse: " + text)
    }
  }

  def createColon(manager: PsiManager): PsiElement = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "var f: Int").asInstanceOf[ScalaFile]
    dummyFile.getFirstChild.asInstanceOf[ScalaPsiElement].findChildrenByType(ScalaTokenTypes.tCOLON).head
  }

  def createComma(manager: PsiManager): PsiElement = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, ",").asInstanceOf[ScalaFile]
    dummyFile.findChildrenByType(ScalaTokenTypes.tCOMMA).head
  }

  def createAssign(manager: PsiManager): PsiElement = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "val x = 0").asInstanceOf[ScalaFile]
    dummyFile.findChildrenByType(ScalaTokenTypes.tASSIGN).head
  }

  def createWhitespace(manager: PsiManager): PsiElement = {
    createExpressionFromText("1 + 1", manager).findElementAt(1)
  }

  def createTypeElementFromText(text: String, context: PsiElement, child: PsiElement): ScTypeElement = {
    createElementWithContext(text, context, child, Type.parse(_)) match {
      case te: ScTypeElement => te
      case _ => null
    }
  }

  def createConstructorTypeElementFromText(text: String, context: PsiElement, child: PsiElement): ScTypeElement = {
    createElementWithContext(text, context, child, Constructor.parse(_)) match {
      case constructor: ScConstructor => constructor.typeElement
      case _ => null
    }
  }

  def createTypeParameterClauseFromTextWithContext(text: String, context: PsiElement,
                                                   child: PsiElement): ScTypeParamClause = {
    createElementWithContext(text, context, child, TypeParamClause.parse(_)) match {
      case clause: ScTypeParamClause => clause
      case _ => null
    }
  }

  def createExistentialClauseForName(name: String, manager: PsiManager): ScExistentialClause = {
   val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "val x: T forSome {type " + name + "}").asInstanceOf[ScalaFile]
    dummyFile.getChildren.head.asInstanceOf[ScValueDeclaration].typeElement.get.
      asInstanceOf[ScExistentialTypeElement].clause
  }

  def createWildcardPattern(manager: PsiManager): ScWildcardPattern = {
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "val _ = x").asInstanceOf[ScalaFile]
    dummyFile.getChildren.head.getChildren.apply(2).getChildren.head.asInstanceOf[ScWildcardPattern]
  }

  def createPatterListFromText(text: String, context: PsiElement, child: PsiElement): ScPatternList = {
    createElementWithContext(s"val $text = 239", context, child, Def.parse(_)) match {
      case patternDef: ScPatternDefinition =>
        val res = patternDef.pList
        res.setContext(context, child)
        res
      case _ => null
    }
  }

  def createIdsListFromText(text: String, context: PsiElement, child: PsiElement): ScIdList = {
    val valDef = createDeclarationFromText(s"val $text : Int", context, child).asInstanceOf[ScValueDeclaration]
    if (valDef != null) {
      val res = valDef.getIdList
      res.setContext(context, child)
      res
    } else null
  }

  def createTemplateDefinitionFromText(text: String, context: PsiElement, child: PsiElement): ScTemplateDefinition = {
    createElementWithContext(text, context, child, TmplDef.parse(_)) match {
      case tmplDef: ScTemplateDefinition => tmplDef
      case _ => null
    }
  }

  def createDeclarationFromText(text: String, context: PsiElement, child: PsiElement): ScDeclaration = {
    createElementWithContext(text, context, child, Dcl.parse(_)) match {
      case decl: ScDeclaration => decl
      case _ => null
    }
  }

  def createTypeAliasDefinitionFromText(text: String, context: PsiElement, child: PsiElement): ScTypeAliasDefinition = {
    createElementWithContext(text, context, child, Def.parse(_)) match {
      case typeAlias: ScTypeAliasDefinition => typeAlias
      case _ => null
    }
  }

  def createBlockCommentFromText(text: String, manager: PsiManager): PsiComment = {
    createScalaFile("/*" + text + "*/" + " class a { }", manager).typeDefinitions(0).simpleComment.get
  }

  def createDocCommentFromText(text: String, manager: PsiManager): ScDocComment = {
    createScalaFile("/**\n" + text + "\n*/" + " class a { }", manager).typeDefinitions(0).docComment.get
  }

  def createMonospaceSyntaxFromText(text: String, manager: PsiManager): ScDocSyntaxElement = {
    val docComment = createScalaFile("/**\n`" + text + "`\n*/" + " class a { }",
      manager).typeDefinitions(0).docComment.get
    docComment.getChildren()(2).asInstanceOf[ScDocSyntaxElement]
  }

  def createDocHeaderElement(length: Int, manager: PsiManager): PsiElement =
    createScalaFile("/**=header" + StringUtils.repeat("=", length) + "*/\n class a {}",
      manager).typeDefinitions(0).docComment.get.getNode.getChildren(null)(1).getLastChildNode.getPsi

  def createDocWhiteSpace(manager: PsiManager): PsiElement =
    createScalaFile("/**\n *\n*/ class a {}", manager).typeDefinitions(0).docComment.
            get.getNode.getChildren(null)(1).getPsi

  def createLeadingAsterisk(manager: PsiManager): PsiElement =
    createScalaFile("/**\n *\n*/ class a {}", manager).typeDefinitions(0).docComment.
            get.getNode.getChildren(null)(2).getPsi

  def createDocSimpleData(text: String, manager: PsiManager): PsiElement =
    createScalaFile("/**" + text + "*/ class a {}", manager).typeDefinitions(0).docComment.
            get.getNode.getChildren(null)(1).getPsi

  def createDocTagValue(text: String,  manager: PsiManager): PsiElement = {
    createScalaFile("/**@param " + text + "\n*/ class a{}", manager).typeDefinitions(0).docComment.
            get.getNode.getChildren(null)(1).getChildren(null)(2).getPsi
  }

  def createDocTag(text: String, manager: PsiManager): PsiElement =
    createScalaFile(s"/**$text*/ class a", manager).typeDefinitions(0).docComment.get.getNode.getChildren(null)(1).getPsi

  def createDocTagName(name: String, manager: PsiManager): PsiElement = {
    createScalaFile("/**@" + name + " qwerty */", manager).typeDefinitions(0).docComment.
            get.getNode.getChildren(null)(1).getChildren(null)(0).getPsi
  }

  def createDocLinkValue(text: String, manager: PsiManager): ScDocResolvableCodeReference = {
    createScalaFile("/**[[" + text + "]]*/ class a{}", manager).typeDefinitions(0).docComment.
            get.getNode.getChildren(null)(1).getChildren(null)(1).getPsi.asInstanceOf[ScDocResolvableCodeReference]
  }

  def createDocInnerCode(text: String, manager: PsiManager): ScDocInnerCodeElement = {
    createScalaFile("/**{{{" + text + "}}}\n*/\n class a{}", manager).typeDefinitions(0).docComment.get.getNode.
            getChildren(null)(1).getPsi.asInstanceOf[ScDocInnerCodeElement]
  }

  def createXmlEndTag(tagName: String, manager: PsiManager): ScXmlEndTag = {
    createScalaFile("val a = <" + tagName + "></" + tagName + ">", manager).getFirstChild.getLastChild.getFirstChild.getLastChild.asInstanceOf[ScXmlEndTag]
  }

  def createXmlStartTag(tagName: String, manager: PsiManager, attributes: String = ""): ScXmlStartTag = {
    createScalaFile("val a = <" + tagName + attributes + "></" + tagName + ">", manager).
            getFirstChild.getLastChild.getFirstChild.getFirstChild.asInstanceOf[ScXmlStartTag]
  }

  def createInterpolatedStringPrefix(prefix: String, manager: PsiManager): PsiElement = {
    createScalaFile(prefix + "\"blah\"", manager).getFirstChild.getFirstChild
  }

  def createEquivMethodCall(infixExpr: ScInfixExpr): ScMethodCall = {
    val baseText = infixExpr.getBaseExpr.getText
    val opText = infixExpr.operation.getText
    val typeArgText = infixExpr.typeArgs match {
      case Some(tpArg) => tpArg.getText
      case _ => ""
    }
    val argText = infixExpr.getArgExpr.getText
    val clauseText = infixExpr.getArgExpr match {
      case _: ScTuple | _: ScParenthesisedExpr | _: ScUnitExpr => argText
      case _ =>  s"($argText)"
    }
    val exprText = s"($baseText).$opText$typeArgText$clauseText"

    val exprA : ScExpression = createExpressionWithContextFromText(baseText, infixExpr, infixExpr.getBaseExpr)

    val methodCallExpr =
      createExpressionWithContextFromText(exprText.toString, infixExpr.getContext, infixExpr).asInstanceOf[ScMethodCall]
    val referenceExpr = methodCallExpr.getInvokedExpr match {
      case ref: ScReferenceExpression => ref
      case call: ScGenericCall => call.referencedExpr.asInstanceOf[ScReferenceExpression]
    }
    referenceExpr.qualifier.get.replaceExpression(exprA, removeParenthesis = true)
    methodCallExpr
  }

  def createEquivQualifiedReference(postfix: ScPostfixExpr): ScReferenceExpression = {
    val operand = postfix.operand
    val operandText = operand.getText
    val qualRefText = s"($operandText).${postfix.operation.getText}"
    val expr = createExpressionWithContextFromText(qualRefText, postfix.getContext, postfix).asInstanceOf[ScReferenceExpression]
    val qualWithoutPars = createExpressionWithContextFromText(operandText, postfix, operand)
    expr.qualifier.foreach(_.replaceExpression(qualWithoutPars, removeParenthesis = true))
    expr
  }
}
