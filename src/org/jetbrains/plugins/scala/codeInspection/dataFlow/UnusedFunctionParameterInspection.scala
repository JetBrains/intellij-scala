package org.jetbrains.plugins.scala.codeInspection.dataFlow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl

/**
  * Created by ssdmitriev on 04.02.16.
  */
class UnusedFunctionParameterInspection
  extends AbstractInspection("UnusedFunctionParameter", "function parameter not used") {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case p: ScFunctionDefinition => findUnusedParameters(p)
  }
  def findUnusedParameters(f: ScFunctionDefinition) = {
    val visitor = new ScalaRecursiveElementVisitor(){
      val localF = f
      //Override also visitReferenceExpression! and visitTypeProjection!
      override def visitReference(ref: ScReferenceElement): Unit = {
        ""
      }
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        ""
      }
    }
    f.body.foreach(_.accept(visitor))
  }
}
