package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.util

import com.intellij.lang.{ASTNode, PsiBuilderFactory}
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{Constructor, Import}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.{Block, Expr}
import org.jetbrains.plugins.scala.lang.parser.parsing.params.{ImplicitParamClause, ParamClauses, TypeParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.CaseClause
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.{ConstrExpr, Dcl, Def}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.{ClassParamClause, ClassParamClauses, ImplicitClassParamClause}
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScIdList, ScModifierList, ScPatternList, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScTypeUtil, ScalaNamesUtil}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocResolvableCodeReference, ScDocSyntaxElement}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable
import scala.reflect.ClassTag

class ScalaPsiElementFactoryImpl(implicit val ctx: ProjectContext) extends JVMElementFactory {
  def createDocCommentFromText(text: String): PsiDocComment = ???

  def isValidClassName(name: String): Boolean = isIdentifier(name)

  def isValidMethodName(name: String): Boolean = isIdentifier(name)

  def isValidParameterName(name: String): Boolean = isIdentifier(name)

  def isValidFieldName(name: String): Boolean = isIdentifier(name)

  def isValidLocalVariableName(name: String): Boolean = isIdentifier(name)

  def createConstructor(name: String, context: PsiElement): PsiMethod = ???

  def createParameter(name: String, `type`: PsiType, context: PsiElement): PsiParameter = ???

  def createClass(name: String): PsiClass = throw new IncorrectOperationException

  def createInterface(name: String): PsiClass = throw new IncorrectOperationException

  def createEnum(name: String): PsiClass = throw new IncorrectOperationException

  def createField(name: String, `type`: PsiType): PsiField = throw new IncorrectOperationException

  def createMethod(name: String, returnType: PsiType): PsiMethod = throw new IncorrectOperationException

