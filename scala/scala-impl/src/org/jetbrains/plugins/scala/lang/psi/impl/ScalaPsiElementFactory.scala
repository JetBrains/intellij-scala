package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.{util => ju}
import com.intellij.lang.{ASTNode, LanguageParserDefinitions, PsiBuilder, PsiBuilderFactory}
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
import com.intellij.psi.tree.{IElementType, IFileElementType}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.apache.commons.lang.StringUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeParamsRenderer
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocParagraph, ScDocResolvableCodeReference, ScDocSyntaxElement}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}

import scala.annotation.tailrec
import scala.reflect.ClassTag

//noinspection NotImplementedCode
final class ScalaPsiElementFactoryImpl(project: Project) extends JVMElementFactory {

  override def createDocCommentFromText(text: String): PsiDocComment = ???

  override def isValidClassName(name: String): Boolean = isIdentifier(name)

  override def isValidMethodName(name: String): Boolean = isIdentifier(name)

  override def isValidParameterName(name: String): Boolean = isIdentifier(name)

  override def isValidFieldName(name: String): Boolean = isIdentifier(name)

  override def isValidLocalVariableName(name: String): Boolean = isIdentifier(name)

  override def createConstructor(name: String, context: PsiElement): PsiMethod = ???

  override def createParameter(name: String, `type`: PsiType, context: PsiElement): PsiParameter = ???

  override def createClass(name: String): PsiClass = throw new IncorrectOperationException

  override def createInterface(name: String): PsiClass = throw new IncorrectOperationException

  override def createEnum(name: String): PsiClass = throw new IncorrectOperationException

  override def createField(name: String, `type`: PsiType): PsiField = throw new IncorrectOperationException

  override def createMethod(name: String, returnType: PsiType): PsiMethod = throw new IncorrectOperationException

