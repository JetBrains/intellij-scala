package org.jetbrains.plugins.scala.uast

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiType, PsiWhiteSpace}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{&, ElementType, NextSibling, ObjectExt, PsiElementExt, PsiTypeExt, StringExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{kVAL, kVAR}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScLongLiteral, ScNullLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createBlockWithGivenExpressions, createScalaElementFromTextWithContext}
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.uast.controlStructures.ScUIfExpression
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter.UConvertible
import org.jetbrains.plugins.scala.lang.psi.uast.expressions._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.uast.UastContextKt.toUElement
import org.jetbrains.uast._
import org.jetbrains.uast.generate.{UParameterInfo, UastElementFactory}

import _root_.java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._

final class ScalaUastElementFactory(project: Project) extends UastElementFactory {
  private implicit val projectContext: ProjectContext = project

  @Nullable
  override def createBinaryExpression(leftOperand: UExpression,
                                      rightOperand: UExpression,
                                      operator: UastBinaryOperator,
                                      @Nullable context: PsiElement): UBinaryExpression =
    createInfixExpr(leftOperand, rightOperand, operator, context)
      .map(new ScUBinaryExpression(_, LazyUElement.Empty))
      .orNull

  @Nullable
  override def createFlatBinaryExpression(leftOperand: UExpression,
                                          rightOperand: UExpression,
                                          operator: UastBinaryOperator,
                                          @Nullable context: PsiElement): UPolyadicExpression = {
    def unwrapParentheses(expr: ScExpression): Unit = expr match {
      case p@ScParenthesisedExpr(e) if !ScalaPsiUtil.needParentheses(p, e) => p.replace(e)
      case _ =>
    }

    val maybeInfixExpr = createInfixExpr(leftOperand, rightOperand, operator, context)
    maybeInfixExpr.foreach { infixExpr =>
      unwrapParentheses(infixExpr.left)
      unwrapParentheses(infixExpr.right)
    }

    toUElement(maybeInfixExpr.orNull, classOf[UPolyadicExpression])
  }

  @Nullable
  override def createBlockExpression(expressions: util.List[_ <: UExpression], @Nullable context: PsiElement): UBlockExpression = {
    val block = createBlockWithGivenExpressions(expressions.asScala.toSeq.flatMap(_.getSourcePsi.toOption), context)
    block.context = context
    new ScUBlockExpression(block, LazyUElement.Empty)
  }

  @Nullable // TODO: implement type params handling
  override def createCallExpression(@Nullable receiver: UExpression,
                                    methodName: String,
                                    parameters: util.List[_ <: UExpression],
                                    @Nullable expectedReturnType: PsiType,
                                    kind: UastCallKind,
                                    @Nullable context: PsiElement = null): UCallExpression = {
    if (kind != UastCallKind.METHOD_CALL) return null

    val str = createCallExpressionTemplateRespectingChainStyle(receiver)
    val callExpr =
      createScalaElementFromTextWithContext[ScMethodCall](str, context).orNull
    if (callExpr == null) return null

    val methodIdentifier = ScalaPsiElementFactory.createIdentifier(methodName).getPsi

    if (receiver != null) {
      callExpr.thisExpr.foreach(_.replace(receiver.getSourcePsi))
    }

    callExpr.getInvokedExpr match {
      case named: ScNamedElement =>
        named.nameId.replace(methodIdentifier)
      case ref: ScReference =>
        ref.nameId.replace(methodIdentifier)
      case _ =>
    }

    if (!parameters.isEmpty) {
      val args = callExpr.args
      // reverse because ScArgumentExprList.addExpr adds to the beginning of the clause
      parameters.asScala.reverse.foreach { parameter =>
        args.addExpr(parameter.getSourcePsi.asInstanceOf[ScExpression])
      }
    }

    // TODO: if expectedReturnType != null then handle type params to match return type
    callExpr.convertTo[UCallExpression](parent = null).orNull
  }

  @Nullable
  override def createCallableReferenceExpression(@Nullable receiver: UExpression,
                                                 methodName: String,
                                                 @Nullable context: PsiElement): UCallableReferenceExpression = {
    val textWithReceiver = for {
      rcv <- receiver.toOption
      src <- rcv.getSourcePsi.toOption
    } yield s"${src.getText}.$methodName"

    createScalaElementFromTextWithContext[ScReference](textWithReceiver.getOrElse(methodName), context)
      .map(new ScUCallableReferenceExpression(_, LazyUElement.Empty))
      .orNull
  }

