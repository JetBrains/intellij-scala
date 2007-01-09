package org.jetbrains.plugins.scala.lang.psi.visitors

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.01.2007
 * Time: 13:10:40
 */
import org.jetbrains.plugins.scala.lang.psi.visitors._
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.top._

class ScalaContainerVisitor extends ScalaElementVisitor {
  override def visit (element : PsiElement) : Unit = {
    type TopLevelElementType = ScFile with ScPackaging with ScClassDefinition with ScObjectDefinition with ScTraitDefinition

    element match {
      case topLevelElement : TopLevelElementType => visitContainer(element)

      case _ => element.acceptChildren(this);
    }
  }

  def visitContainer (element : PsiElement) : Unit = {}
}