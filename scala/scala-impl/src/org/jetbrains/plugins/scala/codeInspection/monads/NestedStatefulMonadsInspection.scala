package org.jetbrains.plugins.scala.codeInspection.monads

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.codeInspection.monads.NestedStatefulMonadsInspection._
import org.jetbrains.plugins.scala.codeInspection.monads.StatefulMonads._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType

/**
 * @author Sergey Tolmachev (tolsi.ru@gmail.com)
 * @since 29.09.15
 */
object NestedStatefulMonadsInspection {
  private[monads] final val Annotation = "Nested stateful monads"
}

final class NestedStatefulMonadsInspection extends AbstractInspection(Annotation) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case call: ScMethodCall =>
      val project = call.getProject
      call.getType().getOrAny match {
        case outer@ParameterizedType(_, typeArgs)
          if isStatefulMonadType(outer, project) && typeArgs.exists(isStatefulMonadType(_, project)) =>
          holder.registerProblem(call, Annotation)
        case _ =>
      }
  }
}