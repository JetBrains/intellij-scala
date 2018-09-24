package org.jetbrains.plugins.scala
package settings
package annotations

import com.intellij.psi.{PsiElement, PsiEnumConstant}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
  * @author Pavel Fatin
  */
sealed trait Implementation {

  import Implementation._

  final def containsReturn: Boolean = returnCandidates.exists {
    case _: ScReturnStmt => true
    case _ => false
  }

  final def hasStableType: Boolean = bodyCandidate.exists {
    case literal: ScLiteral => literal.getFirstChild.getNode.getElementType != ScalaTokenTypes.kNULL
    case _: ScUnitExpr => true
    case _: ScThrowStmt => true
    case definition: ScNewTemplateDefinition if definition.extendsBlock.templateBody.isEmpty => true
    case StableApplyCall() => true
    case ScReferenceExpression(_: PsiEnumConstant) => true
    case EmptyCollectionFactoryCall(_) => true
    case ScMethodCall(StableApplyCall(), _) => true
    case _ => false
  }

  protected def returnCandidates: Iterator[PsiElement]

  protected def bodyCandidate: Option[ScExpression]
}

sealed trait Definition extends Implementation {

  def parameterList: Option[ScalaPsiElement] = None

  def bodyCandidate: Option[ScExpression] = None

  protected def returnCandidates: Iterator[PsiElement] = Iterator.empty
}

object Definition {

  def apply(element: PsiElement): Definition = element match {
    case value: ScPatternDefinition => ValueDefinition(value)
    case variable: ScVariableDefinition => VariableDefinition(variable)
    case function: ScFunctionDefinition => FunctionDefinition(function)
    case _ => new Definition {} // TODO support isSimple for JavaPsi
  }

  case class ValueDefinition(value: ScPatternDefinition) extends Definition {

    override def bodyCandidate: Option[ScExpression] =
      if (value.isSimple) value.expr
      else super.bodyCandidate

    override def parameterList: Option[ScalaPsiElement] =
      if (value.hasExplicitType) super.parameterList
      else Some(value.pList)
  }

  case class VariableDefinition(variable: ScVariableDefinition) extends Definition {

    override def bodyCandidate: Option[ScExpression] =
      if (variable.isSimple) variable.expr
      else super.bodyCandidate

    override def parameterList: Option[ScalaPsiElement] =
      if (variable.hasExplicitType) super.parameterList
      else Some(variable.pList)
  }

  case class FunctionDefinition(function: ScFunctionDefinition) extends Definition {

    override def parameterList: Option[ScalaPsiElement] =
      if (function.hasExplicitType || function.isConstructor) None
      else Some(function.parameterList)

    override def bodyCandidate: Option[ScExpression] =
      if (function.hasAssign && !function.isConstructor) function.body
      else super.bodyCandidate

    override protected def returnCandidates: Iterator[PsiElement] =
      function.returnUsages.iterator
  }

}

case class Expression(expression: ScExpression) extends Implementation {

  protected def returnCandidates: Iterator[PsiElement] = expression.depthFirst()

  override protected def bodyCandidate: Option[ScExpression] = Some(expression)
}

object Implementation {

  private object StableApplyCall {

    def unapply(reference: ScReferenceExpression): Boolean =
      reference.bind().map { result =>
        result.innerResolveResult.getOrElse(result).element
      }.exists {
        case (f: ScFunction) && ContainingClass(o: ScObject) => f.isApplyMethod
        case _ => false
      }
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
