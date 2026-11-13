package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, OpenTypeVariables, ScType, ScTypeExt}

class ScMatchImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScMatch with ScBegin {

  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    // Typed without an expected type in Scala 3, the branches leave the type variables that occur non-variantly
    // in their types open, they are solved when the types are joined, see OpenTypeVariables
    if (expectedType.isEmpty && this.joinedBranches.isDefined)
      return this.typeWithOpenTypeVariables.map(OpenTypeVariables.solve(_)(using Context(this)))

    val branchesTypes = clauses.flatMap { clause =>
      clause.expr.flatMap { body =>
        body.`type`(ExpectedTypePropagation.matchCaseClauseBody(this, clause, expectedType)).toOption
      }
    }

    branchesTypes match {
      case Seq() => Failure("")
      case branchesTypes =>
        val branchesLub = branchesTypes.foldLeft(Nothing: ScType)(_.lub(_)) // TODO Union type in Scala 3, SCL-23806
        Right(branchesLub)
    }
  }

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kMATCH

  override def toString: String = "MatchStatement"
}
