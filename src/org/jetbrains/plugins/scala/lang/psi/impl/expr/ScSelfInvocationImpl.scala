package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import lang.resolve.processor.MethodResolveProcessor
import api.statements.ScFunction
import api.toplevel.typedef.ScClass
import util.PsiTreeUtil
import types.Compatibility.Expression
import lang.resolve.StdKinds

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScSelfInvocationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSelfInvocation {
  override def toString: String = "SelfInvocation"

  def bind: Option[PsiElement] = {
    val psiClass = PsiTreeUtil.getParentOfType(this, classOf[PsiClass])
    if (psiClass == null) return None
    if (!psiClass.isInstanceOf[ScClass]) return None
    val clazz = psiClass.asInstanceOf[ScClass]
    val method = PsiTreeUtil.getParentOfType(this, classOf[ScFunction])
    if (method == null) return None
    val constructors: Array[PsiMethod] = clazz.getConstructors.filter(_ != method)
    if (args == None) return None
    val arguments = args.get
    val proc = new MethodResolveProcessor(this, "this", List(arguments.exprs.map(new Expression(_))), Seq.empty,
      StdKinds.methodsOnly, constructorResolve = true)
    for (constr <- constructors) {
      proc.execute(constr, ResolveState.initial)
    }
    val candidates = proc.candidates
    if (candidates.length == 1) {
      return Some(candidates(0).element)
    } else return None
  }
}