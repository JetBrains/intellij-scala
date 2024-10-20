package org.jetbrains.plugins.scala.lang.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.scala.lang.psi.impl.base._
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterTypeImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl

object ASTNodeToPsiElement {

  def map(node: ASTNode): PsiElement = {
    import ScalaElementType._

    node.getElementType match {
      case creator: SelfPsiCreator => creator.createElement(node) // stub elements still implement this trait

      case ScCodeBlockElementType.BlockExpression =>
        PsiUtilCore.NULL_PSI_ELEMENT

      /* Definition parts */
      case CONSTRUCTOR => new ScConstructorInvocationImpl(node)
      case PARAM_TYPE => new ScParameterTypeImpl(node)
      case SEQUENCE_ARG => new ScSequenceArgImpl(node)
      case REFERENCE => new ScStableCodeReferenceImpl(node)
      case DOC_REFERENCE =>
        /* NOTE: only created to be used from
         * [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory#createDocReferenceFromText]]
         * to create a syntetic reference from doc
         */
        new ScDocResolvableCodeReferenceImpl(node)

      case ANNOTATION_EXPR => new ScAnnotationExprImpl(node)
      case END_STMT => new ScEndImpl(node)

      /* Types */
      case COMPOUND_TYPE => new ScCompoundTypeElementImpl(node)
      case EXISTENTIAL_TYPE => new ScExistentialTypeElementImpl(node)
      case SIMPLE_TYPE => new ScSimpleTypeElementImpl(node)
      case INFIX_TYPE => new ScInfixTypeElementImpl(node)
      case TYPE => new ScFunctionalTypeElementImpl(node)
      case ANNOT_TYPE => new ScAnnotTypeElementImpl(node)
      case WILDCARD_TYPE => new ScWildcardTypeElementImpl(node)
      case TUPLE_TYPE => new ScTupleTypeElementImpl(node)
      case NAMED_TUPLE_TYPE => new ScNamedTupleTypeElementImpl(node)
      case TYPE_IN_PARENTHESIS => new ScParenthesisedTypeElementImpl(node)
      case TYPE_PROJECTION => new ScTypeProjectionImpl(node)
      case TYPE_GENERIC_CALL => new ScParameterizedTypeElementImpl(node)
      case LITERAL_TYPE => new ScLiteralTypeElementImpl(node)
      case TYPE_VARIABLE => new ScTypeVariableTypeElementImpl(node)
      case SPLICED_BLOCK_TYPE => new ScSplicedBlockImpl(node)
      case SPLICED_PATTERN_EXPR => new ScSplicedPatternExprImpl(node)
      case TYPE_LAMBDA => new ScTypeLambdaTypeElementImpl(node)
      case MATCH_TYPE => new ScMatchTypeElementImpl(node)
      case POLY_FUNCTION_TYPE => new ScPolyFunctionTypeElementImpl(node)
      case DEPENDENT_FUNCTION_TYPE => new ScDependentFunctionTypeElementImpl(node)

      /* Type parts */
      case TYPE_ARGS => new ScTypeArgsImpl(node)
      case EXISTENTIAL_CLAUSE => new ScExistentialClauseImpl(node)
      case TYPES => new ScTypesImpl(node)
      case TYPE_CASE_CLAUSES => new ScMatchTypeCasesImpl(node)
      case TYPE_CASE_CLAUSE => new ScMatchTypeCaseImpl(node)
      case CONTEXT_BOUND    => new ScContextBoundImpl(node)
      case NAMED_TUPLE_TYPE_COMPONENT => new ScNamedTupleTypeComponentImpl(node)

      /* Expressions */
      case PREFIX_EXPR => new ScPrefixExprImpl(node)
      case POSTFIX_EXPR => new ScPostfixExprImpl(node)
      case INFIX_EXPR => new ScInfixExprImpl(node)
      case PLACEHOLDER_EXPR => new ScUnderscoreSectionImpl(node)
      case PARENT_EXPR => new ScParenthesisedExprImpl(node)
      case METHOD_CALL => new ScMethodCallImpl(node)
      case REFERENCE_EXPRESSION => new ScReferenceExpressionImpl(node)
      case THIS_REFERENCE => new ScThisReferenceImpl(node)
      case SUPER_REFERENCE => new ScSuperReferenceImpl(node)
      case GENERIC_CALL => new ScGenericCallImpl(node)
      case FUNCTION_EXPR => new ScFunctionExprImpl(node)
      case POLY_FUNCTION_EXPR => new ScPolyFunctionExprImpl(node)
      case BLOCK => new ScBlockImpl(node)
      case SPLICED_BLOCK_EXPR => new ScSplicedBlockImpl(node)
      case QUOTED_BLOCK => new ScQuotedBlockImpl(node)
      case QUOTED_TYPE => new ScQuotedTypeImpl(node)
      case TUPLE => new ScTupleImpl(node)
      case NAMED_TUPLE => new ScNamedTupleImpl(node)
      case UNIT_EXPR => new ScUnitExprImpl(node)
      case CONSTR_BLOCK_EXPR => new ScConstrBlockExprImpl(node)
      case SELF_INVOCATION => new ScSelfInvocationImpl(node)
      case NullLiteral => new ScNullLiteralImpl(node, NullLiteral.toString)
      case LongLiteral => new ScLongLiteralImpl(node, LongLiteral.toString)
      case IntegerLiteral => new ScIntegerLiteralImpl(node, IntegerLiteral.toString)
      case DoubleLiteral => new ScDoubleLiteralImpl(node, DoubleLiteral.toString)
      case FloatLiteral => new ScFloatLiteralImpl(node, FloatLiteral.toString)
      case BooleanLiteral => new ScBooleanLiteralImpl(node, BooleanLiteral.toString)
      case SymbolLiteral => new ScSymbolLiteralImpl(node, SymbolLiteral.toString)
      case CharLiteral => new ScCharLiteralImpl(node, CharLiteral.toString)
      case StringLiteral => new ScStringLiteralImpl(node, StringLiteral.toString)
      case InterpolatedString => new ScInterpolatedStringLiteralImpl(node, InterpolatedString.toString)
      case INTERPOLATED_PREFIX_LITERAL_REFERENCE => new ScInterpolatedExpressionPrefix(node)

      /* Composite expressions */
      case IF_STMT => new ScIfImpl(node)
      case FOR_STMT => new ScForImpl(node)
      case DO_STMT => new ScDoImpl(node)
      case TRY_STMT => new ScTryImpl(node)
      case CATCH_BLOCK => new ScCatchBlockImpl(node)
      case FINALLY_BLOCK => new ScFinallyBlockImpl(node)
      case WHILE_STMT => new ScWhileImpl(node)
      case RETURN_STMT => new ScReturnImpl(node)
      case THROW_STMT => new ScThrowImpl(node)
      case ASSIGN_STMT => new ScAssignmentImpl(node)
      case MATCH_STMT => new ScMatchImpl(node)
      case TYPED_EXPR_STMT => new ScTypedExpressionImpl(node)

      /* Expression Parts */
      case GENERATOR => new ScGeneratorImpl(node)
      case FOR_BINDING => new ScForBindingImpl(node)
      case ENUMERATORS => new ScEnumeratorsImpl(node)
      case GUARD => new ScGuardImpl(node)
      case ARG_EXPRS => new ScArgumentExprListImpl(node)
      case INTERPOLATED_PREFIX_PATTERN_REFERENCE => new ScInterpolatedPatternPrefix(node)
      case NAMED_TUPLE_COMPONENT => new ScNamedTupleExprComponentImpl(node)

      /* Patterns */
      case TUPLE_PATTERN => new ScTuplePatternImpl(node)
      case CONSTRUCTOR_PATTERN => new ScConstructorPatternImpl(node)
      case PATTERN_ARGS => new ScPatternArgumentListImpl(node)
      case INFIX_PATTERN => new ScInfixPatternImpl(node)
      case PATTERN => new ScCompositePatternImpl(node)
      case PATTERNS => new ScPatternsImpl(node)
      case WILDCARD_PATTERN => new ScWildcardPatternImpl(node)
      case CASE_CLAUSE => new ScCaseClauseImpl(node)
      case CASE_CLAUSES => new ScCaseClausesImpl(node)
      case LITERAL_PATTERN => new ScLiteralPatternImpl(node)
      case QUOTED_PATTERN => new ScQuotedPatternImpl(node)
      case INTERPOLATION_PATTERN => new ScInterpolationPatternImpl(node)
      case StableReferencePattern =>
        new ScStableReferencePatternImpl(node, StableReferencePattern.toString)
      case PATTERN_IN_PARENTHESIS => new ScParenthesisedPatternImpl(node)
      case GIVEN_PATTERN => new ScGivenPatternImpl(node)
      case SCALA3_TYPED_PATTERN => new Sc3TypedPatternImpl(node)
      case NAMED_TUPLE_PATTERN => new ScNamedTuplePatternImpl(node)
      case NAMED_TUPLE_PATTERN_COMPONENT => new ScNamedTuplePatternComponentImpl(node)

      /* Type patterns */
      case TYPE_PATTERN => new ScTypePatternImpl(node)
      case REFINEMENT => new ScRefinementImpl(node)

      /* XML */
      case XML_EXPR => new ScXmlExprImpl(node)
      case XML_START_TAG => new ScXmlStartTagImpl(node)
      case XML_END_TAG => new ScXmlEndTagImpl(node)
      case XML_EMPTY_TAG => new ScXmlEmptyTagImpl(node)
      case XML_PI => new ScXmlPIImpl(node)
      case XML_CD_SECT => new ScXmlCDSectImpl(node)
      case XML_ATTRIBUTE => new ScXmlAttributeImpl(node)
      case XML_PATTERN => new ScXmlPatternImpl(node)
      case XML_COMMENT => new ScXmlCommentImpl(node)
      case XML_ELEMENT => new ScXmlElementImpl(node)

      /* Default case */
      case _ => new ASTWrapperPsiElement(node)
    }
  }
}
