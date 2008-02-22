package org.jetbrains.plugins.scala.lang.psi.impl.top

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 17:18:58
* To change this template use File | Settings | File Templates.
*/

class ScEarlyDefinitionImpl2(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "Early definition"
}