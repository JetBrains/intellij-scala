package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.psi.{PsiElement, PsiEnumConstant, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
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
           MethodInvocation(StableApplyCall() | UniversalApply(), _) |
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

  override protected def returnCandidates: Iterator[PsiElement] = expression.depthFirst(!_.is[ScFunction])

  override protected def bodyCandidate: Option[ScExpression] = Some(expression)
}

object Implementation {

  private object StableApplyCall {
    @tailrec
    def unapply(expr: ScExpression): Boolean = {
      expr match {
        case ref: ScReferenceExpression => ref.bind().exists(isApplyMethodOfSameType)
        case call: ScGenericCall => unapply(call.referencedExpr)
        case _ => false
      }
    }


    private def isApplyMethodOfSameType(rr: ScalaResolveResult): Boolean = {
      val obj = rr.parentElement match {
        case Some(obj: ScObject) => obj
        case _ => return false
      }

      val call = rr.element match {
        case fun: ScFunction if fun.isApplyMethod => fun
        case _ => return false
      }

      // Check that the returned type is the companion of the object the apply method is in
      call.returnType
        .toOption
        .map(rr.substitutor)
        .flatMap(_.extractClass)
        .exists {
          case c: ScTypeDefinition =>
            obj.baseCompanion.contains(c)
          case _ => false
        }
    }
  }

  private object UniversalApply {
    def unapply(call: ScReferenceExpression): Boolean = {
      call.bind().flatMap(_.element.asOptionOf[PsiMethod]).exists(_.isConstructor)
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
