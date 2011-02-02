package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.scala.collection.mutable.HashMap
import api.toplevel.{ScTypedDefinition}
import com.intellij.psi.util.PsiTreeUtil
import types._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import api.toplevel.templates.ScTemplateBody
import api.statements.{ScDeclaredElementsHolder, ScTypeAlias}
import collection.Seq
import result.{TypeResult, Failure, Success, TypingContext}
import scala.Some
import com.intellij.psi.PsiClass
import controlFlow.impl.ScalaControlFlowBuilder
import controlFlow.Instruction
import api.base.patterns.{ScCaseClause, ScCaseClauses}
import api.toplevel.typedef.{ScTemplateDefinition, ScClass, ScTypeDefinition, ScObject}

/**
* @author ilyas
*/

class ScBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScBlock {
  override def toString: String = "BlockOfExpressions"
}