  override def createMethod(name: String, returnType: PsiType, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  override def createConstructor(): PsiMethod =
    ScalaPsiElementFactory.createMethodFromText(
      """def this() {
        |this()
        |}""".stripMargin
    )(project)

  override def createConstructor(name: String): PsiMethod = createConstructor()

  override def createClassInitializer(): PsiClassInitializer = throw new IncorrectOperationException

  override def createParameter(name: String, `type`: PsiType): PsiParameter = {
    implicit val context: ProjectContext = project
    val typeText = `type`.toScType().canonicalText
    ScalaPsiElementFactory.createParameterFromText(s"$name: $typeText")
  }

  override def createParameterList(names: Array[String], types: Array[PsiType]): PsiParameterList = throw new IncorrectOperationException

  override def createMethodFromText(text: String, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  override def createAnnotationFromText(annotationText: String, context: PsiElement): PsiAnnotation = throw new IncorrectOperationException

  override def createReferenceElementByType(`type`: PsiClassType): PsiElement = ???

  override def createTypeParameterList(): PsiTypeParameterList = ???

  override def createTypeParameter(name: String, superTypes: Array[PsiClassType]): PsiTypeParameter = ???

  override def createType(aClass: PsiClass): PsiClassType = ???

  override def createAnnotationType(name: String): PsiClass = ???

  override def createType(resolve: PsiClass, substitutor: PsiSubstitutor): PsiClassType = ???

  override def createType(resolve: PsiClass, substitutor: PsiSubstitutor, languageLevel: LanguageLevel): PsiClassType = ???

  override def createType(aClass: PsiClass, parameters: PsiType): PsiClassType = ???

  override def createRawSubstitutor(owner: PsiTypeParameterListOwner): PsiSubstitutor = ???

  override def createSubstitutor(map: ju.Map[PsiTypeParameter, PsiType]): PsiSubstitutor = ???

  override def createPrimitiveType(text: String): PsiPrimitiveType = ???

  override def createTypeByFQClassName(qName: String): PsiClassType = ???

  override def createTypeByFQClassName(qName: String, resolveScope: GlobalSearchScope): PsiClassType = ???

  override def createType(aClass: PsiClass, parameters: PsiType*): PsiClassType = ???

  override def createExpressionFromText(text: String, context: PsiElement): PsiElement =
    ScalaPsiElementFactory.createExpressionFromText(text, context)
}

object ScalaPsiElementFactory {

  import ScalaPsiUtil._
  import lang.parser.parsing.{base => parsingBase, statements => parsingStat, _}
  import lexer.ScalaTokenTypes._
  import refactoring.util.ScalaNamesUtil._

  def safe[T](createBody: ScalaPsiElementFactory.type => T): Option[T] =
    try Some(createBody(ScalaPsiElementFactory)) catch {
      case _: ScalaPsiElementCreationException => None
    }

  def createExpressionFromText(@NonNls text: String, context: PsiElement): ScExpression = {
    try {
      createExpressionWithContextFromText(text, context, context)
    } catch {
      case p: ProcessCanceledException => throw p
      case throwable: Throwable => throw elementCreationException("expression", text, context, throwable)
    }
  }

  def createScalaFileFromText(@NonNls text: String)
                             (implicit ctx: ProjectContext): ScalaFile =
    PsiFileFactory.getInstance(ctx)
      .createFileFromText(
        s"dummy.${ScalaFileType.INSTANCE.getDefaultExtension}",
        if (ctx.project.hasScala3) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE,
        convertLineSeparators(text),
        false, true
      )
      .asInstanceOf[ScalaFile]


  def createElementFromText(@NonNls text: String)
                           (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(text).getFirstChild

  def createScalaElementFromText[E <: ScalaPsiElement](text: String)
                                                      (implicit ctx: ProjectContext): E =
    createElementFromText(text)(ctx).asInstanceOf[E]

  def createWildcardNode(implicit ctx: ProjectContext): ASTNode =
    createScalaFileFromText("import a._").getLastChild.getLastChild.getLastChild.getNode

  def createClauseFromText(@NonNls clauseText: String = "()")
                          (implicit ctx: ProjectContext): ScParameterClause = {
    val function = createMethodFromText(s"def foo$clauseText = null")
    function.paramClauses.clauses.head
  }

  def createClauseForFunctionExprFromText(@NonNls clauseText: String)
                                         (implicit ctx: ProjectContext): ScParameterClause = {
    val functionExpression = createScalaElementFromText[ScFunctionExpr](s"$clauseText => null")
    functionExpression.params.clauses.head
  }

  def createParameterFromText(@NonNls paramText: String)
                             (implicit ctx: ProjectContext): ScParameter = {
    val function = createMethodFromText(s"def foo($paramText) = null")
    function.parameters.head
  }

  // Supports "_" parameter name
  def createFunctionParameterFromText(@NonNls paramText: String)
                                     (implicit ctx: ProjectContext): ScParameter = {
    val function = createScalaFileFromText(s"($paramText) =>").getFirstChild.asInstanceOf[ScFunctionExpr]
    function.parameters.head
  }

  def createPatternFromText(@NonNls patternText: String)
                           (implicit ctx: ProjectContext): ScPattern = {
    val matchStatement = createScalaElementFromText[ScMatch](s"x match { case $patternText => }")
    matchStatement.clauses.head.pattern.get
  }

  def createTypeParameterFromText(@NonNls name: String)
                                 (implicit ctx: ProjectContext): ScTypeParam = {
    val function = createMethodFromText(s"def foo[$name]() = {}")
    function.typeParameters.head
  }

  def createMatch(@NonNls element: String, caseClauses: Seq[String])
                 (implicit ctx: ProjectContext): ScMatch = {
    val clausesText = caseClauses.mkString("{ ", "\n", " }")
    createScalaElementFromText[ScMatch](s"$element match $clausesText")
  }

  def createMethodFromText(@NonNls text: String)
                          (implicit ctx: ProjectContext): ScFunction =
    createScalaElementFromText[ScFunction](text)

  def createExpressionFromText(@NonNls text: String)
                              (implicit context: ProjectContext): ScExpression =
    getExprFromFirstDef(s"val b = ($text)") match {
      case ScParenthesisedExpr(e) => e
      case e => e
    }

  def createReferenceExpressionFromText(@NonNls text: String)
                                       (implicit ctx: ProjectContext): ScReferenceExpression =
    createScalaElementFromText[ScReferenceExpression](text)

  def createImplicitClauseFromTextWithContext(clauses: Iterable[String],
                                              context: PsiElement,
                                              isClassParameter: Boolean): ScParameterClause =
    if (clauses.isEmpty)
      throw new IncorrectOperationException("At least one clause required.")
    else
      createElementWithContext[ScParameterClause](s"(implicit ${clauses.commaSeparated()})", context, contextLastChild(context)) {
        case builder if isClassParameter => top.params.ImplicitClassParamClause.parse(builder)
        case builder => params.ImplicitParamClause.parse(builder)
      }


  def createEmptyClassParamClauseWithContext(context: PsiElement): ScParameterClause =
    createElementWithContext[ScParameterClause]("()", context, contextLastChild(context))(top.params.ClassParamClause.parse)

  def createClassParamClausesWithContext(@NonNls text: String, context: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, contextLastChild(context))(ClassParamClauses()(_))

  def createConstructorFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScConstructorInvocation =
    createElementWithContext[ScConstructorInvocation](text, context, child){
      implicit builder =>
        // Disable newlines because otherwise we cannot parse constructor-invocations
        // that have multiple argument clauses over multiple lines (which is possible in new-expr), like:
        //    (new Test()
        //      ())
        builder.withDisabledNewlines {
          parsingBase.Constructor()
        }
    }

  def createParamClausesWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, child)(params.ParamClauses.parse)

  private def contextLastChild(element: PsiElement): PsiElement =
    stub(element)
      .map(_.getChildrenStubs)
      .fold(element.getLastChild) {
        at(_)().orNull
      }

  def createPatternFromTextWithContext(@NonNls patternText: String, context: PsiElement, child: PsiElement): ScPattern =
    createElementWithContext[ScCaseClause](kCASE.toString + " " + patternText, context, child)(patterns.CaseClause.parse(_))
      .pattern
      .getOrElse {
        throw elementCreationException("pattern", patternText, context)
      }

  def createAnAnnotation(@NonNls name: String)
                        (implicit ctx: ProjectContext): ScAnnotation = {
    val text =
      s"""@$name
         |def foo""".stripMargin
    createElementFromText(text).getFirstChild.getFirstChild.asInstanceOf[ScAnnotation]
  }

  def createAnnotationExpression(@NonNls text: String)
                                (implicit ctx: ProjectContext): ScAnnotationExpr =
    createElement(text)(expressions.AnnotationExpr.parse)
      .asInstanceOf[ScAnnotationExpr]

  def createBlockExpressionWithoutBracesFromText(@NonNls text: String)
                                                (implicit ctx: ProjectContext): ScBlockImpl = {
    createElement(text)(expressions.Block.parse(_, hasBrace = false, needNode = true)) match {
      case b: ScBlockImpl => b
      case _ => null
    }
  }

  def createOptionExpressionFromText(@NonNls text: String)
                                    (implicit ctx: ProjectContext): Option[ScExpression] = {
    val dummyFile = createScalaFileFromText(text)
    Option(dummyFile.getFirstChild).collect {
      case expression: ScExpression if expression.getNextSibling == null && !PsiTreeUtil.hasErrorElements(dummyFile) => expression
    }
  }

  def createIdentifier(@NonNls name: String)
                      (implicit ctx: ProjectContext): ASTNode = {
    try {
      createScalaFileFromText(s"package ${escapeKeyword(name)}").getNode
        .getLastChildNode.getLastChildNode.getLastChildNode
    }
    catch {
      case p: ProcessCanceledException => throw p
      case throwable: Throwable => throw elementCreationException("identifier", name, cause = throwable)
    }
  }

  def createModifierFromText(@NonNls modifier: String)
                            (implicit context: ProjectContext): PsiElement =
    createScalaFileFromText(s"$modifier class a").typeDefinitions.head.getModifierList.getFirstChild

  def createImportExprWithContextFromText(@NonNls name: String, context: PsiElement): ScImportExpr =
    createImportFromText(s"import ${escapeKeywordsFqn(name)}", context)
      .getLastChild.asInstanceOf[ScImportExpr]

  def createImportFromText(@NonNls text: String, context: PsiElement): ScImportStmt =
    createElementWithContext[ScImportStmt](text, context, null)(Import.parse(_))

  def createReferenceFromText(@NonNls name: String)
                             (implicit ctx: ProjectContext): ScStableCodeReference = {
    try {
      val importStatement = createScalaElementFromText[ScImportStmt](s"import ${escapeKeywordsFqn(name)}")
      importStatement.importExprs.head.reference.orNull
    }
    catch {
      case p: ProcessCanceledException => throw p
      case throwable: Throwable => throw elementCreationException("reference", name, cause = throwable)
    }
  }

  def createDeclaration(`type`: ScType, @NonNls name: String, isVariable: Boolean,
                        @NonNls exprText: String, isPresentableText: Boolean = false)
                       (implicit tpc: TypePresentationContext, context: ProjectContext): ScValueOrVariable = {
    val typeText = `type` match {
      case null => ""
      case tp if isPresentableText => tp.presentableText
      case tp => tp.canonicalText
    }

    createDeclaration(name, typeText, isVariable, createExpressionFromText(exprText))
  }

  def createDeclaration(@NonNls name: String, @NonNls typeName: String, isVariable: Boolean, body: ScExpression)
                       (implicit context: ProjectContext): ScValueOrVariable =
    createMember(name, typeName, body, isVariable = isVariable).asInstanceOf[ScValueOrVariable]

  private[this] def createMember(@NonNls name: String, @NonNls typeName: String, body: ScExpression,
                                 modifiers: String = "",
                                 isVariable: Boolean = false)
                                (implicit context: ProjectContext): ScMember = {
    def stmtText(expr: ScBlockStatement): String = expr match {
      case block@ScBlock(st) if !block.hasRBrace =>
        stmtText(st)
      case fun@ScFunctionExpr(parSeq, Some(result)) =>
        val paramText = parSeq match {
          case Seq(parameter) if parameter.typeElement.isDefined && parameter.getPrevSiblingNotWhitespace == null =>
            parameter.getText.parenthesize()
          case _ => fun.params.getText
        }

        val resultText = result match {
          case block: ScBlock if !block.hasRBrace && block.statements.size != 1 =>
            // see ScalaPsiElementFactory.createClassWithBody comment
            s"{\n${block.getText}\n}"
          case block@ScBlock(st) if !block.hasRBrace => stmtText(st)
          case _ => result.getText
        }
        s"$paramText $functionArrow $resultText"
      case null => ""
      case statement => statement.getText
    }

    val typedName = typeName match {
      case null | "" => name
      case _ =>
        // throws an exception if type name is incorrect
        createTypeElementFromText(typeName)

        val space = if (isOpCharacter(name.last)) " " else ""
        s"$name$space: $typeName"
    }

    val text = s"$modifiers${if (modifiers.isEmpty) "" else " "}${if (isVariable) kVAR else kVAL} $typedName = ${stmtText(body)}"

    createMemberFromText(text)
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
                                         (implicit context: ProjectContext = valOrVar.projectContext): ScMember =
    createMemberFromText(replaceKeywordTokenIn(valOrVar, fromToken, toToken))

  private[this] def replaceKeywordTokenIn(member: ScMember,
                                          fromToken: IElementType = kVAR,
                                          toToken: IElementType = kVAL) = {
    val offset = member.findFirstChildByType(fromToken).get.getStartOffsetInParent
    val memberText = member.getText

    memberText.substring(0, offset) +
      toToken +
      memberText.substring(offset + fromToken.toString.length)
  }

  def createForBinding(@NonNls name: String, expr: ScExpression, @NonNls typeName: String)
                      (implicit ctx: ProjectContext): ScForBinding = {
    val typeText = Option(typeName).filter {
      _.nonEmpty
    }.map { name =>
      s": $name"
    }.getOrElse("")
    val enumText = s"$name$typeText = ${expr.getText}"
    // see ScalaPsiElementFactory.createClassWithBody comment
    val text = s"for {\n  i <- 1 to 239\n  $enumText\n}"
    val forStmt = createScalaElementFromText[ScFor](text)
    forStmt.enumerators.flatMap {
      _.forBindings.headOption
    }.getOrElse {
      throw elementCreationException("enumerator", enumText)
    }
  }

  def createNewLine(@NonNls text: String = "\n")
                   (implicit context: ProjectContext): PsiElement =
    createNewLineNode(text).getPsi

  def createNewLineNode(@NonNls text: String = "\n")
                       (implicit context: ProjectContext): ASTNode =
    createScalaFileFromText(text).getNode.getFirstChildNode

  def createBlockFromExpr(expression: ScExpression)
                         (implicit context: ProjectContext): ScExpression = {
    // see ScalaPsiElementFactory.createClassWithBody comment
    val definition = s"val b = {\n${expression.getText}\n}"
    getExprFromFirstDef(definition)
  }

  def createAnonFunBlockFromFunExpr(expression: ScFunctionExpr)
                                   (implicit context: ProjectContext): ScExpression = {
    val params = expression.params.getText
    val body = expression.result.map(_.getText).getOrElse("")
    // see ScalaPsiElementFactory.createClassWithBody comment
    val definition = s"val b = {$params=>\n$body\n}"
    getExprFromFirstDef(definition)
  }

  def createPatternDefinition(@NonNls name: String, @NonNls typeName: String, body: ScExpression,
                              @NonNls modifiers: String = "",
                              isVariable: Boolean = false)
                             (implicit context: ProjectContext): ScPatternDefinition =
    createMember(name, typeName, body, modifiers, isVariable).asInstanceOf[ScPatternDefinition]

  private[this] def getExprFromFirstDef(@NonNls text: String)
                                       (implicit context: ProjectContext): ScExpression =
    createMemberFromText(text) match {
      case ScPatternDefinition.expr(body) => body
      case _ => throw new IncorrectOperationException("Expression not found")
    }

  def createBodyFromMember(@NonNls elementText: String)
                          (implicit ctx: ProjectContext): ScTemplateBody =
    createClassWithBody(elementText).extendsBlock.templateBody.orNull

  def createTemplateBody(implicit ctx: ProjectContext): ScTemplateBody =
    createBodyFromMember("")

  def createClassTemplateParents(@NonNls superName: String)
                                (implicit ctx: ProjectContext): (PsiElement, ScTemplateParents) = {
    val text =
      s"""class a extends $superName {
         |}""".stripMargin
    val extendsBlock = createScalaFileFromText(text).typeDefinitions.head.extendsBlock
    (extendsBlock.findFirstChildByType(kEXTENDS).get, extendsBlock.templateParents.get)
  }

  def createMethodFromSignature(signature: PhysicalMethodSignature, @NonNls body: String,
                                withComment: Boolean = true, withAnnotation: Boolean = true)
                               (implicit projectContext: ProjectContext): ScFunction = {
    val builder = new StringBuilder()

    val PhysicalMethodSignature(method, substitutor) = signature

    if (withComment) {
      val maybeCommentText = method.firstChild.collect {
        case comment: PsiDocComment => comment.getText
      }

      maybeCommentText.foreach(builder.append)
      if (maybeCommentText.isDefined) builder.append("\n")
    }

    if (withAnnotation) {
      val annotations = method match {
        case function: ScFunction => function.annotations.map(_.getText)
        case _ => Seq.empty
      }

      annotations.foreach(builder.append)
      if (annotations.nonEmpty) builder.append("\n")
    }

    signatureText(method, substitutor)(builder)

    builder.append(" ")
      .append(tASSIGN)
      .append(" ")
      .append(body)

    createClassWithBody(builder.toString()).functions.head
  }

  def createOverrideImplementMethod(signature: PhysicalMethodSignature, needsOverrideModifier: Boolean, @NonNls body: String,
                                    withComment: Boolean = true, withAnnotation: Boolean = true)
                                   (implicit ctx: ProjectContext): ScFunction = {
    val function = createMethodFromSignature(signature, body, withComment, withAnnotation)
    addModifiersFromSignature(function, signature, needsOverrideModifier)
  }

  def createOverrideImplementType(alias: ScTypeAlias,
                                  substitutor: ScSubstitutor,
                                  needsOverrideModifier: Boolean,
                                  @NonNls comment: String = "")
                                 (implicit ctx: ProjectContext): ScTypeAlias = {
    val typeSign = getOverrideImplementTypeSign(alias, substitutor, needsOverrideModifier)
    createClassWithBody(s"$comment $typeSign").aliases.head
  }

  def createOverrideImplementVariable(variable: ScTypedDefinition,
                                      substitutor: ScSubstitutor,
                                      needsOverrideModifier: Boolean,
                                      isVal: Boolean,
                                      @NonNls comment: String = "",
                                      withBody: Boolean = true)
                                     (implicit ctx: ProjectContext): ScMember = {
    val variableSign = getOverrideImplementVariableSign(variable, substitutor, if (withBody) Some("_") else None, needsOverrideModifier, isVal, needsInferType = true)
    createMemberFromText(s"$comment $variableSign")
  }

  def createOverrideImplementVariableWithClass(variable: ScTypedDefinition,
                                               substitutor: ScSubstitutor,
                                               needsOverrideModifier: Boolean,
                                               isVal: Boolean,
                                               clazz: ScTemplateDefinition,
                                               @NonNls comment: String = "",
                                               withBody: Boolean = true)(implicit ctx: ProjectContext): ScMember = {
    val member = createOverrideImplementVariable(variable, substitutor, needsOverrideModifier, isVal, comment, withBody)
    clazz match {
      case td: ScTypeDefinition => member.syntheticContainingClass = td
      case _ =>
    }
    member
  }

  def createSemicolon(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(";").findElementAt(0)

  private def addModifiersFromSignature(function: ScFunction, sign: PhysicalMethodSignature, addOverride: Boolean): ScFunction = {
    sign.method match {
      case fun: ScFunction =>
        import lexer.ScalaModifier._
        val res = function.getModifierList.replace(fun.getModifierList)
        if (res.getText.nonEmpty) res.getParent.addAfter(createWhitespace(fun.getManager), res)
        function.setModifierProperty(ABSTRACT, value = false)
        if (!fun.hasModifierProperty("override") && addOverride) function.setModifierProperty(OVERRIDE)
      case m: PsiMethod =>
        var hasOverride = false
        if (m.getModifierList.getNode != null)
          for (modifier <- m.getModifierList.getNode.getChildren(null); modText = modifier.getText) {
            modText match {
              case "override" => hasOverride = true; function.setModifierProperty("override")
              case "protected" => function.setModifierProperty("protected")
              case "final" => function.setModifierProperty("final")
              case _ =>
            }
          }
        if (addOverride && !hasOverride) function.setModifierProperty("override")
    }
    function
  }

  private def signatureText(method: PsiMethod, substitutor: ScSubstitutor)
                           (myBuilder: StringBuilder)
                           (implicit projectContext: ProjectContext): Unit = {
    myBuilder.append(kDEF)
      .append(" ")
      .append(escapeKeyword(method.name))

    val typeParameters = method match {
      case function: ScFunction if function.typeParameters.nonEmpty =>
        val renderer = new TypeParamsRenderer(substitutor(_).canonicalText, stripContextTypeArgs = true)

        def buildText(typeParam: ScTypeParam): String =
          renderer.render(typeParam)

        function.typeParameters.map(buildText)
      case _ if method.hasTypeParameters =>
        for {
          param <- method.getTypeParameters.toSeq
          extendsTypes = param.getExtendsListTypes
          extendsTypesText = if (extendsTypes.nonEmpty) {
            extendsTypes.map { classType =>
              substitutor(classType.toScType()).canonicalText
            }.mkString(" <: ", " with ", "")
          } else ""
        } yield param.name + extendsTypesText
      case _ => Seq.empty
    }

    if (typeParameters.nonEmpty) {
      val typeParametersText = typeParameters.mkString(tLSQBRACKET.toString, ", ", tRSQBRACKET.toString)
      myBuilder.append(typeParametersText)
    }

    // do not substitute aliases
    method match {
      case method: ScFunction if method.paramClauses != null =>
        for (paramClause <- method.paramClauses.clauses) {
          val parameters = paramClause.parameters.map { param =>
            val arrow = if (param.isCallByNameParameter) functionArrow else ""
            val asterisk = if (param.isRepeatedParameter) "*" else ""

            val name = param.name
            val tpe = param.`type`().map(substitutor).getOrAny

            s"$name${colon(name)} $arrow${tpe.canonicalText}$asterisk"
          }

          myBuilder.append(parameters.mkString(if (paramClause.isImplicit) "(implicit " else "(", ", ", ")"))
        }
      case _ if !method.isParameterless || !method.hasQueryLikeName =>
        val params = for (param <- method.parameters) yield {
          val paramName = param.name match {
            case null => param match {
              case param: ClsParameterImpl => param.getStub.getName
              case _ => null
            }
            case x => x
          }

          val pName: String = escapeKeyword(paramName)
          val colon = if (pName.endsWith("_")) " " else ""
          val paramType = {
            val tpe = param.paramType()
            substitutor(tpe)
          }

          val asterisk = if (param.isVarArgs) "*" else ""

          val typeText = paramType match {
            case t if t.isAnyRef => "scala.Any"
            case t => t.canonicalText
          }

          s"$pName$colon: $typeText$asterisk"
        }

        myBuilder.append(params.mkString("(", ", ", ")"))
      case _ =>
    }

    val maybeReturnType = method match {
      case function: ScFunction =>
        function.returnType.toOption.map {
          (_, function.isParameterless && function.typeParameters.isEmpty && isIdentifier(method.name + tCOLON))
        }
      case _ =>
        Option(method.getReturnType).map { returnType =>
          (returnType.toScType(), false)
        }
    }

    maybeReturnType match {
      case Some((returnType, flag)) =>
        val typeText = substitutor(returnType).canonicalText match {
          case "_root_.java.lang.Object" => "AnyRef"
          case text => text
        }

        myBuilder.append(if (flag) " " else "")
          .append(tCOLON)
          .append(" ")
          .append(typeText)
      case _ =>
    }
  }

  def getOverrideImplementTypeSign(alias: ScTypeAlias, substitutor: ScSubstitutor, needsOverride: Boolean): String = {
    try {
      alias match {
        case alias: ScTypeAliasDefinition =>
          val overrideText = if (needsOverride && !alias.hasModifierProperty("override")) "override " else ""
          val modifiersText = alias.getModifierList.getText
          val typeText = substitutor(alias.aliasedType.getOrAny).canonicalText
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

  private def colon(name: String) = (if (isIdentifier(name + tCOLON)) " " else "") + tCOLON + " "

  private def getOverrideImplementVariableSign(variable: ScTypedDefinition, substitutor: ScSubstitutor,
                                               body: Option[String], needsOverride: Boolean,
                                               isVal: Boolean, needsInferType: Boolean): String = {
    val modOwner: ScModifierListOwner = nameContext(variable) match {
      case m: ScModifierListOwner => m
      case _ => null
    }
    val overrideText = if (needsOverride && (modOwner == null || !modOwner.hasModifierProperty("override"))) "override " else ""
    val modifiersText = if (modOwner != null) modOwner.getModifierList.getText + " " else ""
    val keyword = if (isVal) "val " else "var "
    val name = variable.name
    val colon = this.colon(name)
    val typeText = if (needsInferType)
      substitutor(variable.`type`().getOrAny).canonicalText else ""
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

  def createTypeFromText(@NonNls text: String, context: PsiElement, child: PsiElement): Option[ScType] = {
    val typeElement = createTypeElementFromText(text, context, child)
    Option(typeElement).map {
      _.`type`().getOrAny // FIXME this should probably be a None instead of Some(Any)
    }
  }

  def createMethodWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScFunction =
    createElementWithContext[ScFunction](text, context, child)(parsingStat.Def.parse(_))

  def createDefinitionWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScMember =
    createElementWithContext[ScMember](text, context, child)(parsingStat.Def.parse(_))

  def createObjectWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScObject =
    createElementWithContext[ScObject](text, context, child)(TmplDef()(_))

  def createTypeDefinitionWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScTypeDefinition =
    createElementWithContext[ScTypeDefinition](text, context, child)(TmplDef()(_))

  def createReferenceFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScStableCodeReference =
    createElementWithContext[ScStableCodeReference](text, context, child) {
      types.StableId.parse(_, parser.ScalaElementType.REFERENCE)
    }

  def createDocReferenceFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScStableCodeReference =
    createElementWithContext[ScDocResolvableCodeReference](text, context, child) {
      types.StableId.parse(_, parser.ScalaElementType.DOC_REFERENCE)
    }

  // TODO method should be eliminated eventually
  def createExpressionWithContextFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScExpression = {
    val methodCall = createElementWithContext[ScMethodCall](s"foo($text)", context, child)(expressions.Expr.parse(_))

    val firstArgument = methodCall.argumentExpressions
      .headOption
      .getOrElse {
        throw elementCreationException("expression", text, context)
      }

    firstArgument.context = context
    firstArgument.child = child
    firstArgument
  }

  def createElement(@NonNls text: String)
                   (parse: ScalaPsiBuilder => AnyVal)
                   (implicit ctx: ProjectContext): PsiElement =
    createElement(
      text,
      createScalaFileFromText("")
    )(parse)(ctx.project)

  private def createElementWithContext[E <: ScalaPsiElement](@NonNls text: String,
                                                             context: PsiElement,
                                                             child: PsiElement)
                                                            (parse: ScalaPsiBuilder => AnyVal)
                                                            (implicit tag: ClassTag[E]): E = {
    implicit val project: Project = (if (context == null) child else context).getProject
    createElement(text, context, checkLength = true)(parse) match {
      case element: E =>
        element.context = context
        element.child = child
        element
      case element => throw elementCreationException(tag.runtimeClass.getSimpleName, text + "; actual: " + element.getText, context)
    }
  }

  def createEmptyModifierList(context: PsiElement): ScModifierList =
    createElementWithContext[ScModifierList]("", context, context.getFirstChild) {
      _.mark().done(parser.ScalaElementType.MODIFIERS)
    }

  private def createElement[T <: AnyVal](@NonNls text: String, context: PsiElement,
                                         checkLength: Boolean = false)
                                        (parse: ScalaPsiBuilder => T)
                                        (implicit project: Project): PsiElement = {
    val chameleon = DummyHolderFactory.createHolder(
      PsiManager.getInstance(project),
      context
    ).getTreeElement

    val contextLanguage = context.getLanguage
    // can't use `contextLanguage` directly because for example ScalaDocLanguage is also kind of ScalaLanguage
    val language =
      if (contextLanguage.isKindOf(Scala3Language.INSTANCE)) Scala3Language.INSTANCE
      else ScalaLanguage.INSTANCE
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language)

    val seq = convertLineSeparators(text).trim
    val delegate = PsiBuilderFactory.getInstance.createBuilder(
      project,
      chameleon,
      parserDefinition.createLexer(project),
      language,
      seq
    )

    val isScala3 = context.isInScala3File
    val psiBuilder = new ScalaPsiBuilderImpl(delegate, isScala3)
    if (text.indexOf('\n') >= 0 && !ScalaPsiUtil.newLinesEnabled(context)) {
      psiBuilder.disableNewlines()
    }

    val marker = psiBuilder.mark()
    parse(psiBuilder)
    advanceLexer(psiBuilder)(marker, parserDefinition.getFileNodeType)

    val first = psiBuilder.getTreeBuilt
      .getFirstChildNode
      .asInstanceOf[TreeElement]
    chameleon.rawAddChildren(first)

    val result = first.getPsi
    if (checkLength && result.getTextLength != seq.length) {
      throw new IncorrectOperationException(s"Text length differs; actual: ${result.getText}, expected: $seq")
    }
    result
  }

  @tailrec
  private[this] def advanceLexer(psiBuilder: PsiBuilder)
                                (marker: PsiBuilder.Marker,
                                 fileNodeType: IFileElementType): Unit =
    if (psiBuilder.eof()) {
      marker.done(fileNodeType)
    } else {
      psiBuilder.advanceLexer()
      advanceLexer(psiBuilder)(marker, fileNodeType)
    }

  def createImportFromTextWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScImportStmt =
    createElementWithContext[ScImportStmt](text, context, child)(Import()(_))

  def createTypeElementFromText(@NonNls text: String)
                               (implicit ctx: ProjectContext): ScTypeElement =
    createScalaFileFromText(s"var f: $text")
      .getLastChild
      .getLastChild match {
      case typeElement: ScTypeElement => typeElement
      case _ => throw elementCreationException("type element", text)
    }

  def createParameterTypeFromText(@NonNls text: String)(implicit ctx: ProjectContext): ScParameterType =
    createScalaFileFromText(s"(_: $text) => ())")
      .getFirstChild.asInstanceOf[ScFunctionExpr].parameters.head.paramType.get

  def createColon(implicit ctx: ProjectContext): PsiElement =
    createScalaElementFromText[ScalaPsiElement]("var f: Int").findChildrenByType(tCOLON).head

  def createComma(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(",").findChildrenByType(tCOMMA).head

  def createAssign(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("val x = 0").findChildrenByType(tASSIGN).head

  def createWhitespace(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText("1 + 1").findElementAt(1)

  def createWhitespace(@NonNls whitespace: String)(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText(s"1$whitespace+ 1").findElementAt(1)

  def createTypeElementFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTypeElement =
    createElementWithContext[ScTypeElement](text, context, child)(types.ParamType.parseInner)

  def createTypeParameterClauseFromTextWithContext(@NonNls text: String, context: PsiElement,
                                                   child: PsiElement): ScTypeParamClause =
    createElementWithContext[ScTypeParamClause](text, context, child)(params.TypeParamClause.parse(_))

  def createWildcardPattern(implicit ctx: ProjectContext): ScWildcardPattern = {
    val element = createElementFromText("val _ = x")
    element.getChildren.apply(2).getFirstChild.asInstanceOf[ScWildcardPattern]
  }

  def createTemplateDefinitionFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTemplateDefinition =
    createElementWithContext[ScTemplateDefinition](text, context, child)(TmplDef()(_))

  def createDeclarationFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScDeclaration =
    createElementWithContext[ScDeclaration](text, context, child)(parsingStat.Dcl()(_))

  def createTypeAliasDefinitionFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTypeAliasDefinition =
    createElementWithContext[ScTypeAliasDefinition](text, context, child)(parsingStat.Def.parse(_))

  def createDocCommentFromText(@NonNls text: String)
                              (implicit ctx: ProjectContext): ScDocComment =
    createDocComment(s"/**\n$text\n*/")

  def createMonospaceSyntaxFromText(@NonNls text: String)
                                   (implicit ctx: ProjectContext): ScDocSyntaxElement = {
    val comment = createDocCommentFromText(s"`$text`")
    val paragraph = PsiTreeUtil.findChildOfType(comment, classOf[ScDocParagraph])
    val result = PsiTreeUtil.findChildOfType(paragraph, classOf[ScDocSyntaxElement])
    result
  }

  def createDocHeaderElement(length: Int)
                            (implicit ctx: ProjectContext): PsiElement = {
    val text = s"/**=header${StringUtils.repeat("=", length)}*/"
    val comment = createDocComment(text)
    val paragraph = PsiTreeUtil.findChildOfType(comment, classOf[ScDocParagraph])
    val headerElement = paragraph.getFirstChild
    val result = headerElement.getLastChild
    result
  }

  def createDocWhiteSpaceWithNewLine(implicit ctx: ProjectContext): PsiElement = {
    val node = createDocComment(s"/**\n *\n*/").getNode
    node.getChildren(null)(1).getPsi
  }

  def createLeadingAsterisk(implicit ctx: ProjectContext): PsiElement =
    createDocCommentFromText(" *").getNode.getChildren(null)(2).getPsi

  def createDocSimpleData(@NonNls text: String)
                         (implicit ctx: ProjectContext): PsiElement =
    createDocComment(s"/**$text*/").getNode.getChildren(null)(1).getPsi

  def createDocTagValue(@NonNls text: String)
                       (implicit ctx: ProjectContext): PsiElement =
    createClassWithBody(
      s"""/**@param $text
         |*/""".stripMargin).docComment.orNull
      .getNode.getChildren(null)(1).getChildren(null)(2).getPsi

  def createDocTagName(@NonNls name: String)
                      (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("/**@" + name + " qwerty */")
      .typeDefinitions.head.docComment.get.getNode.getChildren(null)(1).getChildren(null)(0).getPsi

  def createDocLinkValue(@NonNls text: String)
                        (implicit ctx: ProjectContext): ScDocResolvableCodeReference =
    createDocComment(s"/**[[$text]]*/")
      .getNode.getChildren(null)(1).getChildren(null)(1).getPsi.asInstanceOf[ScDocResolvableCodeReference]

  def createXmlEndTag(@NonNls tagName: String)
                     (implicit ctx: ProjectContext): ScXmlEndTag =
    createScalaFileFromText(s"val a = <$tagName></$tagName>")
      .getFirstChild.getLastChild.getFirstChild.getLastChild.asInstanceOf[ScXmlEndTag]

  def createXmlStartTag(@NonNls tagName: String, @NonNls attributes: String = "")
                       (implicit ctx: ProjectContext): ScXmlStartTag =
    createScalaFileFromText(s"val a = <$tagName$attributes></$tagName>")
      .getFirstChild.getLastChild.getFirstChild.getFirstChild.asInstanceOf[ScXmlStartTag]

  def createInterpolatedStringPrefix(@NonNls prefix: String)
                                    (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(prefix + "\"blah\"").getFirstChild.getFirstChild

  def createEquivMethodCall(infix: ScInfixExpr): ScMethodCall = {
    val ScInfixExpr.withAssoc(base, ElementText(operationText), argument) = infix

    val clauseText = argument match {
      case _: ScTuple | _: ScParenthesisedExpr | _: ScUnitExpr => argument.getText
      case ElementText(text)                                   => text.parenthesize()
    }

    val typeArgText = infix.typeArgs.map(_.getText).getOrElse("")
    val exprText = s"(${base.getText}).$operationText$typeArgText$clauseText"

    val exprA = createExpressionWithContextFromText(base.getText, infix, base)

    val methodCall = createExpressionWithContextFromText(exprText, infix.getContext, infix)
    val referenceExpression = methodCall match {
      case ScMethodCall(reference: ScReferenceExpression, _) => reference
      case ScMethodCall(ScGenericCall(reference, _), _)      => reference
    }

    referenceExpression.qualifier.foreach {
      _.replaceExpression(exprA, removeParenthesis = true)
    }
    methodCall.asInstanceOf[ScMethodCall]
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

  private[this] def createClassWithBody(@NonNls body: String)
                                       (implicit context: ProjectContext): ScTypeDefinition = {
    // ATTENTION!  Do not use `stripMargin` here!
    // If the injected `body` contains multiline string with margins '|' they will be whipped out (see SCL-14585)
    val fileText = s"class a {\n  $body\n}"
    createScalaFileFromText(fileText).typeDefinitions.head
  }

  private[this] def createMemberFromText(@NonNls text: String)
                                        (implicit context: ProjectContext): ScMember =
    createClassWithBody(text).members.head

  def createDocComment(@NonNls prefix: String)
                      (implicit context: ProjectContext): ScDocComment =
    createScalaFileFromText(s"$prefix class a").typeDefinitions.head
      .docComment.orNull

  private[this] def elementCreationException(@NonNls kind: String, @NonNls text: String,
                                             context: PsiElement = null,
                                             cause: Throwable = null) =
    ScalaPsiElementCreationException(kind, text, context, cause)

  final class ScalaPsiElementCreationException(message: String, cause: Throwable)
    extends IncorrectOperationException(message, cause)

  private object ScalaPsiElementCreationException {

    def apply(@NonNls kind: String, @NonNls text: String, context: PsiElement = null, cause: Throwable = null): ScalaPsiElementCreationException = {
      val contextSuffix = context match {
        case null => ""
        case _ => "; with context: " + context.getText
      }
      new ScalaPsiElementCreationException(s"Cannot create $kind from text: $text$contextSuffix", cause)
    }
  }

}