  @Nullable
  override def createDeclarationExpression(declarations: util.List[_ <: UDeclaration],
                                           @Nullable context: PsiElement): UDeclarationsExpression =
    new ScUDeclarationsExpression(declarations.asScala.toList, LazyUElement.Empty)

  @Nullable
  override def createIfExpression(condition: UExpression,
                                  thenBranch: UExpression,
                                  @Nullable elseBranch: UExpression,
                                  @Nullable context: PsiElement): UIfExpression = {
    val conditionPsi = condition.getSourcePsi match {
      case e: ScExpression => e
      case _ => return null
    }
    val thenBranchPsi = thenBranch.getSourcePsi match {
      case e: ScExpression => e
      case _ => return null
    }
    val maybeElseBranchPsi = elseBranch.toOption.flatMap(_.getSourcePsi.asOptionOf[ScExpression])

    val ifExprText = maybeElseBranchPsi match {
      case Some(_) => "if (a) b else c"
      case _ => "if (a) b"
    }

    val scIf = createScalaElementFromTextWithContext[ScIf](ifExprText, context).orNull
    if (scIf == null) return null

    scIf.condition.foreach(_.replace(conditionPsi))
    scIf.thenExpression.foreach(_.replace(thenBranchPsi))
    maybeElseBranchPsi.foreach(psi => scIf.elseExpression.foreach(_.replaceExpression(psi, removeParenthesis = true)))

    new ScUIfExpression(scIf, LazyUElement.Empty)
  }

  @Nullable
  override def createLambdaExpression(parameters: util.List[UParameterInfo],
                                      body: UExpression,
                                      @Nullable context: PsiElement): ULambdaExpression = {
    val nameSuggester = new NameSuggester.UniqueNameSuggester()

    val statements = body match {
      case block: UBlockExpression =>
        block.getExpressions.asScala
          .flatMap {
            case ret: UReturnExpression => ret.getReturnExpression.toOption.flatMap(_.getSourcePsi.toOption)
            case expr => expr.getSourcePsi.toOption
          }
      case _ =>
        val bodyPsi = body.getSourcePsi
        if (bodyPsi == null) return null
        Seq(bodyPsi)
    }

    val params = parameters.asScala
    val needsParensAroundParams = params.sizeIs != 1 || params.head.getType != null
    val paramText = params.map { p =>
      val suggestedName = p.getSuggestedName.toOption
      val scType = p.getType.toOption.map(_.toScType())

      val name = suggestedName match {
        case Some(name) => name
        case None =>
          scType match {
            case Some(tp) => nameSuggester(tp)
            case None => nameSuggester(Nil)
          }
      }

      scType match {
        case Some(tp) => s"$name: ${tp.presentableText(TypePresentationContext.emptyContext)}"
        case _ => name
      }
    }.mkString(if (needsParensAroundParams) "(" else "", ", ", if (needsParensAroundParams) ")" else "")

    ScalaPsiElementFactory.createScalaFileFromText(s"Option(???).map { $paramText => () }", context)
      .getFirstChild
      .asInstanceOf[ScMethodCall]
      .argumentExpressions
      .head
      .asInstanceOf[ScBlockExpr]
      .asSimpleExpression
      .collect {
        case fn: ScFunctionExpr =>
          fn.context = context

          val newBody =
            if (statements.sizeIs == 1) statements.head
            else createBlockWithGivenExpressions(statements, context)

          fn.result.foreach(_.replace(newBody))
          new ScULambdaExpression(fn, LazyUElement.Empty)
      }.orNull
  }

  @Nullable
  override def createLocalVariable(@Nullable suggestedName: String,
                                   @Nullable tpe: PsiType,
                                   initializer: UExpression,
                                   immutable: Boolean,
                                   @Nullable context: PsiElement): ULocalVariable = {
    val initializerPsi = initializer.getSourcePsi
    if (initializerPsi == null) return null

    val scType = tpe.toOption.map(_.toScType())

    val varBuilder = new mutable.StringBuilder()
    varBuilder.append(if (immutable) kVAL else kVAR)

    val name =
      if (suggestedName != null) suggestedName
      else {
        val nameSuggester = new NameSuggester.UniqueNameSuggester()
        scType match {
          case Some(tp) => nameSuggester(tp)
          case _ => nameSuggester(Nil)
        }
      }

    varBuilder.append(" ").append(name)
    scType.foreach(varBuilder.append(": ").append(_))
    varBuilder.append(" = ???")

    (for {
      psiVariable <- createScalaElementFromTextWithContext[ScValueOrVariableDefinition](varBuilder.result(), context)
      _ = psiVariable.expr.foreach(_.replace(initializerPsi))
      firstBinding <- psiVariable.bindings.headOption
      localVariable <- toUElement(firstBinding).asOptionOf[ULocalVariable]
    } yield localVariable).orNull
  }