  def createMethod(name: String, returnType: PsiType, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  def createConstructor(): PsiMethod =
    ScalaPsiElementFactory.createMethodFromText(
      """def this() {
        |this()
        |}""".stripMargin)

  def createConstructor(name: String): PsiMethod =
    createConstructor()

  def createClassInitializer(): PsiClassInitializer = throw new IncorrectOperationException

  def createParameter(name: String, `type`: PsiType): PsiParameter = {
    val typeText = `type`.toScType().canonicalText
    ScalaPsiElementFactory.createParameterFromText(s"$name: $typeText")
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

  def createExpressionFromText(text: String, context: PsiElement): PsiElement =
    ScalaPsiElementFactory.createExpressionFromText(text, context)
}

object ScalaPsiElementFactory {

  import ScalaTokenTypes._

  def createExpressionFromText(text: String, context: PsiElement): PsiElement = {
    try {
      createExpressionWithContextFromText(text, context, context)
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Throwable => throw new IncorrectOperationException(s"Cannot create expression from text $text with context ${context.getText}", e)
    }
  }

  def createScalaFileFromText(text: String)
                             (implicit ctx: ProjectContext): ScalaFile =
    PsiFileFactory.getInstance(ctx)
      .createFileFromText(s"dummy.${ScalaFileType.INSTANCE.getDefaultExtension}", ScalaFileType.INSTANCE, convertLineSeparators(text))
      .asInstanceOf[ScalaFile]


  def createElementFromText(text: String)
                           (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(text).getFirstChild

  def createElementFromText[E <: ScalaPsiElement](text: String, returnType: Class[E])
                                                 (implicit ctx: ProjectContext): E =
    createElementFromText(text)(ctx).asInstanceOf[E]

  def createWildcardNode(implicit ctx: ProjectContext): ASTNode =
    createScalaFileFromText("import a._").getLastChild.getLastChild.getLastChild.getNode

  def createClauseFromText(clauseText: String)
                          (implicit ctx: ProjectContext): ScParameterClause = {
    val function = createMethodFromText(s"def foo$clauseText = null")
    function.paramClauses.clauses.head
  }

  def createClauseForFunctionExprFromText(clauseText: String)
                                         (implicit ctx: ProjectContext): ScParameterClause = {
    val functionExpression = createElementFromText(s"$clauseText => null", classOf[ScFunctionExpr])
    functionExpression.params.clauses.head
  }

  def createParameterFromText(paramText: String)
                             (implicit ctx: ProjectContext): ScParameter = {
    val function = createMethodFromText(s"def foo($paramText) = null")
    function.parameters.head
  }

  // Supports "_" parameter name
  def createFunctionParameterFromText(paramText: String)
                                     (implicit ctx: ProjectContext): ScParameter = {
    val function = createScalaFileFromText(s"($paramText) =>").getFirstChild.asInstanceOf[ScFunctionExpr]
    function.parameters.head
  }

  def createPatternFromText(patternText: String)
                           (implicit ctx: ProjectContext): ScPattern = {
    val matchStatement = createElementFromText(s"x match { case $patternText => }", classOf[ScMatchStmt])
    matchStatement.caseClauses.head.pattern.get
  }

  def createTypeParameterFromText(name: String)
                                 (implicit ctx: ProjectContext): ScTypeParam = {
    val function = createMethodFromText(s"def foo[$name]() = {}")
    function.typeParameters.head
  }

  def createMatch(element: String, caseClauses: Seq[String])
                 (implicit ctx: ProjectContext): ScMatchStmt = {
    val clausesText = caseClauses.mkString("{ ", "\n", " }")
    createElementFromText(s"$element match $clausesText", classOf[ScMatchStmt])
  }

  def createMethodFromText(text: String)
                          (implicit ctx: ProjectContext): ScFunction =
    createElementFromText(text, classOf[ScFunction])

  def createExpressionFromText(buffer: String)
                              (implicit ctx: ProjectContext): ScExpression = {
    val classDef = createClassDefinitionFromText(s"val b = ($buffer)")
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

  def createReferenceExpressionFromText(text: String)
                                       (implicit ctx: ProjectContext): ScReferenceExpression =
    createElementFromText(text, classOf[ScReferenceExpression])

  def createImplicitClauseFromTextWithContext(clauseText: String, context: PsiElement): ScParameterClause =
    createElementWithContext[ScParameterClause](clauseText, context, contextLastChild(context), ImplicitParamClause.parse).orNull

  def createImplicitClassParamClauseFromTextWithContext(clauseText: String, context: PsiElement): ScParameterClause =
    createElementWithContext[ScParameterClause](clauseText, context, contextLastChild(context), ImplicitClassParamClause.parse).orNull

  def createEmptyClassParamClauseWithContext(context: PsiElement): ScParameterClause =
    createElementWithContext[ScParameterClause]("()", context, contextLastChild(context), ClassParamClause.parse).orNull

  def createClassParamClausesWithContext(text: String, context: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, contextLastChild(context), ClassParamClauses.parse).orNull

  def createConstructorFromText(text: String, context: PsiElement, child: PsiElement): ScConstructor =
    createElementWithContext[ScConstructor](text, context, child, Constructor.parse).orNull

  def createParamClausesWithContext(text: String, context: PsiElement, child: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, child, ParamClauses.parse).orNull

  private def contextLastChild(context: PsiElement): PsiElement = context.stub match {
    case Some(stub) =>
      val children = stub.getChildrenStubs
      if (children.isEmpty) null
      else children.get(children.size() - 1).getPsi
    case _ => context.getLastChild
  }

  def createCaseClauseFromTextWithContext(clauseText: String, context: PsiElement, child: PsiElement): ScCaseClause =
    createElementWithContext[ScCaseClause]("case " + clauseText, context, child, CaseClause.parse).orNull

  def createAnAnnotation(name: String)
                        (implicit ctx: ProjectContext): ScAnnotation = {
    val text =
      s"""@$name
          |def foo""".stripMargin
    createElementFromText(text).getFirstChild.getFirstChild.asInstanceOf[ScAnnotation]
  }

  def createBlockExpressionWithoutBracesFromText(text: String)
                                                (implicit ctx: ProjectContext): ScBlockImpl = {
    createElement(text, Block.parse(_, hasBrace = false, needNode = true)) match {
      case b: ScBlockImpl => b
      case _ => null
    }
  }

  def createOptionExpressionFromText(text: String)
                                    (implicit ctx: ProjectContext): Option[ScExpression] = {
    val dummyFile = createScalaFileFromText(text)
    Option(dummyFile.getFirstChild).collect {
      case expression: ScExpression if expression.getNextSibling == null && !PsiTreeUtil.hasErrorElements(dummyFile) => expression
    }
  }

  def createIdentifier(name: String)
                      (implicit ctx: ProjectContext): ASTNode = {
    try {
      createScalaFileFromText(s"package ${ScalaNamesUtil.escapeKeyword(name)}").getNode
        .getLastChildNode.getLastChildNode.getLastChildNode
    }
    catch {
      case p: ProcessCanceledException => throw p
      case _: Throwable => throw new IllegalArgumentException(s"Cannot create identifier from text $name")
    }
  }

  def createModifierFromText(name: String)
                            (implicit ctx: ProjectContext): PsiElement =
    createClassDefinitionFromText(prefix = name).getModifierList.getFirstChild

  def createImportExprFromText(name: String)
                              (implicit ctx: ProjectContext): ScImportExpr =
    createScalaFileFromText(s"import ${ScalaNamesUtil.escapeKeywordsFqn(name)}")
      .getLastChild.getLastChild.asInstanceOf[ScImportExpr]

  def createImportFromText(text: String)
                          (implicit ctx: ProjectContext): ScImportStmt =
    createElementFromText(text, classOf[ScImportStmt])

  def createReferenceFromText(name: String)
                             (implicit ctx: ProjectContext): ScStableCodeReferenceElement = {
    try {
      val importStatement = createElementFromText(s"import ${ScalaNamesUtil.escapeKeywordsFqn(name)}", classOf[ScImportStmt])
      importStatement.importExprs.head.reference.orNull
    }
    catch {
      case p: ProcessCanceledException => throw p
      case _: Throwable => throw new IllegalArgumentException(s"Cannot create reference with text $name")
    }
  }

  def createDeclaration(`type`: ScType, name: String, isVariable: Boolean,
                        exprText: String, isPresentableText: Boolean = false)
                       (implicit ctx: ProjectContext): ScMember = {
    val typeText = Option(`type`).map {
      case tp if isPresentableText => tp.presentableText
      case tp => tp.canonicalText
    }.getOrElse("")
    createDeclaration(name, typeText, isVariable, createExpressionFromText(exprText))
  }

  def createDeclaration(name: String, typeName: String, isVariable: Boolean, expr: ScExpression)
                       (implicit ctx: ProjectContext): ScMember = {
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
          case block: ScBlock if !block.hasRBrace && block.statements.size != 1 =>
            s"""{
                |${block.getText}
                |}""".stripMargin
          case block @ ScBlock(st) if !block.hasRBrace => stmtText(st)
          case _ => result.getText
        }
        val arrow = ScalaPsiUtil.functionArrow(ctx)
        s"$paramText $arrow $resultText"
      case null => ""
      case _ => stmt.getText
    }
    val beforeColon = if (ScalaNamesUtil.isOpCharacter(name.last)) " " else ""
    val typeText =
      if (typeName != null && typeName != ""){
        createTypeElementFromText(typeName) //throws an exception if type name is incorrect
        s"$beforeColon: $typeName"
      }  else ""

    val text = s"${if (isVariable) "var" else "val"} $name$typeText = ${stmtText(expr)}"

    createClassDefinitionFromText(text = text).members.head match {
      case variable: ScVariable => variable
      case value: ScValue => value
    }
  }

  def createValFromVarDefinition(parameter: ScClassParameter): ScClassParameter = {
    val clauseText = replaceKeywordTokenIn(parameter).parenthesize()

    val classParameters = createClassParamClausesWithContext(clauseText, parameter).params
    classParameters.head.asInstanceOf[ScClassParameter]
  }

  def createValFromVarDefinition(variable: ScVariable): ScValue =
    createValueOrVariable(variable, kVAR, kVAL).asInstanceOf[ScValue]

  def createVarFromValDeclaration(value: ScValue): ScVariable =
    createValueOrVariable(value, kVAL, kVAR).asInstanceOf[ScVariable]

  private[this] def createValueOrVariable(valOrVar: ScValueOrVariable,
                                          fromToken: IElementType,
                                          toToken: IElementType)
                                         (implicit context: ProjectContext = valOrVar.projectContext): ScMember = {
    val text = replaceKeywordTokenIn(valOrVar, fromToken, toToken)
    createClassDefinitionFromText(text = text).members.head
  }

  private[this] def replaceKeywordTokenIn(member: ScMember,
                                          fromToken: IElementType = kVAR,
                                          toToken: IElementType = kVAL) = {
    val offset = member.findFirstChildByType(fromToken).getStartOffsetInParent
    val memberText = member.getText

    memberText.substring(0, offset) +
      toToken +
      memberText.substring(offset + fromToken.toString.length)
  }

  def createEnumerator(name: String, expr: ScExpression, typeName: String)
                      (implicit ctx: ProjectContext): ScEnumerator = {
    val typeText = Option(typeName).filter {
      _.nonEmpty
    }.map { name =>
      s": $name"
    }.getOrElse("")
    val enumText = s"$name$typeText = ${expr.getText}"

    val text =
      s"""for {
          |  i <- 1 to 239
          |  $enumText
          |}""".stripMargin
    val forStmt = createElementFromText(text, classOf[ScForStatement])
    forStmt.enumerators.flatMap {
      _.enumerators.headOption
    }.getOrElse {
      throw new IllegalArgumentException(s"Could not create enumerator from text: $enumText")
    }
  }

  def createNewLine(text: String = "\n")
                   (implicit ctx: ProjectContext): PsiElement =
    createNewLineNode(text).getPsi

  def createNewLineNode(text: String = "\n")
                       (implicit ctx: ProjectContext): ASTNode =
    createScalaFileFromText(text).getNode.getFirstChildNode

  def createBlockFromExpr(expr: ScExpression)
                         (implicit ctx: ProjectContext): ScExpression =
    getExprFromFirstDef(
      s"""val b = {
          |${expr.getText}
          |}""".stripMargin)

  def createAnonFunBlockFromFunExpr(expr: ScFunctionExpr)
                                   (implicit ctx: ProjectContext): ScExpression =
    getExprFromFirstDef(
      s"""val b = {${expr.params.getText}=>
          |${expr.result.map(_.getText).getOrElse("")}
          |}""".stripMargin)

  def createPatternDefinition(text: String)
                             (implicit context: ProjectContext): ScPatternDefinition =
    createClassDefinitionFromText(text).members.head.asInstanceOf[ScPatternDefinition]

  private def getExprFromFirstDef(text: String)
                                 (implicit context: ProjectContext): ScExpression =
    createPatternDefinition(text).expr.getOrElse {
      throw new IllegalArgumentException("Expression not found")
    }

  def createBodyFromMember(elementText: String)
                          (implicit ctx: ProjectContext): ScTemplateBody =
    createClassDefinitionFromText(text = elementText).extendsBlock.templateBody.orNull

  def createTemplateBody(implicit ctx: ProjectContext): ScTemplateBody =
    createBodyFromMember("")

  def createClassTemplateParents(superName: String)
                                (implicit ctx: ProjectContext): (PsiElement, ScTemplateParents) = {
    val text =
      s"""class a extends $superName {
          |}""".stripMargin
    val extendsBlock = createScalaFileFromText(text).typeDefinitions.head.extendsBlock
    (extendsBlock.findFirstChildByType(kEXTENDS), extendsBlock.templateParents.get)
  }

  def createMethodFromSignature(signature: PhysicalSignature, needsInferType: Boolean, body: String,
                                withComment: Boolean = true, withAnnotation: Boolean = true)
                               (implicit ctx: ProjectContext): ScFunction = {
    val signatureText = methodFromSignatureText(signature, needsInferType, body, withComment, withAnnotation)
    createClassDefinitionFromText(text = signatureText).functions.head
  }

  def createOverrideImplementMethod(signature: PhysicalSignature,
                                    needsOverrideModifier: Boolean, body: String,
                                    withComment: Boolean = true, withAnnotation: Boolean = true)
                                   (implicit ctx: ProjectContext): ScFunction = {
    val function = createMethodFromSignature(signature, needsInferType = true, body, withComment, withAnnotation)
    addModifiersFromSignature(function, signature, needsOverrideModifier)
  }

  def createOverrideImplementType(alias: ScTypeAlias,
                                  substitutor: ScSubstitutor,
                                  needsOverrideModifier: Boolean,
                                  comment: String = "")
                                 (implicit ctx: ProjectContext): ScTypeAlias = {
    val typeSign = getOverrideImplementTypeSign(alias, substitutor, needsOverrideModifier)
    createClassDefinitionFromText(text = s"$comment $typeSign").aliases.head
  }

  def createOverrideImplementVariable(variable: ScTypedDefinition,
                                      substitutor: ScSubstitutor,
                                      needsOverrideModifier: Boolean,
                                      isVal: Boolean,
                                      comment: String = "",
                                      withBody: Boolean = true)
                                     (implicit ctx: ProjectContext): ScMember = {
    val variableSign = getOverrideImplementVariableSign(variable, substitutor, if (withBody) Some("_") else None, needsOverrideModifier, isVal, needsInferType = true)
    createClassDefinitionFromText(text = s"$comment $variableSign").members.head
  }

  def createOverrideImplementVariableWithClass(variable: ScTypedDefinition,
                                               substitutor: ScSubstitutor,
                                               needsOverrideModifier: Boolean,
                                               isVal: Boolean,
                                               clazz: ScTemplateDefinition,
                                               comment: String = "",
                                               withBody: Boolean = true)(implicit ctx: ProjectContext): ScMember = {
    val member = createOverrideImplementVariable(variable, substitutor, needsOverrideModifier, isVal, comment, withBody)
    Option(clazz).collect { case td: ScTypeDefinition => member.setSyntheticContainingClass(td) }
    member
  }

  def createSemicolon(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(";").findElementAt(0)

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

  private def methodFromSignatureText(sign: PhysicalSignature, needsInferType: Boolean, inBody: String,
                                      withComment: Boolean = true, withAnnotation: Boolean = true): String = {

    def methodName(method: PsiMethod): String = "def " + ScalaNamesUtil.escapeKeyword(method.name)

    def docComment(method: PsiMethod): String =
      method.firstChild.collect{case dc: PsiDocComment if withComment => dc}.map(_.getText + "\n").getOrElse("")

    val builder = mutable.StringBuilder.newBuilder
    val method = sign.method
    // do not substitute aliases
    val substitutor = sign.substitutor

    implicit val project = sign.projectContext

    method match {
      case method: ScFunction =>
        def annotations: String =
          if (withAnnotation && method.annotations.nonEmpty) method.annotations.map(_.getText).mkString("\n") + "\n" else ""

        def typeParams: String = {
          if (method.typeParameters.nonEmpty) {
            def buildText(typeParam: ScTypeParam): String = {
              val variance = if (typeParam.isContravariant) "-" else if (typeParam.isCovariant) "+" else ""
              val clauseText = typeParam.typeParametersClause match {
                case None => ""
                case Some(x) => x.typeParameters.map(buildText).mkString("[", ",", "]")
              }
              val lowerBoundText = typeParam.lowerBound.toOption collect {
                case t if t.isNothing => ""
                case x => " >: " + substitutor.subst(x).canonicalText
              }
              val upperBoundText = typeParam.upperBound.toOption collect {
                case t if t.isAny => ""
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
            typeParamTexts.mkString("[", ", ", "]")
          } else ""
        }

        def paramClauses: String = {
          if (method.paramClauses != null) {
            val localBuilder = mutable.StringBuilder.newBuilder
            for (paramClause <- method.paramClauses.clauses) {
              def buildText(param: ScParameter): String = {
                val name = param.name
                param.typeElement match {
                  case Some(x) =>
                    val colon = this.colon(name)
                    val typeText = substitutor.subst(x.`type`().getOrAny).canonicalText
                    val arrow = ScalaPsiUtil.functionArrow(param.getProject)
                    name + colon + (if (param.isCallByNameParameter) arrow else "") + typeText + (if (param.isRepeatedParameter) "*" else "")
                  case _ => name
                }
              }

              val params = for (t <- paramClause.parameters) yield buildText(t)
              localBuilder ++= params.mkString(if (paramClause.isImplicit) "(implicit " else "(", ", ", ")")
            }

            localBuilder.toString()
          } else ""
        }

        def body: String = {
          val retType = method.returnType.toOption.map(t => substitutor.subst(t))
          val retAndBody = (needsInferType, retType) match {
            case (true, Some(scType)) =>
              var text = scType.canonicalText
              if (text == "_root_.java.lang.Object") text = "AnyRef"
              val colon = this.colon(method.name, flag = method.paramClauses.clauses.isEmpty && method.typeParameters.isEmpty)
              s"$colon$text = $inBody"
            case _ =>
              " = " + inBody
          }
          retAndBody
        }

        builder ++= s"${docComment(method)}$annotations${methodName(method)}$typeParams$paramClauses$body"
      case _ =>
        def typeParams: String = {
          if (method.hasTypeParameters) {
            val params = method.getTypeParameters
            val strings = for (param <- params) yield {
              val extendsTypes = param.getExtendsListTypes
              val extendsTypesText = if (extendsTypes.nonEmpty) {
                val typeTexts = extendsTypes.map((t: PsiClassType) =>
                  substitutor.subst(t.toScType()).canonicalText)
                typeTexts.mkString(" <: ", " with ", "")
              } else ""
              param.name + extendsTypesText
            }
            strings.mkString("[", ", ", "]")
          } else ""
        }

        def paramsList = {
          import org.jetbrains.plugins.scala.extensions._

          val paramCount = method.getParameterList.getParametersCount
          val omitParamList = paramCount == 0 && method.hasQueryLikeName

          if (!omitParamList) {
            val params = for (param <- method.parameters) yield {
              val paramName = param.name match {
                case null => param match {
                  case param: ClsParameterImpl => param.getStub.getName
                  case _ => null
                }
                case x => x
              }
              val pName: String = ScalaNamesUtil.escapeKeyword(paramName)
              val colon = if (pName.endsWith("_")) " : " else ": "
              val scType: ScType = substitutor.subst(param.getTypeElement.getType.toScType())
              val typeText = scType match {
                case t if t.isAnyRef => "scala.Any"
                case JavaArrayType(argument) if param.isVarArgs => argument.canonicalText + "*"
                case _ => scType.canonicalText
              }
              s"$pName$colon$typeText"
            }

            params.mkString("(", ", ", ")")
          } else ""
        }

        def body: String = {
          val retType = substitutor.subst(method.getReturnType.toScType())
          val retAndBody =
            if (needsInferType) {
              val typeText = if (retType.isAny) "AnyRef" else retType.canonicalText
              s": $typeText = $inBody"
            } else " = " + inBody
          retAndBody
        }

        builder ++= s"${docComment(method)}${methodName(method)}$typeParams$paramsList$body"
    }
    builder.toString()
  }

  def getOverrideImplementTypeSign(alias: ScTypeAlias, substitutor: ScSubstitutor, needsOverride: Boolean): String = {
    try {
      alias match {
        case alias: ScTypeAliasDefinition =>
          val overrideText = if (needsOverride && !alias.hasModifierProperty("override")) "override " else ""
          val modifiersText = alias.getModifierList.getText
          val typeText = substitutor.subst(alias.aliasedType.getOrAny).canonicalText
          s"$overrideText$modifiersText type ${alias.name} = $typeText"
        case alias: ScTypeAliasDeclaration =>
          val overrideText = if (needsOverride) "override " else ""
          s"$overrideText${alias.getModifierList.getText} type ${alias.name} = this.type"
      }
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Exception =>
        e.printStackTrace()
        ""
    }
  }

  private def colon(name: String, flag: Boolean = true) =
    (if (flag && isIdentifier(s"$name:")) " " else "") + ": "

  private def getOverrideImplementVariableSign(variable: ScTypedDefinition, substitutor: ScSubstitutor,
                                               body: Option[String], needsOverride: Boolean,
                                               isVal: Boolean, needsInferType: Boolean): String = {
    val modOwner: ScModifierListOwner = ScalaPsiUtil.nameContext(variable) match {case m: ScModifierListOwner => m case _ => null}
    val overrideText = if (needsOverride && (modOwner == null || !modOwner.hasModifierProperty("override"))) "override " else ""
    val modifiersText = if (modOwner != null) modOwner.getModifierList.getText + " " else ""
    val keyword = if (isVal) "val " else "var "
    val name = variable.name
    val colon = this.colon(name)
    val typeText = if (needsInferType)
      substitutor.subst(variable.`type`().getOrAny).canonicalText else ""
    s"$overrideText$modifiersText$keyword$name$colon$typeText${body.map(x => " = " + x).getOrElse("")}"
  }

  def getStandardValue(`type`: ScType): String = {
    val stdTypes = `type`.projectContext.stdTypes
    import stdTypes._

    `type` match {
      case Unit => "()"
      case Boolean => "false"
      case Char | Int | Byte => "0"
      case Long => "0L"
      case Float | Double => "0.0"
      case ScDesignatorType(c: PsiClass) if c.qualifiedName == "java.lang.String" => "\"\""
      case _ => "null"
    }
  }

  def createTypeFromText(text: String, context: PsiElement, child: PsiElement): Option[ScType] = {
    val typeElement = createTypeElementFromText(text, context, child)
    Option(typeElement).map {
      _.`type`().getOrAny // FIXME this should probably be a None instead of Some(Any)
    }
  }

  def createMethodWithContext(text: String, context: PsiElement, child: PsiElement): ScFunction =
    createElementWithContext[ScFunction](text, context, child, Def.parse).orNull

  def createDefinitionWithContext(text: String, context: PsiElement, child: PsiElement): ScMember =
    createElementWithContext[ScMember](text, context, child, Def.parse).orNull

  def createObjectWithContext(text: String, context: PsiElement, child: PsiElement): ScObject =
    createElementWithContext[ScObject](text, context, child, TmplDef.parse).orNull

  def createTypeDefinitionWithContext(text: String, context: PsiElement, child: PsiElement): ScTypeDefinition =
    createElementWithContext[ScTypeDefinition](text, context, child, TmplDef.parse).orNull

  def createReferenceFromText(text: String, context: PsiElement, child: PsiElement): ScStableCodeReferenceElement =
    createElementWithContext[ScStableCodeReferenceElement](text, context, child, StableId.parse(_, ScalaElementTypes.REFERENCE)).orNull

  def createExpressionWithContextFromText(text: String, context: PsiElement, child: PsiElement): ScExpression = {
    val result = createElementWithContext[ScMethodCall](s"foo($text)", context, child, Expr.parse).flatMap {
      _.argumentExpressions.headOption
    }

    withContext(result, context, child).orNull
  }

  def createConstructorBodyWithContextFromText(text: String, context: PsiElement, child: PsiElement): ScExpression =
    createElementWithContext[ScExpression](s"$text", context, child, ConstrExpr.parse).orNull

  def createElement(text: String,
                    parse: ScalaPsiBuilder => AnyVal)
                   (implicit ctx: ProjectContext): PsiElement =
    createElement(text, createScalaFileFromText(""), ctx, parse)

  def createElementWithContext[E <: ScalaPsiElement](text: String,
                                                     context: PsiElement,
                                                     child: PsiElement,
                                                     parse: ScalaPsiBuilder => AnyVal)
                                                    (implicit tag: ClassTag[E]): Option[E] = {
    val result = createElement(text, context, context.getProject, parse)(context.getManager).toOption.collect {
      case element: E => element
    }

    withContext(result, context, child)
  }

  def createEmptyModifierList(context: PsiElement): ScModifierList = {
    val parseEmptyModifier = (_: ScalaPsiBuilder).mark.done(ScalaElementTypes.MODIFIERS)
    createElementWithContext[ScModifierList]("", context, context.getFirstChild, parseEmptyModifier).orNull
  }

  private def withContext[E <: ScalaPsiElement](maybeElement: Option[E],
                                                context: PsiElement,
                                                child: PsiElement) = {
    maybeElement.foreach {
      _.setContext(context, child)
    }
    maybeElement
  }

  private def createElement[T <: AnyVal](text: String,
                                         context: PsiElement,
                                         project: Project,
                                         parse: ScalaPsiBuilder => T)
                                        (implicit ctx: ProjectContext): PsiElement = {
    val holder = DummyHolderFactory.createHolder(ctx, context).getTreeElement

    val builder = new ScalaPsiBuilderImpl(PsiBuilderFactory.getInstance
      .createBuilder(project, holder, new ScalaLexer, ScalaLanguage.INSTANCE, convertLineSeparators(text.trim)))

    val marker = builder.mark()
    parse(builder)
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    marker.done(ScalaElementTypes.FILE)

    val fileNode = builder.getTreeBuilt
    val node = fileNode.getFirstChildNode
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    node.getPsi
  }

  def createImportFromTextWithContext(text: String, context: PsiElement, child: PsiElement): ScImportStmt =
    createElementWithContext[ScImportStmt](text, context, child, Import.parse).orNull

  def createTypeElementFromText(text: String)
                               (implicit ctx: ProjectContext): ScTypeElement =
    Option(createScalaFileFromText(s"var f: $text")).map {
      _.getLastChild.getLastChild
    }.collect {
      case typeElement: ScTypeElement => typeElement
    }.getOrElse {
      throw new IncorrectOperationException(s"wrong type element to parse: $text")
    }

  def createParameterTypeFromText(text: String)(implicit ctx: ProjectContext): ScParameterType =
    createScalaFileFromText(s"(_: $text) => ())")
      .getFirstChild.asInstanceOf[ScFunctionExpr].parameters.head.paramType.get

  def createColon(implicit ctx: ProjectContext): PsiElement =
    createElementFromText("var f: Int", classOf[ScalaPsiElement]).findChildrenByType(tCOLON).head

  def createComma(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(",").findChildrenByType(tCOMMA).head

  def createAssign(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("val x = 0").findChildrenByType(tASSIGN).head

  def createWhitespace(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText("1 + 1").findElementAt(1)

  def createTypeElementFromText(text: String, context: PsiElement, child: PsiElement): ScTypeElement =
    createElementWithContext[ScTypeElement](text, context, child, Type.parse(_)).orNull

  def createTypeParameterClauseFromTextWithContext(text: String, context: PsiElement,
                                                   child: PsiElement): ScTypeParamClause =
    createElementWithContext[ScTypeParamClause](text, context, child, TypeParamClause.parse).orNull

  def createWildcardPattern(implicit ctx: ProjectContext): ScWildcardPattern = {
    val element = createElementFromText("val _ = x")
    element.getChildren.apply(2).getFirstChild.asInstanceOf[ScWildcardPattern]
  }

  def createPatterListFromText(text: String, context: PsiElement, child: PsiElement): ScPatternList = {
    val result = createElementWithContext[ScPatternDefinition](s"val $text = 239", context, child, Def.parse).map {
      _.pList
    }
    withContext(result, context, child).orNull
  }

  def createIdsListFromText(text: String, context: PsiElement, child: PsiElement): ScIdList = {
    val result = Option(createDeclarationFromText(s"val $text : Int", context, child)).collect {
      case valueDeclaration: ScValueDeclaration => valueDeclaration
    }.map {
      _.getIdList
    }
    withContext(result, context, child).orNull
  }

  def createTemplateDefinitionFromText(text: String, context: PsiElement, child: PsiElement): ScTemplateDefinition =
    createElementWithContext[ScTemplateDefinition](text, context, child, TmplDef.parse).orNull

  def createDeclarationFromText(text: String, context: PsiElement, child: PsiElement): ScDeclaration =
    createElementWithContext[ScDeclaration](text, context, child, Dcl.parse).orNull

  def createTypeAliasDefinitionFromText(text: String, context: PsiElement, child: PsiElement): ScTypeAliasDefinition =
    createElementWithContext[ScTypeAliasDefinition](text, context, child, Def.parse).orNull

  def createDocCommentFromText(text: String)
                              (implicit ctx: ProjectContext): ScDocComment =
    createClassDefinitionFromText(prefix =
      s"""/**
          |$text
          |*/""".stripMargin).docComment.orNull

  def createMonospaceSyntaxFromText(text: String)
                                   (implicit ctx: ProjectContext): ScDocSyntaxElement =
    createDocCommentFromText(s"`$text`").getChildren()(2).asInstanceOf[ScDocSyntaxElement]

  def createDocHeaderElement(length: Int)
                            (implicit ctx: ProjectContext): PsiElement =
    createClassDefinitionFromText(
      s"""/**=header${StringUtils.repeat("=", length)}*/
          |""".stripMargin).docComment.orNull
      .getNode.getChildren(null)(1).getLastChildNode.getPsi

  def createDocWhiteSpace(implicit ctx: ProjectContext): PsiElement =
    createDocCommentFromText(" *").getNode.getChildren(null)(1).getPsi

  def createLeadingAsterisk(implicit ctx: ProjectContext): PsiElement =
    createDocCommentFromText(" *").getNode.getChildren(null)(2).getPsi

  def createDocSimpleData(text: String)
                         (implicit ctx: ProjectContext): PsiElement =
    createClassDefinitionFromText(prefix = s"/**$text*/").docComment.get.getNode.getChildren(null)(1).getPsi

  def createDocTagValue(text: String)
                       (implicit ctx: ProjectContext): PsiElement =
    createClassDefinitionFromText(
      s"""/**@param $text
          |*/""".stripMargin).docComment.orNull
      .getNode.getChildren(null)(1).getChildren(null)(2).getPsi

  def createDocTagName(name: String)
                      (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("/**@" + name + " qwerty */")
      .typeDefinitions(0).docComment.get.getNode.getChildren(null)(1).getChildren(null)(0).getPsi

  def createDocLinkValue(text: String)
                        (implicit ctx: ProjectContext): ScDocResolvableCodeReference =
    createClassDefinitionFromText(prefix = s"/**[[$text]]*/").docComment.orNull
      .getNode.getChildren(null)(1).getChildren(null)(1).getPsi.asInstanceOf[ScDocResolvableCodeReference]

  def createXmlEndTag(tagName: String)
                     (implicit ctx: ProjectContext): ScXmlEndTag =
    createScalaFileFromText(s"val a = <$tagName></$tagName>")
      .getFirstChild.getLastChild.getFirstChild.getLastChild.asInstanceOf[ScXmlEndTag]

  def createXmlStartTag(tagName: String, attributes: String = "")
                       (implicit ctx: ProjectContext): ScXmlStartTag =
    createScalaFileFromText(s"val a = <$tagName$attributes></$tagName>")
      .getFirstChild.getLastChild.getFirstChild.getFirstChild.asInstanceOf[ScXmlStartTag]

  def createInterpolatedStringPrefix(prefix: String)
                                    (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(prefix + "\"blah\"").getFirstChild.getFirstChild

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

  private def createClassDefinitionFromText(text: String = "", prefix: String = "")
                                           (implicit ctx: ProjectContext): ScTypeDefinition = {
    val fileText =
      s"""$prefix${if (prefix.isEmpty) "" else " "}class a {
         |  $text
         |}""".stripMargin
    createScalaFileFromText(fileText).typeDefinitions.head
  }
}
