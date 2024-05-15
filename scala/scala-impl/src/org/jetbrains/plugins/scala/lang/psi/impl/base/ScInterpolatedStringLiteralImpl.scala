package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.{ASTNode, LanguageNamesValidation}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScInterpolatedStringLiteralImpl.Log
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.{ScLiteralEscaper, ScLiteralRawEscaper}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.StringContextCanonical

import scala.meta.intellij.QuasiquoteInferUtil._
import scala.util.control.NonFatal

// todo: move to "literals" subpackage, but check usages
final class ScInterpolatedStringLiteralImpl(node: ASTNode,
                                            override val toString: String)
  extends ScStringLiteralImpl(node, toString)
    with ScInterpolatedStringLiteral {

  import ScInterpolatedStringLiteral._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  override def kind: Kind = Kind.fromPrefix(referenceText)

  protected override def innerType: TypeResult =
    desugaredExpression.fold(Failure(ScalaBundle.message("cannot.find.method.of.stringcontext", referenceText)): TypeResult) {
      case (reference, _) if isMetaQQ(reference) =>
        getMetaQQExprType(this)
      case (reference, call) =>
        val typeProvider = InterpolatedStringMacroTypeProvider.getTypeProvider(reference)
        typeProvider.fold(call.getNonValueType()) {
          _.inferExpressionType(this)
        }
    }

  override def reference: ScReferenceExpression = getFirstChild.asInstanceOf[ScReferenceExpression]

  override def referenceName: String = reference.refName

  override def hasValidClosingQuotes: Boolean =
    getNode.getLastChildNode.getElementType == tINTERPOLATED_STRING_END

  override def isMultiLineString: Boolean = hasValidClosingQuotes && {
    val next = firstNode.getTreeNext
    next != null && next.getElementType == tINTERPOLATED_MULTILINE_STRING
  }

  override protected def startQuote: String = referenceText + super.startQuote

  override protected def endQuote: String = super.startQuote

  override def desugaredExpression: Option[(ScReferenceExpression, ScMethodCall)] = cachedInUserData("desugaredExpression", this, BlockModificationTracker(this)) {
    (referenceText, getContext) match {
      case (methodName, context) if context != null &&
        hasValidClosingQuotes &&
        isValidIdentifier(methodName) =>
        val quote = endQuote

        // NOTE: we don't need to actually extract all the string parts content during resolve,
        // some dummy placeholders is enough
        val constructorParameters = getStringPartsDummies.map(quote + _ + quote)
          .commaSeparated(Model.Parentheses)

        val injectionsValues = getInjections.map { injection =>
          val text = injection.getText
          val isInvalidRef = injection.is[ScReferenceExpression] && !isValidIdentifier(text)
          if (isInvalidRef) "???" else text
        }
        val methodParameters = injectionsValues.commaSeparated(Model.Parentheses)

        val expression =
          try {
            // FIXME: fails on s"aaa /* ${s"ccc s${s"/*"} ddd"} bbb" (SCL-17625, SCL-18706)
            val text = s"$StringContextCanonical$constructorParameters.$methodName$methodParameters"
            ScalaPsiElementFactory.createExpressionWithContextFromText(text, context, this).asInstanceOf[ScMethodCall]
          } catch {
            case e: IncorrectOperationException =>
              throw new IncorrectOperationException(s"Couldn't desugar interpolated string ${this.getText}", e: Throwable)
          }
        Some(expression.getInvokedExpr.asInstanceOf[ScReferenceExpression], expression)
      case _ => None
    }
  }

  private def referenceText: String = firstNode.getText

  private def isValidIdentifier(name: String) = {
    val validation = LanguageNamesValidation.INSTANCE.forLanguage(getLanguage)
    val project = getProject

    !validation.isKeyword(name, project) &&
      validation.isIdentifier(name, project)
  }

  override def createLiteralTextEscaper: LiteralTextEscaper[ScStringLiteral] =
    if (kind == Raw)
      new ScLiteralRawEscaper(this)
    else
      new ScLiteralEscaper(this)

  override def updateText(text: String): ScStringLiteralImpl = try {
    val newStringLiteral = ScalaPsiElementFactory.createStringLiteralFromText(text, this.features).asInstanceOf[ScStringLiteralImpl]
    getParent.getNode.replaceChild(getNode, newStringLiteral.getNode)
    newStringLiteral
  } catch {
    case NonFatal(ex) =>
      Log.warn(ex)
      //TODO: this is a fallback workaround for SCL-22507 and IJPL-149605
      // it should be removed once IJPL-149605 is resolved
      this
  }
}

object ScInterpolatedStringLiteralImpl {
  private val Log = Logger.getInstance(this.getClass)
}