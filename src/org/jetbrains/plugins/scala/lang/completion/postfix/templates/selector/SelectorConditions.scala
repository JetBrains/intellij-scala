package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{Boolean, ValType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.language.implicitConversions

/**
 * @author Roman.Shein
 * @since 08.09.2015.
 */
object SelectorConditions {

  val BOOLEAN_EXPR = typedCondition(Boolean)

  val ANY_EXPR = new Condition[PsiElement] {
    override def value(t: PsiElement): Boolean = t.isInstanceOf[ScExpression]
  }

  val THROWABLE = isDescendantCondition("java.lang.Throwable")

  def isDescendantCondition(ancestorFqn: String) = new Condition[PsiElement]{
    override def value(t: PsiElement): Boolean = t match {
      case expr: ScExpression =>
        val project = t.getProject
        implicit val typeSystem = project.typeSystem
        val manager = ScalaPsiManager.instance(project)

        expr.getTypeIgnoreBaseType().toOption.flatMap {
          _.extractClass()
        }.zip {
          manager.getCachedClass(ancestorFqn, GlobalSearchScope.allScope(project), ClassCategory.ALL)
        }.exists {
          case (psiClass, base) =>
            ScEquivalenceUtil.areClassesEquivalent(psiClass, base) ||
              manager.cachedDeepIsInheritor(psiClass, base)
        }
      case _ => false
    }
  }

  def typedCondition(myType: ValType) = new Condition[PsiElement]{

    override def value(t: PsiElement): Boolean = t match {
      case expr: ScExpression =>
        expr.getTypeIgnoreBaseType().getOrAny.conforms(myType)(t.getProject.typeSystem)
      case _ => false
    }
  }

  class ExpandedCondition[T](source: Condition[T]) extends Condition[T] {
    override def value(t: T): Boolean = source.value(t)

    def ||(other: Condition[_ >: T]): Condition[T] = {
      def f(t: T) = value(t) || other.value(t)
      new Condition[T] {
        override def value(t: T): Boolean = f(t)
      }
    }

    def &&(other: Condition[_ >: T]): Condition[T] = {
      def f(t: T) = value(t) && other.value(t)
      new Condition[T] {
        override def value(t: T): Boolean = f(t)
      }
    }

    def a: Boolean = false
  }
}
