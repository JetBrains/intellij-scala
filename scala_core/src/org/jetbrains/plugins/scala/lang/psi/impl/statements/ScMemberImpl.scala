package org.jetbrains.plugins.scala.lang.psi.impl.statements

import java.util._
import com.intellij.lang._
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.icons._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
* @author ilyas
*/

//todo: remove this class, or show for what this exist.
abstract class ScMemberImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMember {

  override def getModifierList = findChildByClass(classOf[ScModifierList])
}