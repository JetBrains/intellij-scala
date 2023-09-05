package org.jetbrains.plugins.scala.conversion.ast

case class IfStatement(
  condition: Option[IntermediateNode],
  thenBranch: Option[IntermediateNode],
  elseBranch: Option[IntermediateNode]
) extends ExpressionsHolderNodeBase(condition.toSeq ++ thenBranch ++ elseBranch)

case class ReturnStatement(value: IntermediateNode)
  extends ExpressionsHolderNodeBase(value :: Nil) {

  /**
   * `return` keyword can be dropped in Scala if it's in tail position<br>
   * It's convenient to use this mutable field and shouldn't hurt
   */
  var canDropReturnKeyword: Boolean = false
}

case class ThrowStatement(value: IntermediateNode)
  extends ExpressionsHolderNodeBase(value :: Nil)

case class AssertStatement(condition: Option[IntermediateNode], description: Option[IntermediateNode])
  extends ExpressionsHolderNodeBase(condition.toSeq ++ description)

case class ImportStatement(importValue: IntermediateNode, onDemand: Boolean) extends IntermediateNode

case class ImportStatementList(imports: Seq[IntermediateNode]) extends IntermediateNode

case class PackageStatement(statement: IntermediateNode) extends IntermediateNode

case class JavaCodeReferenceStatement(
  qualifier: Option[IntermediateNode],
  parameterList: Option[IntermediateNode],
  name: Option[String]
) extends IntermediateNode

case class ForeachStatement(
  iterParamName: IntermediateNode,
  iteratedValue: Option[IntermediateNode],
  body: Option[IntermediateNode],
  isJavaCollection: Boolean
) extends ExpressionsHolderNodeBase(iteratedValue.toSeq)


case class ExpressionListStatement(exprs: Seq[IntermediateNode])
  extends ExpressionsHolderNodeBase(exprs)

case class SynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode])
  extends ExpressionsHolderNodeBase(body.toSeq)

case class SwitchLabelStatement(
  caseValues: Seq[IntermediateNode],
  guardExpression: Option[IntermediateNode],
  arrow: String,
  body: Option[IntermediateNode] = None
) extends ExpressionsHolderNodeBase(caseValues ++ body)

case class SwitchBlock(expression: Option[IntermediateNode], body: Option[IntermediateNode])
  extends ExpressionsHolderNodeBase(expression.toSeq ++ body)

case class TryCatchStatement(
  resourcesList: Seq[(String, IntermediateNode)],
  tryBlock: Option[BlockConstruction],
  catchStatements: Seq[(ParameterConstruction, IntermediateNode)],
  finallyStatements: Option[Seq[IntermediateNode]],
  arrow: String
) extends ExpressionsHolderNodeBase(tryBlock.toSeq ++ catchStatements.map(_._2) ++ finallyStatements.getOrElse(Nil))

object WhileStatement {
  val PRE_TEST_LOOP = 0
  val POST_TEST_LOOP = 1
}

case class WhileStatement(
  initialization: Option[IntermediateNode],
  condition: Option[IntermediateNode],
  body: Option[IntermediateNode],
  update: Option[IntermediateNode],
  whileType: Int
) extends ExpressionsHolderNodeBase(initialization.toSeq ++ condition ++ body ++ update)

case class NotSupported(iNode: Option[IntermediateNode], msg: String) extends ExpressionsHolderNodeBase(iNode.toSeq)

case class NameIdentifier(name: String) extends IntermediateNode
