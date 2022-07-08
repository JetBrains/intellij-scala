package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

class ScMatchImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScMatch with ScBegin {

  protected override def innerType: TypeResult = {
    expressions.flatMap(_.`type`().toOption) match {
      case Seq() => Failure("")
      case branchesTypes =>
        val branchesLub = branchesTypes.foldLeft(Nothing: ScType)(_.lub(_))
        Right(branchesLub)
    }
  }

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kMATCH

  override def toString: String = "MatchStatement"
}