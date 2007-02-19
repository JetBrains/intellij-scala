package org.jetbrains.plugins.scala.lang.psi.impl.types

/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.resolve.references._

trait ScStableId extends ScPattern3

class ScStableIdImpl(node: ASTNode) extends ScSimpleExprImpl(node) with ScStableId with ScSimpleType {

  override def toString: String = "Stable Identifier"

  def getType(): PsiType = null

  override def getReference = {
    if (!getText.contains(".")) {
      new ScalaClassReference(this)
    } else {
      null       
    }
  }

  override def getName = getText

}