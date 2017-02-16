package org.jetbrains.plugins.scala.conversion.ast

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */

case class ArrayAccess(expression: IntermediateNode, idxExpression: IntermediateNode)
  extends IntermediateNode

case class ClassCast(operand: IntermediateNode,
                     castType: IntermediateNode,
                     isPrimitive: Boolean) extends IntermediateNode with TypedElement {
  def canSimplify: Boolean =
    isPrimitive && List("Int", "Long", "Double", "Float", "Byte", "Char", "Short").contains(castType.asInstanceOf[TypeConstruction].inType)


  override def getType: TypeConstruction = castType.asInstanceOf[TypedElement].getType
}

case class ArrayInitializer(expresions: Seq[IntermediateNode]) extends IntermediateNode
case class BinaryExpressionConstruction(firstPart: IntermediateNode, secondPart: IntermediateNode,
                                        operation: String, inExpression:Boolean) extends IntermediateNode

case class ClassObjectAccess(expression: IntermediateNode) extends IntermediateNode
case class InstanceOfConstruction(operand: IntermediateNode,
                                  mtype: IntermediateNode) extends IntermediateNode with TypedElement {
    override def getType: TypeConstruction = mtype.asInstanceOf[TypedElement].getType
}

case class QualifiedExpression(qualifier: IntermediateNode, identifier: IntermediateNode) extends IntermediateNode
object MethodCallExpression extends IntermediateNode {
  def build(reciever: IntermediateNode, methodName: String, args: IntermediateNode): MethodCallExpression = {
    val identifier = methodName match {
      case "this" => LiteralExpression(methodName)
      case _ => LiteralExpression(escapeKeyword(methodName))
    }
    MethodCallExpression(methodName, if (reciever != null)
      QualifiedExpression(reciever, identifier) else identifier, args, withSideEffects = false)
  }
}

case class MethodCallExpression(name: String, method: IntermediateNode,
                                args: IntermediateNode, withSideEffects: Boolean) extends IntermediateNode

case class ExpressionList(data: Seq[IntermediateNode]) extends IntermediateNode
case class ThisExpression(value: Option[IntermediateNode]) extends IntermediateNode
case class SuperExpression(value: Option[IntermediateNode]) extends IntermediateNode
case class LiteralExpression(literal: String) extends IntermediateNode
case class ParenthesizedExpression(value: Option[IntermediateNode]) extends IntermediateNode
case class FunctionalExpression(params: IntermediateNode, body: IntermediateNode) extends IntermediateNode
object NewExpression {
  def apply(mtype: IntermediateNode, arrayInitalizer: Seq[IntermediateNode],
            withArrayInitalizer: Boolean = true): NewExpression = {
    if (withArrayInitalizer)
      NewExpression(mtype, arrayInitalizer, Seq[IntermediateNode]())
    else
      NewExpression(mtype, Seq[IntermediateNode](), arrayInitalizer)
  }
}

case class NewExpression(mtype: IntermediateNode, arrayInitalizer: Seq[IntermediateNode],
                         arrayDimension: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = mtype.asInstanceOf[TypedElement].getType
}

case class AnonymousClassExpression(anonymousClass: IntermediateNode) extends IntermediateNode

case class PolyadicExpression(args: Seq[IntermediateNode], operation: String) extends IntermediateNode

case class PrefixExpression(operand: IntermediateNode, signType: String, canBeSimplified: Boolean) extends IntermediateNode
case class PostfixExpression(operand: IntermediateNode, signType: String, canBeSimplified: Boolean) extends IntermediateNode