  @Nullable
  override def createLongConstantExpression(long: Long, @Nullable context: PsiElement): UExpression =
    toUElement(createScalaElementFromTextWithContext[ScLongLiteral](s"${long}L", context).orNull, classOf[UExpression])

  @Nullable
  override def createNullLiteral(@Nullable context: PsiElement): ULiteralExpression =
    toUElement(createScalaElementFromTextWithContext[ScNullLiteral]("null", context).orNull, classOf[ULiteralExpression])

  @Nullable
  override def createParenthesizedExpression(expression: UExpression, @Nullable context: PsiElement): UParenthesizedExpression = {
    val sourcePsi = expression.getSourcePsi
    if (sourcePsi == null) return null

    createScalaElementFromTextWithContext[ScParenthesisedExpr](sourcePsi.getText.parenthesize(), context)
      .map(new ScUParenthesizedExpression(_, LazyUElement.Empty))
      .orNull
  }

  @Nullable
  override def createQualifiedReference(qualifiedName: String, @Nullable context: PsiElement): UQualifiedReferenceExpression =
    createScalaElementFromTextWithContext[ScReferenceExpression](qualifiedName, context)
      .map(new ScUQualifiedReferenceExpression(_, LazyUElement.Empty))
      .orNull

  @Nullable
  override def createReturnExpression(@Nullable expression: UExpression,
                                      isLambda: Boolean,
                                      @Nullable context: PsiElement): UReturnExpression = {
    val returnExpr = createScalaElementFromTextWithContext[ScReturn]("return 1", context).orNull
    val sourcePsi = if (expression == null) null else expression.getSourcePsi
    if (sourcePsi == null) returnExpr.expr.foreach(_.delete())
    else returnExpr.expr.foreach(_.replace(sourcePsi))
    new ScUReturnExpression(returnExpr, LazyUElement.Empty)
  }

  @Nullable
  override def createSimpleReference(name: String, @Nullable context: PsiElement): USimpleNameReferenceExpression =
    createScalaElementFromTextWithContext[ScReference](name, context)
      .map(new ScUSimpleNameReferenceExpression(_, None, LazyUElement.Empty))
      .orNull

  @Nullable
  override def createSimpleReference(variable: UVariable, @Nullable context: PsiElement): USimpleNameReferenceExpression = {
    val name = variable.getName
    if (name == null) null else createSimpleReference(name, context)
  }

  @Nullable
  override def createStringLiteralExpression(text: String, @Nullable context: PsiElement): ULiteralExpression =
    toUElement(createScalaElementFromTextWithContext[ScStringLiteral](StringUtil.wrapWithDoubleQuote(text), context).orNull, classOf[ULiteralExpression])

  @Nullable
  override def createMethodFromText(methodText: String, @Nullable context: PsiElement): UMethod =
    toUElement(ScalaPsiElementFactory.createMethodFromText(methodText, context), classOf[UMethod])

  private def createInfixExpr(leftOperand: UExpression,
                              rightOperand: UExpression,
                              operator: UastBinaryOperator,
                              @Nullable context: PsiElement): Option[ScInfixExpr] = for {
    leftPsi <- leftOperand.getSourcePsi.toOption
    rightPsi <- rightOperand.getSourcePsi.toOption
    expr <- createScalaElementFromTextWithContext[ScInfixExpr](s"a ${operator.getText} b", context)
  } yield {
    expr.left.replace(leftPsi)
    expr.right.replace(rightPsi)
    expr
  }

  private def createCallExpressionTemplateRespectingChainStyle(@Nullable receiver: UExpression): String =
    if (receiver != null) receiver.getSourcePsi.toOption.flatMap(_.nextSibling) match {
      case Some(ws: PsiWhiteSpace) => s"a${ws.getText}.b()"
      case Some(ElementType(ScalaTokenTypes.tDOT) & NextSibling(ws: PsiWhiteSpace)) =>
        s"a.${ws.getText}b()"
      case _ => "a.b()"
    } else "a()"
}
