package org.jetbrains.plugins.scala.conversion.ast

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class IfStatement(condition: Option[IntermediateNode],
                       thenBranch: Option[IntermediateNode],
                       elseBranch: Option[IntermediateNode]) extends IntermediateNode

case class ReturnStatement(value: IntermediateNode) extends IntermediateNode

case class ThrowStatement(value: IntermediateNode) extends IntermediateNode

case class AssertStatement(condition: Option[IntermediateNode], description: Option[IntermediateNode]) extends IntermediateNode

case class ImportStatement(importValue: IntermediateNode, onDemand: Boolean) extends IntermediateNode

case class ImportStatementList(imports: Seq[IntermediateNode]) extends IntermediateNode

case class PackageStatement(statement: IntermediateNode) extends IntermediateNode

case class JavaCodeReferenceStatement(qualifier: Option[IntermediateNode],
                                      parameterList: Option[IntermediateNode],
                                      name: Option[String]) extends IntermediateNode

case class ForeachStatement(iterParamName: IntermediateNode, iteratedValue: Option[IntermediateNode],
                            body: Option[IntermediateNode], isJavaCollection: Boolean) extends IntermediateNode

case class ExpressionListStatement(exprs: Seq[IntermediateNode]) extends IntermediateNode

case class SynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode]) extends IntermediateNode

case class SwitchLabelStatement(caseValue: Option[IntermediateNode], arrow: String) extends IntermediateNode

case class SwitchStatemtnt(expession: Option[IntermediateNode], body: Option[IntermediateNode]) extends IntermediateNode

case class TryCatchStatement(resourcesList: Seq[(String, IntermediateNode)], tryBlock: Seq[IntermediateNode],
                             catchStatements: Seq[(IntermediateNode, IntermediateNode)],
                             finallyStatements: Option[Seq[IntermediateNode]], arrow: String) extends IntermediateNode
object WhileStatement {
  val PRE_TEST_LOOP = 0
  val POST_TEST_LOOP = 1
}

case class WhileStatement(initialization: Option[IntermediateNode], condition: Option[IntermediateNode],
                          body: Option[IntermediateNode], update: Option[IntermediateNode], whileType: Int)
  extends IntermediateNode

case class NotSupported(iNode: Option[IntermediateNode], msg: String) extends IntermediateNode

case class NameIdentifier(name: String) extends IntermediateNode