package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.psi.{PsiElement, PsiEnumConstant}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
  * @author Pavel Fatin
  */
sealed trait Implementation {

  import Implementation._

  final def containsReturn: Boolean = returnCandidates.exists(_.isInstanceOf[ScReturnStmt])

  final def isTypeStable: Boolean = bodyCandidate.exists {
    case literal: ScLiteral => literal.getFirstChild.getNode.getElementType != ScalaTokenTypes.kNULL
    case _: ScUnitExpr => true
    case _: ScThrowStmt => true
    case it: ScNewTemplateDefinition if it.extendsBlock.templateBody.isEmpty => true
    case ref: ScReferenceExpression if isApplyCall(ref) => true
    case ScReferenceExpression(_: PsiEnumConstant) => true
    case EmptyCollectionFactoryCall(_) => true
    case ScMethodCall(invoked: ScReferenceExpression, _) if isApplyCall(invoked) => true
    case _ => false
  }

  protected def returnCandidates: Iterator[PsiElement]

  protected def bodyCandidate: Option[ScExpression]
}

case class Definition(element: PsiElement) extends Implementation {

  override protected def returnCandidates: Iterator[PsiElement] = element match {
    case f: ScFunctionDefinition => f.returnUsages.iterator
    case _ => Iterator.empty
  }

  override protected def bodyCandidate: Option[ScExpression] = element match {
    case value: ScPatternDefinition if value.isSimple => value.expr
    case variable: ScVariableDefinition if variable.isSimple => variable.expr
    case method: ScFunctionDefinition if method.hasAssign && !method.isConstructor => method.body
    case _ => None //support isSimple for JavaPsi
  }
}

case class Expression(expression: ScExpression) extends Implementation {

  override protected def returnCandidates: Iterator[PsiElement] = expression.depthFirst()

  override protected def bodyCandidate: Option[ScExpression] = Some(expression)
}

object Implementation {

  private def isApplyCall(reference: ScReferenceExpression): Boolean =
    reference.bind().map { result =>
      result.innerResolveResult.getOrElse(result).element
    }.exists {
      case function: ScFunction => function.isApplyMethod
      case _ => false
    }

  // TODO Restore encapsulation
  object EmptyCollectionFactoryCall {

    private[this] val TraversableClassNames =
      Set("Seq", "Array", "List", "Vector", "Set", "HashSet", "Map", "HashMap", "Iterator", "Option")

    def unapply(genericCall: ScGenericCall): Option[ScReferenceExpression] = genericCall match {
      case ScGenericCall(ref@ScReferenceExpression.withQualifier(qualifier: ScReferenceExpression), _)
        if TraversableClassNames(qualifier.refName) && ref.refName == "empty" => Some(ref)
      case _ => None
    }

  }
}
