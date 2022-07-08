package org.jetbrains.plugins.scala
package settings
package annotations

import com.intellij.psi.{PsiElement, PsiEnumConstant}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec

sealed abstract class Implementation {

  import Implementation._

  final def containsReturn: Boolean = returnCandidates.exists {
    case _: ScReturn => true
    case _ => false
  }

  final def hasStableType: Boolean = {
    @tailrec
    def hasStableTypeInner(expr: ScExpression): Boolean = expr match {
      case literal: ScLiteral => literal.getFirstChild.getNode.getElementType != ScalaTokenTypes.kNULL
      case definition: ScNewTemplateDefinition => definition.extendsBlock.templateBody.isEmpty
      case ScParenthesisedExpr(inner) => hasStableTypeInner(inner)
      case _: ScUnitExpr |
           _: ScThrow |
           ScReferenceExpression(_: PsiEnumConstant) |
           StableApplyCall() |
           ScMethodCall(StableApplyCall(), _) |
           EmptyCollectionFactoryCall(_) => true
      case _ => false
    }

    bodyCandidate.exists(hasStableTypeInner)
  }

  protected def returnCandidates: Iterator[PsiElement]

  protected def bodyCandidate: Option[ScExpression]
}

sealed abstract class Definition extends Implementation {

  def name: Option[String] = None

  def parameterList: Option[ScalaPsiElement] = None

  override def bodyCandidate: Option[ScExpression] = None

  override protected def returnCandidates: Iterator[PsiElement] = Iterator.empty

  def hasCustomIndents: Boolean = assignment.exists { it =>
    (it.prevSibling ++ it.nextSibling).exists(_.getText.count(_ == ' ') > 1)
  }

  protected def assignment: Option[PsiElement] = None
}

object Definition {

  def apply(element: PsiElement): Definition = element match {
    case value: ScPatternDefinition => ValueDefinition(value)
    case variable: ScVariableDefinition => VariableDefinition(variable)
    case function: ScFunctionDefinition => FunctionDefinition(function)
    case _ => new Definition {} // TODO support isSimple for JavaPsi
  }

  case class ValueDefinition(value: ScPatternDefinition) extends Definition {

    override def name: Option[String] = if (value.isSimple) value.names.headOption else None

    override def bodyCandidate: Option[ScExpression] =
      if (value.isSimple) value.expr
      else None

    override def parameterList: Option[ScalaPsiElement] =
      if (value.hasExplicitType) None
      else Some(value.pList)

    override protected def assignment: Option[PsiElement] = value.assignment
  }

  case class VariableDefinition(variable: ScVariableDefinition) extends Definition {

    override def name: Option[String] = if (variable.isSimple) variable.names.headOption else None

    override def bodyCandidate: Option[ScExpression] =
      if (variable.isSimple) variable.expr
      else None

    override def parameterList: Option[ScalaPsiElement] =
      if (variable.hasExplicitType) None
      else Some(variable.pList)

    override protected def assignment: Option[PsiElement] = variable.assignment
  }

  case class FunctionDefinition(function: ScFunctionDefinition) extends Definition {

    override def name: Option[String] = Some(function.name)

    override def parameterList: Option[ScalaPsiElement] =
      if (function.hasExplicitType || function.isConstructor) None
      else Some(function.parameterList)

    override def bodyCandidate: Option[ScExpression] =
      if (function.hasAssign && !function.isConstructor) function.body
      else None

    override protected def returnCandidates: Iterator[PsiElement] =
      function.returnUsages.iterator

    override protected def assignment: Option[PsiElement] = function.assignment
  }

}

case class Expression(expression: ScExpression) extends Implementation {

  override protected def returnCandidates: Iterator[PsiElement] = expression.depthFirst()

  override protected def bodyCandidate: Option[ScExpression] = Some(expression)
}

object Implementation {

  private object StableApplyCall {
    def unapply(reference: ScReferenceExpression): Boolean =
      reference.bind()
        .filter(referencesObject)
        .flatMap(_.innerResolveResult)
        .map(_.element)
        .flatMap(_.asOptionOf[ScFunction])
        .exists(_.isApplyMethod)

    private def referencesObject(rr: ScalaResolveResult): Boolean =
      rr.element.asOptionOfUnsafe[Typeable]
        .flatMap(_.`type`().toOption)
        .flatMap(_.asOptionOf[DesignatorOwner])
        .exists(_.element.is[ScObject])
  }

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
