package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.lang.{ASTNode, LanguageParserDefinitions, PsiBuilder, PsiBuilderFactory}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.{IElementType, IFileElementType}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.IncorrectOperationException
import org.apache.commons.lang3.{ObjectUtils, StringUtils}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaKeywordTokenType, ScalaModifier, ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
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
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, _}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeParamsRenderer
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocParagraph, ScDocResolvableCodeReference, ScDocSyntaxElement}
import org.jetbrains.plugins.scala.project.ProjectContext.toManager
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt, ScalaFeatures}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scalaDirective.psi.api.ScDirective

import java.{util => ju}
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
        |}""".stripMargin, ScalaFeatures.default)(project)

  override def createConstructor(name: String): PsiMethod = createConstructor()

  override def createClassInitializer(): PsiClassInitializer = throw new IncorrectOperationException

  override def createParameter(name: String, `type`: PsiType): PsiParameter = {
    implicit val context: ProjectContext = project
    val typeText = `type`.toScType().canonicalText
    ScalaPsiElementFactory.createParameterFromText(s"$name: $typeText", ScalaFeatures.default)
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
    ScalaPsiElementFactory.createExpressionWithContextFromText(text, context)
}

object ScalaPsiElementFactory {

  sealed abstract class TemplateDefKind(val keyword: ScalaKeywordTokenType)
  object TemplateDefKind {
    // Add more if needed
    case object Class extends TemplateDefKind(ScalaTokenType.ClassKeyword)
    case object Trait extends TemplateDefKind(ScalaTokenType.TraitKeyword)
    case object Object extends TemplateDefKind(ScalaTokenType.ObjectKeyword)
    case object Given extends TemplateDefKind(ScalaTokenType.GivenKeyword)
  }

  final class TemplateDefinitionBuilder private (
    kind: TemplateDefKind,
    @Nullable context:   PsiElement,
    @Nullable child:     PsiElement,
    name:                String,
    body:                String,
    needsBlock:          Boolean,
    forceBraces:         Boolean,
    scalaFeatures:       Option[ScalaFeatures] = None,
    projectContext:      Option[ProjectContext] = None,
  ) {
    private def copy(
      kind:                TemplateDefKind        = this.kind,
      @Nullable context:   PsiElement             = this.context,
      @Nullable child:     PsiElement             = this.child,
      name:                String                 = this.name,
      body:                String                 = this.body,
      needsBlock:          Boolean                = this.needsBlock,
      forceBraces:         Boolean                = this.forceBraces,
      scalaFeatures:       Option[ScalaFeatures]  = this.scalaFeatures,
      projectContext:      Option[ProjectContext] = this.projectContext,
    ): TemplateDefinitionBuilder =
      new TemplateDefinitionBuilder(
        kind,
        context,
        child,
        name,
        body,
        needsBlock,
        forceBraces,
        scalaFeatures,
        projectContext,
      )

    def withScalaFeatures(features: ScalaFeatures): TemplateDefinitionBuilder =
      copy(scalaFeatures = Some(features))

    def withProjectContext(projectContext: ProjectContext): TemplateDefinitionBuilder =
      copy(projectContext = Some(projectContext))

    def createTemplateDefinition(): ScTemplateDefinition = {
      val textBuilder = new StringBuilder()
        .append(kind.keyword)
        .append(" ")
        .append(name)

      val firstNonNullOfContextAndChild = ObjectUtils.firstNonNull(context, child)
      implicit val ctx: ProjectContext = projectContext.getOrElse(firstNonNullOfContextAndChild)
      val features: ScalaFeatures = scalaFeatures.getOrElse(firstNonNullOfContextAndChild)

      if (needsBlock || body.nonEmpty) {
        val braceless = !forceBraces && ctx.project.indentationBasedSyntaxEnabled(features)
        if (kind == TemplateDefKind.Given) {
          textBuilder.append(" with")
          if (!braceless)
            textBuilder.append(" {")
        } else {
          textBuilder.append(if (braceless) ":" else " {")
        }

        textBuilder.append(body)

        if (!braceless)
          textBuilder.append("}")
        else {
          if (!body.endsWith('\n'))
            textBuilder.append("\n")
          if (needsBlock) {
            textBuilder
              .append("end ")
              .append(if (kind == TemplateDefKind.Given) kind.keyword else name)
          }
        }
      }

      createTemplateDefinitionFromText(textBuilder.result(), context, child, features)
    }
  }

  object TemplateDefinitionBuilder {
    def apply(
      kind: TemplateDefKind,
      @Nullable context: PsiElement = null,
      @Nullable child: PsiElement = null,
      name: String = "td",
      body: String = "",
      needsBlock: Boolean = false,
      forceBraces: Boolean = false,
    ): TemplateDefinitionBuilder =
      new TemplateDefinitionBuilder(
        kind,
        context,
        child,
        name,
        body,
        needsBlock,
        forceBraces,
      )
  }

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
  import org.jetbrains.plugins.scala.lang.parser.parsing.{base => parsingBase, statements => parsingStat, _}
  import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
  import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._

  def safe[T](createBody: ScalaPsiElementFactory.type => T): Option[T] =
    try Some(createBody(ScalaPsiElementFactory)) catch {
      case _: ScalaPsiElementCreationException => None
    }

  def createExpressionWithContextFromText(@NonNls text: String, context: PsiElement): ScExpression = {
    try {
      createExpressionWithContextFromText(text, context, context)
    } catch {
      case c: ControlFlowException => throw c
      case throwable: Throwable => throw ScalaPsiElementCreationException("expression", text, context, throwable)
    }
  }

  def createScalaElementFromTextWithContext[E <: ScalaPsiElement : ClassTag](
    text: String,
    @Nullable contextElement: PsiElement
  )(implicit ctx: ProjectContext): Option[E] = {
    createElementFromText[E](text, contextElement).toOption.map { element =>
      element.context = contextElement
      element
    }
  }

  def createPsiElementFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): PsiElement =
    createElementFromText[PsiElement](text, features)

  def createElementFromText[E <: PsiElement](
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): E =
    createScalaFileFromText(text, features).getFirstChild.asInstanceOf[E]

  def createDirectiveValueFromText(text: String, features: ScalaFeatures = ScalaFeatures.default)(implicit ctx: ProjectContext): PsiElement = {
    val directiveText = s"//> using key $text"
    createScalaFileFromText(directiveText, features).getFirstChild
      .asInstanceOf[ScDirective]
      .value
      .getOrElse(throw ScalaPsiElementCreationException("directive value", text))
  }

  def createWildcardNode(features: ScalaFeatures)(implicit ctx: ProjectContext): ASTNode = {
    val wildcard = if (features.isScala3) "*" else "_"
    createScalaFileFromText(s"import a.$wildcard", features).getLastChild.getLastChild.getLastChild.getNode
  }

  def createClauseFromText(
    @NonNls clauseText: String = "()",
    features:           ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScParameterClause = {
    val function = createMethodFromText(s"def foo$clauseText = null", features)
    function.paramClauses.clauses.head
  }

  def createClauseForFunctionExprFromText(
    @NonNls clauseText: String,
    features:      ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScParameterClause = {
    val functionExpression = createElementFromText[ScFunctionExpr](s"$clauseText => null", features)
    functionExpression.params.clauses.head
  }

  def createParameterFromText(
    @NonNls paramText: String,
    features:          ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScParameter = {
    val function = createMethodFromText(s"def foo($paramText) = null", features)
    function.parameters.head
  }

  def createClassParameterFromText(@NonNls paramText: String, features: ScalaFeatures)
                                  (implicit ctx: ProjectContext): ScClassParameter =
    createScalaFileFromText(s"class a($paramText)", features)
      .typeDefinitions.head.asInstanceOf[ScClass]
      .constructor.get
      .parameters.head

  // Supports "_" parameter name
  def createFunctionParameterFromText(@NonNls paramText: String)
                                     (implicit ctx: ProjectContext): ScParameter = {
    val function = createElementFromText[ScFunctionExpr](s"($paramText) =>", ScalaFeatures.default)
    function.parameters.head
  }

  def createPatternFromText(
    @NonNls patternText: String,
    features:            ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScPattern = {
    val matchStatement = createElementFromText[ScMatch](s"x match { case $patternText => }", features)
    matchStatement.clauses.head.pattern.get
  }

  def createTypeParameterFromText(
    @NonNls name: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScTypeParam = {
    val function = createMethodFromText(s"def foo[$name]() = {}", features)
    function.typeParameters.head
  }

  def createMatch(
    @NonNls element: String,
    caseClauses:     Seq[String],
    features:        ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScMatch = {
    val clausesText = caseClauses.mkString("{ ", "\n", " }")
    createElementFromText[ScMatch](s"$element match $clausesText", features)
  }

  def createMethodFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScFunction =
    createElementFromText[ScFunction](text, features)

  def createStringLiteralFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    context: ProjectContext
  ): ScStringLiteral =
    createExpressionFromText(text, features)(context).asInstanceOf[ScStringLiteral]

  def createExpressionFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    context: ProjectContext
  ): ScExpression = {
    /**
     * Use trimmed text in case it was called with extra new line(s): {{{
     *   createExpressionFromText(
     *     """if a then b
     *       |else c
     *       |""".stripMargin, features)
     * }}} instead of {{{
     *   createExpressionFromText(
     *     """if a then b
     *       |else c""".stripMargin, features)
     * }}}
     */
    val TrimmedText = text.trim
    getExprFromFirstDef(s"val b = ($text)", features) match {
      case ScParenthesisedExpr(e@ElementText(TrimmedText)) => e
      case _ =>
        // Fallback for better Scala 3 indentation-based syntax support. See ScalaPsiElementFactoryTest, SCL-21596
        getExprFromFirstDef(s"val b = {\n$text\n}", features) match {
          case ScBlockExpr.Expressions(e@ElementText(TrimmedText)) => e
          case _ =>
            // Throw exception early if can't create an expression
            throw ScalaPsiElementCreationException("expression", text)
        }
    }
  }

  def createReferenceExpressionFromText(
    @NonNls text: String
  )(implicit
    ctx: ProjectContext
  ): ScReferenceExpression =
    createElementFromText[ScReferenceExpression](text, ScalaFeatures.default)

  def createImplicitClauseFromTextWithContext(clauses: Iterable[String],
                                              context: PsiElement,
                                              isClassParameter: Boolean): ScParameterClause =
    if (clauses.isEmpty)
      throw new IncorrectOperationException("At least one clause required.")
    else if (context.isInScala3File)
      createElementWithContext[ScParameterClause](s"(using ${clauses.commaSeparated()})", context, null) {
        case builder if isClassParameter => top.params.ClassParamClause.parse(builder)
        case builder => params.ParamClause.parse(builder)
      }
    else
      createElementWithContext[ScParameterClause](s"(implicit ${clauses.commaSeparated()})", context, null) {
        case builder if isClassParameter => top.params.ImplicitClassParamClause.parse(builder)
        case builder => params.ImplicitParamClause.parse(builder)
      }


  def createEmptyClassParamClauseWithContext(context: PsiElement): ScParameterClause =
    createElementWithContext[ScParameterClause]("()", context, contextLastChild(context))(top.params.ClassParamClause.parse(_))

  def createClassParamClausesWithContext(@NonNls text: String, context: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, contextLastChild(context))(ClassParamClauses.parse(_))

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

  def createSelfInvocationFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScSelfInvocation =
    createElementWithContext[ScSelfInvocation](text, context, child) { implicit builder =>
      builder.withDisabledNewlines {
        expressions.SelfInvocation()
      }
    }

  def createParamClausesWithContext(@NonNls text: String, context: PsiElement, child: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, child)(params.ParamClauses()(_))

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
        throw ScalaPsiElementCreationException("pattern", patternText, context)
      }

  def createAnAnnotation(@NonNls name: String, features: ScalaFeatures)(implicit ctx: ProjectContext): ScAnnotation = {
    val text =
      s"""@$name
         |def foo""".stripMargin

    createElementFromText[PsiElement](text, features).getFirstChild.getFirstChild.asInstanceOf[ScAnnotation]
  }

  def createBlockWithGivenExpression(
    expression: PsiElement,
    features: ScalaFeatures
  )(implicit ctx: ProjectContext): ScBlockExpr =
    createBlockWithGivenExpressions(Seq(expression), features)

  def createBlockWithGivenExpressions[CC[+X] <: collection.Seq[X]](
    expressions: CC[PsiElement],
    features: ScalaFeatures
  )(implicit ctx: ProjectContext): ScBlockExpr = {
    val block = createElementFromText[ScBlockExpr](expressions.mkString("{\n", "\n", "\n}"), features)
    block.exprs.zip(expressions).foreach { case (placeholder, expr) =>
      placeholder.replace(expr)
    }
    block
  }

  def createBlockExpressionWithoutBracesFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScBlockImpl =
    createFromTextImpl(text, features)(expressions.Block.Braceless(stopOnOutdent = false, needNode = true)(_))(
      _.getFirstChild match {
        case b: ScBlockImpl => b
        case _              => null
      }
    )

  def createOptionExpressionFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): Option[ScExpression] = {
    val file = createScalaFileFromText(text, features)

    Option(file.getFirstChild).collect {
      case expression: ScExpression if expression.getNextSibling == null && !PsiTreeUtil.hasErrorElements(file) =>
        expression
    }
  }

  def createIdentifier(@NonNls name: String)(implicit ctx: ProjectContext): ASTNode = {
    try {
      createScalaFileFromText(s"package ${escapeKeyword(name)}", ScalaFeatures.default).getNode
        .getLastChildNode.getLastChildNode.getLastChildNode
    }
    catch {
      case c: ControlFlowException => throw c
      case throwable: Throwable => throw ScalaPsiElementCreationException("identifier", name, cause = throwable)
    }
  }

  def createModifierFromText(@NonNls modifier: String)(implicit ctx: ProjectContext): PsiElement =
    createElementFromText[ScClass](s"$modifier class a", ScalaFeatures.default)
      .getModifierList
      .getFirstChild

  def createImportExprFromText(@NonNls name: String,
                               context: PsiElement,
                               @Nullable child: PsiElement = null,
                               escapeKeywords: Boolean = true): ScImportExpr = {
    val importStmt = createImportFromText(s"import ${if (escapeKeywords) escapeKeywordsFqn(name) else name}", context, child)
    importStmt.getLastChild.asInstanceOf[ScImportExpr]
  }

  //TODO: add docs, what is `child` ???
  def createImportFromText(@NonNls text: String, context: PsiElement, @Nullable child: PsiElement = null): ScImportStmt =
    createElementWithContext[ScImportStmt](text, context, child)(Import.parse(_))

  def createReferenceFromText(
    @NonNls name: String,
  )(implicit
    ctx: ProjectContext
  ): ScStableCodeReference =
    try {
      val importStatement = createElementFromText[ScImportStmt](s"import ${escapeKeywordsFqn(name)}", ScalaFeatures.default)
      importStatement.importExprs.head.reference.orNull
    } catch {
      case c: ControlFlowException => throw c
      case throwable: Throwable    => throw ScalaPsiElementCreationException("reference", name, cause = throwable)
    }

  def createDeclaration(
    `type`:            ScType,
    @NonNls name:      String,
    isVariable:        Boolean,
    @Nullable @NonNls
    exprText:          String,
    features:          ScalaFeatures,
    isPresentableText: Boolean = false
  )(implicit
    tpc:     TypePresentationContext,
    context: ProjectContext
  ): ScValueOrVariable = {
    val typeText = `type` match {
      case null                    => ""
      case tp if isPresentableText => tp.presentableText
      case tp                      => tp.canonicalText(tpc)
    }

    val expr = NullSafe(exprText).map(createExpressionFromText(_, features))
    createDeclaration(name, typeText, isVariable, expr.orNull, features)
  }

  def createDeclaration(
    @NonNls name:     String,
    @NonNls typeName: String,
    isVariable:       Boolean,
    @Nullable body:   ScExpression,
    features:         ScalaFeatures
  )(implicit
    context: ProjectContext
  ): ScValueOrVariable =
    createMember(name, typeName, body, features, isVariable = isVariable).asInstanceOf[ScValueOrVariable]

  private[this] def createMember(
    @NonNls name:     String,
    @NonNls typeName: String,
    @Nullable body:   ScExpression,
    features:         ScalaFeatures,
    modifiers:        String  = "",
    isVariable:       Boolean = false
  )(implicit
    context: ProjectContext
  ): ScMember = {
    def stmtText(@Nullable expr: ScBlockStatement): String = expr match {
      case block @ ScBlock(st) if !block.hasRBrace =>
        stmtText(st)
      case WithParenthesesStripped(fun @ ScFunctionExpr(parSeq, Some(result))) =>
        val paramText = parSeq match {
          case Seq(parameter) if parameter.typeElement.isDefined && parameter.getPrevSiblingNotWhitespace == null =>
            parameter.getText.parenthesize()
          case _ => fun.params.getText
        }

        val resultText = result match {
          case block: ScBlock if !block.hasRBrace && block.statements.size != 1 =>
            // see ScalaPsiElementFactory.createClassWithBody comment
            s"{\n${block.getText}\n}"
          case block @ ScBlock(st) if !block.hasRBrace => stmtText(st)
          case _                                       => result.getText
        }
        s"$paramText $functionArrow $resultText"
      case null      => ""
      case statement => statement.getText
    }

    val typedName = typeName match {
      case null | "" => name
      case _         =>
        // throws an exception if type name is incorrect
        createTypeElementFromText(typeName, features)

        val space = if (isOpCharacter(name.last)) " " else ""
        s"$name$space: $typeName"
    }

    val text =
      s"$modifiers${if (modifiers.isEmpty) "" else " "}${if (isVariable) kVAR else kVAL} $typedName = ${stmtText(body)}"

    createMemberFromText(text, features)
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

  private[this] def createValueOrVariable(
    valOrVar:  ScValueOrVariable,
    fromToken: IElementType,
    toToken:   IElementType
  )(implicit
    context: ProjectContext = valOrVar.projectContext
  ): ScMember =
    createMemberFromText(
      replaceKeywordTokenIn(valOrVar, fromToken, toToken),
      ScalaFeatures.forPsiOrDefault(valOrVar)
    )

  private[this] def replaceKeywordTokenIn(member: ScMember,
                                          fromToken: IElementType = kVAR,
                                          toToken: IElementType = kVAL) = {
    val offset = member.findFirstChildByType(fromToken).get.getStartOffsetInParent
    val memberText = member.getText

    memberText.substring(0, offset) +
      toToken +
      memberText.substring(offset + fromToken.toString.length)
  }

  def createForBinding(
    @NonNls name:     String,
    expr:             ScExpression,
    @NonNls typeName: String
  )(implicit
    ctx: ProjectContext
  ): ScForBinding = {
    val typeText = Option(typeName).collect { case name if name.nonEmpty => s": $name" }.getOrElse("")

    val enumText = s"$name$typeText = ${expr.getText}"
    // see ScalaPsiElementFactory.createClassWithBody comment
    val text    = s"for {\n  i <- 1 to 239\n  $enumText\n}"
    val forStmt = createElementFromText[ScFor](text, ScalaFeatures.forPsiOrDefault(expr))

    forStmt.enumerators.flatMap {
      _.forBindings.headOption
    }.getOrElse {
      throw ScalaPsiElementCreationException("enumerator", enumText)
    }
  }

  def createNewLine(@NonNls text: String = "\n")(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(text, ScalaFeatures.default, shouldTrimText = false).getFirstChild

  def createNewLineNode(@NonNls text: String = "\n")(implicit ctx: ProjectContext): ASTNode =
    createNewLine(text).getNode

  def createBlockFromExpr(expression: ScExpression, features: ScalaFeatures)
                         (implicit context: ProjectContext): ScExpression = {
    // see ScalaPsiElementFactory.createClassWithBody comment
    val definition = s"val b = {\n${expression.getText}\n}"
    getExprFromFirstDef(definition, features)
  }

  def createAnonFunBlockFromFunExpr(
    expression: ScFunctionExpr,
    features:   ScalaFeatures
  )(implicit
    context: ProjectContext
  ): ScExpression = {
    val params = expression.params.getText
    val body = expression.result.map(_.getText).getOrElse("")
    // see ScalaPsiElementFactory.createClassWithBody comment
    val definition = s"val b = {$params=>\n$body\n}"
    getExprFromFirstDef(definition, features)
  }

  def createPatternDefinition(
    @NonNls name:      String,
    @NonNls typeName:  String,
    body:              ScExpression,
    features:          ScalaFeatures,
    @NonNls modifiers: String  = "",
    isVariable:        Boolean = false
  )(implicit
    context: ProjectContext
  ): ScPatternDefinition =
    createMember(name, typeName, body, features, modifiers, isVariable).asInstanceOf[ScPatternDefinition]

  private[this] def getExprFromFirstDef(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    context: ProjectContext
  ): ScExpression =
    createMemberFromText(text, features) match {
      case ScPatternDefinition.expr(body) => body
      case _                              => throw new IncorrectOperationException("Expression not found")
    }

  def createBodyFromMember(
    @NonNls memberText: String,
    isGiven:            Boolean,
    scalaFeatures:      ScalaFeatures,
  )(implicit
    ctx: ProjectContext
  ): ScTemplateBody = {
    val definition =
      if (isGiven) createGivenDefWithBody(memberText, scalaFeatures)
      else createClassWithBody(memberText, scalaFeatures)

    definition
      .extendsBlock
      .templateBody
      .orNull
  }

  def createTemplateBody(isGiven: Boolean, features: ScalaFeatures)(implicit ctx: ProjectContext): ScTemplateBody =
    createBodyFromMember("", isGiven, features)

  def createClassTemplateParents(
    @NonNls superName: String,
    scalaFeatures:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): (PsiElement, ScTemplateParents) = {
    val text =
      s"""class a extends $superName {
         |}""".stripMargin

    val extendsBlock = createElementFromText[ScClass](text, scalaFeatures).extendsBlock
    (extendsBlock.findFirstChildByType(kEXTENDS).get, extendsBlock.templateParents.get)
  }

  def createMethodFromSignature(
    signature:      PhysicalMethodSignature,
    @NonNls body:   String,
    scalaFeatures:  ScalaFeatures,
    withComment:    Boolean = true,
    withAnnotation: Boolean = true
  )(implicit
    ctx: ProjectContext
  ): ScFunction = {
    val builder = new StringBuilder()

    val PhysicalMethodSignature(method, substitutor) = signature

    if (withComment) {
      appendCommentText(builder, method)
    }

    if (withAnnotation) {
      val annotations = method match {
        case function: ScFunction => function.annotations.map(_.getText)
        case _                    => Seq.empty
      }

      annotations.foreach(builder.append)
      if (annotations.nonEmpty) builder.append("\n")
    }

    signatureText(method, substitutor)(builder)

    builder
      .append(" ")
      .append(tASSIGN)
      .append(" ")
      .append(body)

    createClassWithBody(builder.toString, scalaFeatures).functions.head
  }

  private def appendCommentText(builder: StringBuilder, method: PsiMethod): Boolean = {
    val maybeCommentText = method.firstChild.collect { case comment: PsiDocComment =>
      comment.getText
    }

    maybeCommentText.foreach(builder.append)
    val commentAdded = maybeCommentText.isDefined
    if (commentAdded) {
      builder.append("\n")
    }
    commentAdded
  }

  def createOverrideImplementMethod(
    signature:             PhysicalMethodSignature,
    needsOverrideModifier: Boolean,
    @NonNls body:          String,
    features:              ScalaFeatures,
    withComment:           Boolean = true,
    withAnnotation:        Boolean = true
  )(implicit
    ctx: ProjectContext
  ): ScFunction = {
    val function = createMethodFromSignature(signature, body, features, withComment, withAnnotation)
    addModifiersFromSignature(function, signature, needsOverrideModifier)
    function
  }

  case class ExtensionMethodConstructionInfo(
    signature: PhysicalMethodSignature,
    needsOverrideModifier: Boolean,
    @NonNls body: String,
  )

  /**
   * @param extensionMethodsInfos represents extension methods belonging to the same extension (extension with same signature)
   */
  def createOverrideImplementExtensionMethods(
    extensionMethodsInfos: Seq[ExtensionMethodConstructionInfo],
    features: ScalaFeatures,
    wrapMultipleExtensionsWithBraces: Boolean,
    withComment: Boolean = true,
  )(implicit
    ctx: ProjectContext
  ): ScExtension =
    createOverrideImplementExtensionMethodsImpl(
      extensionMethodsInfos,
      features,
      wrapMultipleExtensionsWithBraces,
      withComment,
      addNewLineAfterExtensionSignature = true
    )

  def createOverrideImplementExtensionMethod(
    extensionMethodsInfo: ExtensionMethodConstructionInfo,
    features: ScalaFeatures,
    wrapMultipleExtensionsWithBraces: Boolean,
    withComment: Boolean = true,
  )(implicit
    ctx: ProjectContext
  ): ScExtension = createOverrideImplementExtensionMethodsImpl(
    Seq(extensionMethodsInfo),
    features,
    wrapMultipleExtensionsWithBraces,
    withComment,
    addNewLineAfterExtensionSignature = false
  )

  private def createOverrideImplementExtensionMethodsImpl(
    extensionMethodsInfos: Seq[ExtensionMethodConstructionInfo],
    features: ScalaFeatures,
    wrapMultipleExtensionsWithBraces: Boolean,
    withComment: Boolean,
    addNewLineAfterExtensionSignature: Boolean
  )(implicit
    ctx: ProjectContext
  ): ScExtension = {
    assert(extensionMethodsInfos.nonEmpty)

    val extension = createExtensionMethodFromSignature(
      extensionMethodsInfos,
      features,
      wrapMultipleExtensionsWithBraces,
      withComment,
      addNewLineAfterExtensionSignature
    )

    extension.extensionMethods.zip(extensionMethodsInfos).foreach { case (newMethod, methodConstructionInfo) =>
      addModifiersFromSignature(newMethod, methodConstructionInfo.signature, methodConstructionInfo.needsOverrideModifier)
    }

    extension
  }

  private def createExtensionMethodFromSignature(
    extensionMethodsInfos: Seq[ExtensionMethodConstructionInfo],
    scalaFeatures: ScalaFeatures,
    wrapMultipleExtensionsWithBraces: Boolean,
    withComment: Boolean,
    addNewLineAfterExtensionSignature: Boolean
  )(implicit
    ctx: ProjectContext
  ): ScExtension = {
    val builder = new StringBuilder()

    //Using any extension method to get signature of extension
    val representativeMethod = extensionMethodsInfos.head.signature
    assert(representativeMethod.isExtensionMethod)
    val extensionSignature = representativeMethod.extensionSignature.get
    appendExtensionSignatureText(builder, extensionSignature, representativeMethod.substitutor)

    val addBraces = wrapMultipleExtensionsWithBraces && extensionMethodsInfos.size > 1 ||
      !scalaFeatures.indentationBasedSyntaxEnabled
    if (addBraces)
      builder.append(" {")

    if (addNewLineAfterExtensionSignature)
      builder.append("\n")
    else
      builder.append(" ")

    extensionMethodsInfos.foreach { info =>
      assert(info.signature.isExtensionMethod)

      val PhysicalMethodSignature(method, substitutor) = info.signature

      // use an indent which is 2 times bigger, cause it will be extra-indented in class body, to get
      //class wrapper:
      //  extension (p: Any)
      //    |
      val ExtensionBodyIndent = "    "
      if (addNewLineAfterExtensionSignature)
        builder.append(ExtensionBodyIndent)

      if (withComment) {
        val commentAdded = appendCommentText(builder, method)
        if (commentAdded && addNewLineAfterExtensionSignature) {
          builder.append(ExtensionBodyIndent)
        }
      }

      signatureText(method, substitutor)(builder)

      builder
        .append(" = ")
        .append(info.body)
        .append("\n")
    }

    if (addBraces) {
      builder.append(" }")
    }

    val text = builder.toString.trimRight
    val classBody = createClassWithBody(text, scalaFeatures)
    classBody.extensions.head
  }

  private def appendExtensionSignatureText(
    builder: StringBuilder,
    extensionSignature: ExtensionSignatureInfo,
    substitutor: ScSubstitutor
  )(implicit projectContext: ProjectContext): Unit = {
    builder.append("extension ")

    val typeParams = extensionSignature.typeParams

    val typeParameters: Seq[String] = {
      val renderer = new TypeParamsRenderer(substitutor(_).canonicalText, stripContextTypeArgs = true)

      def buildText(typeParam: ScTypeParam): String =
        renderer.render(typeParam)

      typeParams.map(buildText)
    }

    if (typeParameters.nonEmpty) {
      val typeParametersText = typeParameters.mkString(tLSQBRACKET.toString, ", ", tRSQBRACKET.toString)
      builder.append(typeParametersText)
    }

    appendParameterClausesText(builder, extensionSignature.paramClauses, substitutor)
  }

  def createOverrideImplementType(
    alias:                 ScTypeAlias,
    substitutor:           ScSubstitutor,
    needsOverrideModifier: Boolean,
    features:              ScalaFeatures,
    @NonNls comment:       String = ""
  )(implicit
    ctx: ProjectContext
  ): ScTypeAlias = {
    val typeSign = getOverrideImplementTypeSign(alias, substitutor, needsOverrideModifier)
    createClassWithBody(s"$comment $typeSign", features).aliases.head
  }

  def createOverrideImplementVariable(
    variable:              ScTypedDefinition,
    substitutor:           ScSubstitutor,
    needsOverrideModifier: Boolean,
    isVal:                 Boolean,
    features:              ScalaFeatures,
    @NonNls comment:       String  = "",
    withBody:              Boolean = true
  )(implicit
    ctx: ProjectContext
  ): ScMember = {
    val body = if (withBody) Some("???") else None
    val variableSign = getOverrideImplementVariableSign(
      variable,
      substitutor,
      body,
      needsOverrideModifier,
      isVal,
      needsInferType = true
    )

    createMemberFromText(s"$comment $variableSign", features)
  }

  def createOverrideImplementVariableWithClass(
    variable:              ScTypedDefinition,
    substitutor:           ScSubstitutor,
    needsOverrideModifier: Boolean,
    isVal:                 Boolean,
    clazz:                 ScTemplateDefinition,
    features:              ScalaFeatures,
    @NonNls comment:       String  = "",
    withBody:              Boolean = true
  )(implicit
    ctx: ProjectContext
  ): ScMember = {
    val member = createOverrideImplementVariable(
      variable,
      substitutor,
      needsOverrideModifier,
      isVal,
      features,
      comment,
      withBody
    )

    clazz match {
      case td: ScTypeDefinition => member.syntheticContainingClass = td
      case _                    =>
    }

    member
  }

  def createSemicolon(implicit ctx: ProjectContext): PsiElement = createElementFromText(";", ScalaFeatures.default)

  private def addModifiersFromSignature(target: ScFunction, source: PhysicalMethodSignature, addOverride: Boolean): Unit = {
    source.method match {
      case fun: ScFunction =>
        val res = target.getModifierList.replace(fun.getModifierList)
        if (res.getText.nonEmpty) {
          res.getParent.addAfter(createWhitespace(fun.getManager), res)
        }
        target.setModifierProperty(ScalaModifier.ABSTRACT, value = false)
        if (!fun.hasModifierProperty("override") && addOverride) {
          target.setModifierProperty(ScalaModifier.OVERRIDE)
        }

      case m: PsiMethod =>
        var hasOverride = false
        if (m.getModifierList.getNode != null)
          for {modifier <- m.getModifierList.getNode.getChildren(null)} {
            val modText = modifier.getText
            modText match {
              case "override" =>
                hasOverride = true
                target.setModifierProperty("override")
              case "protected" =>
                target.setModifierProperty("protected")
              case "final" =>
                target.setModifierProperty("final")
              case _ =>
            }
          }
        if (addOverride && !hasOverride) {
          target.setModifierProperty("override")
        }
    }
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
        appendParameterClausesText(myBuilder, method.paramClauses.clauses, substitutor)
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

  private def appendParameterClausesText(
    builder: StringBuilder,
    paramClauses: Seq[ScParameterClause],
    substitutor: ScSubstitutor
  )(implicit project: ProjectContext): Unit = {
    for (paramClause <- paramClauses) {
      val parameters = paramClause.parameters.map { param =>
        val arrow = if (param.isCallByNameParameter) functionArrow else ""
        val asterisk = if (param.isRepeatedParameter) "*" else ""

        val name = param.name
        val tpe = param.`type`().map(substitutor).getOrAny

        if (param.isAnonimousContextParameter)
          s"$arrow${tpe.canonicalText}"
        else
          s"$name${colon(name)} $arrow${tpe.canonicalText}$asterisk"
      }

      builder.append("(")
      if (paramClause.isImplicit)
        builder.append("implicit ")
      else if (paramClause.isUsing)
        builder.append("using ")
      builder.append(parameters.mkString(", "))
      builder.append(")")
    }
  }

  def getOverrideImplementTypeSign(alias: ScTypeAlias, substitutor: ScSubstitutor, needsOverride: Boolean): String =
    try alias match {
      case alias: ScTypeAliasDefinition =>
        val overrideText = if (needsOverride && !alias.hasModifierProperty("override")) "override " else ""
        val modifiersText = alias.getModifierList.getText
        val typeText = substitutor(alias.aliasedType.getOrAny).canonicalText
        s"$overrideText$modifiersText type ${alias.name} = $typeText"
      case alias: ScTypeAliasDeclaration =>
        val overrideText = if (needsOverride) "override " else ""
        s"$overrideText${alias.getModifierList.getText} type ${alias.name} = this.type"
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Exception =>
        e.printStackTrace()
        ""
    }

  private def colon(name: String): String = (if (isIdentifier(name + tCOLON)) " " else "") + tCOLON + " "

  private def getOverrideImplementVariableSign(
    variable:       ScTypedDefinition,
    substitutor:    ScSubstitutor,
    body:           Option[String],
    needsOverride:  Boolean,
    isVal:          Boolean,
    needsInferType: Boolean
  ): String = {
    val modOwner: ScModifierListOwner = variable.nameContext match {
      case m: ScModifierListOwner => m
      case _                      => null
    }
    val overrideText =
      if (needsOverride && (modOwner == null || !modOwner.hasModifierProperty("override"))) "override " else ""
    val modifiersText = if (modOwner != null) modOwner.getModifierList.getText + " " else ""
    val keyword =       if (isVal) "val "                                            else "var "
    val name = variable.name
    val colon = this.colon(name)
    val typeText =
      if (needsInferType)
        substitutor(variable.`type`().getOrAny).canonicalText
      else ""
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

  def createTypeFromText(@NonNls text: String, @Nullable context: PsiElement, @Nullable child: PsiElement): Option[ScType] = {
    val typeElement = createTypeElementFromText(text, context, child)
    Option(typeElement).map {
      _.`type`().getOrAny // FIXME this should probably be a None instead of Some(Any)
    }
  }

  def createMethodWithContext(@NonNls text: String, @Nullable context: PsiElement, @Nullable child: PsiElement): ScFunction =
    createElementWithContext[ScFunction](text, context, child)(parsingStat.Def.parse(_))

  def createDefinitionWithContext(@NonNls text: String, @Nullable context: PsiElement, @Nullable child: PsiElement): ScMember =
    createElementWithContext[ScMember](text, context, child)(parsingStat.Def.parse(_))

  def createObjectWithContext(@NonNls text: String, @Nullable context: PsiElement, @Nullable child: PsiElement): ScObject =
    createElementWithContext[ScObject](text, context, child)(TmplDef.parse(_))

  def createTypeDefinitionWithContext(@NonNls text: String, @Nullable context: PsiElement, @Nullable child: PsiElement): ScTypeDefinition =
    createElementWithContext[ScTypeDefinition](text, context, child)(TmplDef.parse(_))

  def createReferenceFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScStableCodeReference =
    createElementWithContext[ScStableCodeReference](text, context, child) {
      types.StableId(ScalaElementType.REFERENCE)(_)
    }

  def createDocReferenceFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScStableCodeReference =
    createElementWithContext[ScDocResolvableCodeReference](text, context, child) {
      types.StableId(ScalaElementType.DOC_REFERENCE)(_)
    }

  // TODO method should be eliminated eventually
  def createExpressionWithContextFromText(@NonNls text: String,
                                          @Nullable context: PsiElement,
                                          @Nullable child: PsiElement): ScExpression = {
    val methodCall = createElementWithContext[ScMethodCall](s"foo($text)", context, child)(expressions.Expr.parse(_))

    val firstArgument = methodCall.argumentExpressions
      .headOption
      .getOrElse {
        throw ScalaPsiElementCreationException("expression", text, context)
      }

    firstArgument.context = context
    firstArgument.child = child
    firstArgument
  }

  def createElementWithContext[E <: ScalaPsiElement](
    @NonNls text:      String,
    @Nullable context: PsiElement,
    child:             PsiElement,
    features:          ScalaFeatures,
  )(parse:             ScalaPsiBuilder => AnyVal
  )(implicit
    tag: ClassTag[E],
    ctx: ProjectContext
  ): E = {
    val instance =
      createFromTextImpl[PsiElement](text, features, checkLength = true)(parse)(_.getFirstChild)

    instance match {
      case element: E =>
        element.context = context
        element.child = child
        element
      case element =>
        throw ScalaPsiElementCreationException(
          tag.runtimeClass.getSimpleName,
          text + "; actual: " + element.getText,
          context
        )
    }
  }

  def createElementWithContext[E <: ScalaPsiElement : ClassTag](
    @NonNls text:      String,
    @Nullable context: PsiElement,
    @Nullable child:   PsiElement
  )(parse:        ScalaPsiBuilder => AnyVal): E = {
    implicit val project: Project = (if (context == null) child else context).getProject
    createElementWithContext[E](text, context, child, ScalaFeatures.forPsiOrDefault(context))(parse)
  }

  def createEmptyModifierList(context: PsiElement): ScModifierList =
    createElementWithContext[ScModifierList]("", context, null) {
      _.mark().done(ScalaElementType.MODIFIERS)
    }

  def createScalaFileFromText(
    @NonNls text:   String,
    features:       ScalaFeatures,
    checkLength:    Boolean = false,
    shouldTrimText: Boolean = true,
    eventSystemEnabled: Boolean = false
  )(implicit
    ctx: ProjectContext
  ): ScalaFile =
    createFromTextImpl(
      text,
      features,
      checkLength,
      shouldTrimText
    )(CompilationUnit()(_))(identity)

  private def createFromTextImpl[R <: PsiElement](
    @NonNls text:       String,
    features:           ScalaFeatures,
    checkLength:        Boolean = false,
    shouldTrimText:     Boolean = true,
    eventSystemEnabled: Boolean = false
  )(parse:         ScalaPsiBuilder => Any
  )(getResult:     ScalaFile => R
  )(implicit
    ctx: ProjectContext
  ): R = {
    val text1 = convertLineSeparators(text)
    val text2 = if (shouldTrimText) text1.trim else text1
    val textFinal = text2

    val language = if (features.isScala3) Scala3Language.INSTANCE else ScalaLanguage.INSTANCE
    val lightVirtualFile = new LightVirtualFile("dummy.scala", "")
    val fileViewProviderFactory = LanguageFileViewProviders.INSTANCE.forLanguage(language)

    val viewProvider =
      fileViewProviderFactory.createFileViewProvider(lightVirtualFile, language, PsiManager.getInstance(ctx.getProject), eventSystemEnabled)

    val scalaFile = new ScalaFileImpl(viewProvider)
    scalaFile.putUserData(SyntheticFileKey, true)

    val project = ctx.getProject
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language)

    val chameleon = scalaFile.getTreeElement

    val delegate = PsiBuilderFactory.getInstance.createBuilder(
      project,
      chameleon,
      parserDefinition.createLexer(project),
      language,
      textFinal
    )

    val scalaPsiBuilder = new ScalaPsiBuilderImpl(delegate, features.isScala3, Some(features))
    val marker = scalaPsiBuilder.mark()
    parse(scalaPsiBuilder)
    advanceLexer(scalaPsiBuilder)(marker, parserDefinition.getFileNodeType)

    val first = scalaPsiBuilder.getTreeBuilt.getFirstChildNode.asInstanceOf[TreeElement]
    chameleon.getFirstChildNode

    if (first ne null) {
      val valueBefore = scalaFile.incrementModificationCounterOnSubtreeChange
      scalaFile.incrementModificationCounterOnSubtreeChange = false
      try chameleon.rawAddChildren(first) finally {
        scalaFile.incrementModificationCounterOnSubtreeChange = valueBefore
      }
    }

    val result = getResult(scalaFile)
    if (checkLength && chameleon.getTextLength != text2.length) {
      throw new ScalaPsiElementCreationException(
        s"Text length differs; actual: ${chameleon.getText}, expected: $text2",
        null
      )
    }

    CodeEditUtil.setNodeGeneratedRecursively(chameleon, true)
    ScalaFeatures.setAttachedScalaFeatures(scalaFile, features)
    features.psiContext.foreach(scalaFile.context = _)
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

  def createTypeElementFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScTypeElement =
    createScalaFileFromText(s"var f: $text", features).getLastChild.getLastChild match {
      case typeElement: ScTypeElement => typeElement
      case _                          => throw ScalaPsiElementCreationException("type element", text)
    }

  def createParameterTypeFromText(
    @NonNls text:  String,
    scalaFeatures: ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScParameterType =
    createElementFromText[ScFunctionExpr](s"(_: $text) => ())", scalaFeatures).parameters.head.paramType.get

  def createColon(implicit ctx: ProjectContext): PsiElement =
    createElementFromText[ScalaPsiElement]("var f: Int", ScalaFeatures.default).findChildrenByType(tCOLON).head

  def createComma(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(",", ScalaFeatures.default).findChildrenByType(tCOMMA).head

  def createLBrace(implicit ctx: ProjectContext): PsiElement =
    createElementFromText[ScBlockExpr]("{}", ScalaFeatures.default).getLBrace.get

  def createRBrace(implicit ctx: ProjectContext): PsiElement =
    createElementFromText[ScBlockExpr]("{}", ScalaFeatures.default).getRBrace.get

  def createAssign(implicit ctx: ProjectContext): PsiElement =
    createElementFromText("val x = 0", ScalaFeatures.default)

  def createWhitespace(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText("1 + 1", ScalaFeatures.default).findElementAt(1)

  def createWhitespace(@NonNls whitespace: String)(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText(s"1$whitespace+ 1", ScalaFeatures.default).findElementAt(1)

  def createWithKeyword(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("class A extends B with C", ScalaFeatures.default)
      .typeDefinitions
      .headOption
      .flatMap(_.extendsBlock.templateParents)
      .flatMap(_.findLastChildByTypeScala[PsiElement](ScalaTokenTypes.kWITH))
      .get

  def createTypeElementFromText(
    @NonNls text:      String,
    @Nullable context: PsiElement,
    @Nullable child:   PsiElement,
    isPattern:         Boolean = false,
    typeVariables:     Boolean = false
  ): ScTypeElement =
    createElementWithContext[ScTypeElement](text, context, child)(
      types.ParamType.parseWithoutScParamTypeCreation(
        isPattern = isPattern,
        typeVariables = typeVariables
      )(_)
    )

  def createContextBoundFromText(
    @NonNls text:      String,
    @Nullable context: PsiElement,
    @Nullable child:   PsiElement
  ): ScContextBound =
    createElementWithContext[ScContextBound](text, context, child)(types.ContextBound()(_))

  def createTypedPatternFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTypeElement =
    createElementWithContext[ScTypeElement](text, context, child)(types.Type(isPattern = true)(_))

  def createTypeParameterClauseFromTextWithContext(
    @NonNls text: String,
    context:      PsiElement,
    child:        PsiElement
  ): ScTypeParamClause =
    createElementWithContext[ScTypeParamClause](text, context, child)(params.TypeParamClause.parse(_))

  def createWildcardPattern(implicit ctx: ProjectContext): ScWildcardPattern = {
    val element = createElementFromText[PsiElement]("val _ = x", ScalaFeatures.default)
    element.getChildren.apply(2).getFirstChild.asInstanceOf[ScWildcardPattern]
  }

  def createTemplateDefinitionFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTemplateDefinition =
    createElementWithContext[ScTemplateDefinition](text, context, child)(TmplDef.parse(_))

  def createTemplateDefinitionFromText(@NonNls text: String, context: PsiElement, child: PsiElement, features: ScalaFeatures)
                                      (implicit ctx: ProjectContext): ScTemplateDefinition =
    createElementWithContext[ScTemplateDefinition](text, context, child, features)(TmplDef.parse(_))

  def createDeclarationFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScDeclaration =
    createElementWithContext[ScDeclaration](text, context, child)(parsingStat.Dcl.parse(_))

  def createTypeAliasDeclarationFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTypeAliasDeclaration =
    createElementWithContext[ScTypeAliasDeclaration](text, context, child)(parsingStat.Def.parse(_))

  def createTypeAliasDefinitionFromText(@NonNls text: String, context: PsiElement, child: PsiElement): ScTypeAliasDefinition =
    createElementWithContext[ScTypeAliasDefinition](text, context, child)(parsingStat.Def.parse(_))

  def createCommentFromText(@NonNls text: String)(implicit ctx: ProjectContext): PsiComment = {
    val definition = createScalaFileFromText(s"$text\nclass a", ScalaFeatures.default).getFirstChild.asInstanceOf[ScClass]
    definition.allComments.headOption.getOrElse {
      throw ScalaPsiElementCreationException("scala comment", text)
    }
  }

  //============================================================
  // ScalaDoc elements
  //============================================================

  def createScalaDocComment(@NonNls prefix: String)(implicit ctx: ProjectContext): ScDocComment = {
    val definition = createScalaFileFromText(s"$prefix class a", ScalaFeatures.default).getFirstChild.asInstanceOf[ScClass]
    definition.docComment.getOrElse {
      throw ScalaPsiElementCreationException("scaladoc comment", prefix)
    }
  }

  def createScalaDocMonospaceSyntaxFromText(@NonNls text: String)
                                           (implicit ctx: ProjectContext): ScDocSyntaxElement =
    createScalaDocSyntaxElementFromText("`", text, "`")

  /** @param linkContent examples:<br>
   *                     https://google.com<br>
   *                     org.example.MyClass
   *                     org.example.MyClass link description
   */
  def createScalaDocLinkSyntaxFromText(@NonNls linkContent: String)
                                      (implicit ctx: ProjectContext): ScDocSyntaxElement =
    createScalaDocSyntaxElementFromText("[[", linkContent, "]]")

  private def createScalaDocSyntaxElementFromText(@NonNls left: String, @NonNls text: String, @NonNls right: String)
                                                 (implicit ctx: ProjectContext): ScDocSyntaxElement = {
    val comment = createScalaDocCommentFromText(s"$left$text$right")
    val paragraph = PsiTreeUtil.findChildOfType(comment, classOf[ScDocParagraph])
    val result = PsiTreeUtil.findChildOfType(paragraph, classOf[ScDocSyntaxElement])
    result
  }

  def createScalaDocCommentFromText(@NonNls text: String)
                                   (implicit ctx: ProjectContext): ScDocComment =
    createScalaDocComment(s"/**\n$text\n*/")

  def createScalaDocHeaderElement(length: Int)
                                 (implicit ctx: ProjectContext): PsiElement = {
    val text = s"/**=header${StringUtils.repeat("=", length)}*/"
    val comment = createScalaDocComment(text)
    val paragraph = PsiTreeUtil.findChildOfType(comment, classOf[ScDocParagraph])
    val headerElement = paragraph.getFirstChild
    val result = headerElement.getLastChild
    result
  }

  def createScalaDocWhiteSpaceWithNewLine(implicit ctx: ProjectContext): PsiElement = {
    val node = createScalaDocComment(s"/**\n *\n*/").getNode
    node.getChildren(null)(1).getPsi
  }

  def createScalaDocLeadingAsterisk(implicit ctx: ProjectContext): PsiElement =
    createScalaDocCommentFromText(" *").getNode.getChildren(null)(2).getPsi

  def createScalaDocSimpleData(@NonNls text: String)
                              (implicit ctx: ProjectContext): PsiElement =
    createScalaDocComment(s"/**$text*/").getNode.getChildren(null)(1).getPsi

  def createScalaDocTagValue(@NonNls text: String)
                            (implicit ctx: ProjectContext): PsiElement = {
    val definition = createClassWithBody(
      s"""/**@param $text
         |*/""".stripMargin,
      ScalaFeatures.default
    )
    val docComment = definition.docComment.orNull
    assert(docComment != null, s"Can't find any scaladoc comment in definition: ${definition.getText}")
    docComment.getNode.getChildren(null)(1).getChildren(null)(2).getPsi
  }

  def createScalaDocTagName(@NonNls name: String)(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("/**@" + name + " qwerty */", ScalaFeatures.default)
      .typeDefinitions
      .head
      .docComment
      .get
      .getNode
      .getChildren(null)(1)
      .getChildren(null)(0)
      .getPsi

  def createScalaDocLinkValue(@NonNls text: String)
                             (implicit ctx: ProjectContext): ScDocResolvableCodeReference =
    PsiTreeUtil.findChildOfType(createScalaDocComment(s"/**[[$text]]*/"), classOf[ScDocResolvableCodeReference])

  def createXmlEndTag(@NonNls tagName: String)
                     (implicit ctx: ProjectContext): ScXmlEndTag =
    createScalaFileFromText(s"val a = <$tagName></$tagName>", ScalaFeatures.default)
      .getFirstChild.getLastChild.getFirstChild.getLastChild.asInstanceOf[ScXmlEndTag]

  def createXmlStartTag(@NonNls tagName: String, @NonNls attributes: String = "")
                       (implicit ctx: ProjectContext): ScXmlStartTag =
    createScalaFileFromText(s"val a = <$tagName$attributes></$tagName>", ScalaFeatures.default)
      .getFirstChild.getLastChild.getFirstChild.getFirstChild.asInstanceOf[ScXmlStartTag]

  def createInterpolatedStringPrefix(@NonNls prefix: String)(implicit ctx: ProjectContext): PsiElement =
    createElementFromText[PsiElement](prefix + "\"blah\"", ScalaFeatures.default).getFirstChild

  def createEquivMethodCall(infix: ScInfixExpr): ScMethodCall = {
    val ScInfixExpr.withAssoc(base, ElementText(operationText), argument) = infix

    val clauseText = argument match {
      case _: ScTuple | _: ScParenthesisedExpr | _: ScUnitExpr => argument.getText
      case ElementText(text) & ScOptionalBracesOwner.withColon(colon) =>
        val textAfterColon = text.substring(colon.getTextRangeInParent.getStartOffset + 1)
        s"$textAfterColon\n".parenthesize()
      case ElementText(text) => text.parenthesize()
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

  private[this] def createClassWithBody(
    @NonNls body:  String,
    scalaFeatures: ScalaFeatures,
  )(implicit ctx: ProjectContext
  ): ScTypeDefinition = {
    // ATTENTION!  Do not use `stripMargin` here!
    // If the injected `body` contains multiline string with margins '|' they will be whipped out (see SCL-14585)
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = s"\n  $body\n")
      .withScalaFeatures(scalaFeatures)
      .withProjectContext(ctx)
      .createTemplateDefinition()
      .asInstanceOf[ScClass]
  }

  private[this] def createGivenDefWithBody(
    @NonNls body: String,
    scalaFeatures: ScalaFeatures,
  )(implicit ctx: ProjectContext
  ): ScGivenDefinition = {
    // See comment in ScalaPsiElementFactory.createClassWithBody
    TemplateDefinitionBuilder(kind = TemplateDefKind.Given, body = s"\n  $body\n")
      .withScalaFeatures(scalaFeatures)
      .withProjectContext(ctx)
      .createTemplateDefinition()
      .asInstanceOf[ScGivenDefinition]
  }

  private[this] def createMemberFromText(
    @NonNls text: String,
    features:     ScalaFeatures
  )(implicit
    ctx: ProjectContext
  ): ScMember = {
    // We need to force braces here, otherwise the indentation needs to be correct in `text`
    // If we would adjust it ourselves, we change the intended indentation of text, that leads to problems downstream
    val clazz = TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = s"\n$text\n", forceBraces = true)
      .withScalaFeatures(features)
      .withProjectContext(ctx)
      .createTemplateDefinition()
      .asInstanceOf[ScClass]
    clazz.members.head
  }

  final class ScalaPsiElementCreationException(message: String, @Nullable cause: Throwable)
    extends IncorrectOperationException(message, cause)

  private object ScalaPsiElementCreationException {

    def apply(@NonNls kind: String,
              @NonNls text: String,
              @Nullable context: PsiElement = null,
              @Nullable cause: Throwable = null): ScalaPsiElementCreationException = {
      val contextSuffix = context match {
        case null => ""
        case _ => "; with context: " + context.getText
      }
      new ScalaPsiElementCreationException(s"Cannot create $kind from text: $text$contextSuffix", cause)
    }
  }

  val SyntheticFileKey: Key[true] = Key.create("SCALA_SYNTHETIC_FILE_KEY")
}
