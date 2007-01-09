package org.jetbrains.plugins.scala.lang.psi.visitors

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceExpression;

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.top._

abstract class ScalaElementVisitor extends PsiElementVisitor {
  def visit(element : PsiElement) : Unit

  override def visitElement(element : PsiElement) : Unit = {

    //todo: add types
    //type ScElementType = ScFile with ScPackaging with ScClassDefinition with ScObjectDefinition with ScTraitDefinition with Sc

    element match {
      case element : ScalaPsiElementImpl => visit(element)
      case _ => {}
    }
  }

  override def visitReferenceExpression(expression : PsiReferenceExpression) : Unit = visit(expression);
}

