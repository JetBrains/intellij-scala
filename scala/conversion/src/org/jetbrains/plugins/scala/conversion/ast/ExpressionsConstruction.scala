package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

case class ArrayAccess(
  expression: IntermediateNode,
  idxExpression: IntermediateNode
) extends ExpressionsHolderNodeBase(expression :: idxExpression :: Nil)

case class ClassCast(
  operand: IntermediateNode,
  castType: TypeNode,
  isPrimitive: Boolean
) extends ExpressionsHolderNodeBase(operand :: Nil) with TypedElement {

  def canSimplify: Boolean =
    isPrimitive && List("Int", "Long", "Double", "Float", "Byte", "Char", "Short").contains(castType.asInstanceOf[TypeConstruction].inType)

  override def getType: TypeConstruction = castType.asInstanceOf[TypedElement].getType
}

case class ArrayInitializer(expressions: Seq[IntermediateNode])
  extends ExpressionsHolderNodeBase(expressions)

case class BinaryExpressionConstruction(
  firstPart: IntermediateNode,
  secondPart: IntermediateNode,
  operation: String,
  inExpression: Boolean
) extends ExpressionsHolderNodeBase(firstPart :: secondPart :: Nil)

case class ClassObjectAccess(expression: IntermediateNode)
  extends ExpressionsHolderNodeBase(expression :: Nil)

case class InstanceOfConstruction(
  operand: IntermediateNode,
  mtype: TypeNode
) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = mtype.asInstanceOf[TypedElement].getType
}

case class QualifiedExpression(
  qualifier: IntermediateNode,
  identifier: IntermediateNode
) extends IntermediateNode

object MethodCallExpression extends IntermediateNode {

  def build(receiver: IntermediateNode, methodName: String, args: ExpressionList): MethodCallExpression = {
    val escapedName = methodName match {
      case "this" => methodName
      case _ => ScalaNamesUtil.escapeKeyword(methodName)
    }

    val identifier = LiteralExpression(escapedName)
    val method = receiver match {
      case null => identifier
      case qualifier => QualifiedExpression(qualifier, identifier)
    }

    MethodCallExpression(method, args, withSideEffects = false)
  }
}

case class MethodCallExpression(
  method: IntermediateNode,
  args: ExpressionList,
  withSideEffects: Boolean
) extends ExpressionsHolderNodeBase(args :: Nil)

case class ExpressionList(data: Seq[IntermediateNode])
  extends ExpressionsHolderNodeBase(data)

case class ThisExpression(value: Option[IntermediateNode]) extends IntermediateNode
case class SuperExpression(value: Option[IntermediateNode]) extends IntermediateNode
case class LiteralExpression(literal: String) extends IntermediateNode

case class RangeExpression(from: IntermediateNode, to: IntermediateNode, inclusive: Boolean, descending: Boolean)
  extends ExpressionsHolderNodeBase(from :: to :: Nil)

case class ParenthesizedExpression(value: Option[IntermediateNode])
  extends ExpressionsHolderNodeBase(value.toSeq)

case class FunctionalExpression(params: ParameterListConstruction, body: IntermediateNode)
  extends ExpressionsHolderNodeBase(body :: Nil)

object NewExpression {
  def apply(
    mtype: TypeNode,
    arrayInitalizer: Seq[IntermediateNode],
    withArrayInitalizer: Boolean = true
  ): NewExpression = {
    if (withArrayInitalizer)
      NewExpression(mtype, arrayInitalizer, Seq[IntermediateNode]())
    else
      NewExpression(mtype, Seq[IntermediateNode](), arrayInitalizer)
  }
}

case class NewExpression(
  mtype: TypeNode,
  arrayInitializer: Seq[IntermediateNode],
  arrayDimension: Seq[IntermediateNode]
) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = mtype.asInstanceOf[TypedElement].getType
}

case class AnonymousClassExpression(anonymousClass: AnonymousClass) extends IntermediateNode

case class PolyadicExpression(args: Seq[IntermediateNode], operation: String) extends ExpressionsHolderNodeBase(args)

case class PrefixExpression(operand: IntermediateNode, signType: String, canBeSimplified: Boolean) extends ExpressionsHolderNodeBase(operand :: Nil)
case class PostfixExpression(operand: IntermediateNode, signType: String, canBeSimplified: Boolean) extends ExpressionsHolderNodeBase(operand :: Nil)