package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.impl.{PositionUtil, DebuggerContextImpl}
import com.intellij.debugger.ui.tree.{FieldDescriptor, LocalVariableDescriptor, NodeDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.annotation.tailrec

/**
 * @author Nikolay.Tropin
 */
class ScalaSourcePositionProvider extends SourcePositionProvider {
  override def computeSourcePosition(descriptor: NodeDescriptor,
                                     project: Project,
                                     context: DebuggerContextImpl,
                                     nearest: Boolean): SourcePosition = {

    val contextElement = PositionUtil.getContextElement(context)
    val isScala = contextElement match {
      case _: ScalaPsiElement => true
      case _ => false
    }
    if (!isScala) return null

    descriptor match {
      case _: FieldDescriptor | _: LocalVariableDescriptor =>
      case _ => return null
    }

    val name = descriptor.getName
    resolveReferenceWithName(name, contextElement) match {
      case bp: ScBindingPattern => SourcePosition.createFromElement(bp)
      case _ => null
    }
  }

  @tailrec
  private def resolveReferenceWithName(name: String, context: PsiElement): PsiElement = {
    if (!ScalaNamesUtil.isIdentifier(name)) return null

    val ref = ScalaPsiElementFactory.createExpressionWithContextFromText(name, context, context).asInstanceOf[ScReferenceExpression]

    ref.resolve() match {
      case null if name.contains("$") =>
        val fixedName = name.substring(0, name.indexOf('$'))
        resolveReferenceWithName(fixedName, context)
      case elem => elem
    }
  }
}
