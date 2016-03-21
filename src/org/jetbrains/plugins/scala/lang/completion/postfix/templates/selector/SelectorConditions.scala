package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.types.{Boolean => BooleanType, ScTypeExt, ValType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.language.implicitConversions

/**
 * @author Roman.Shein
 * @since 08.09.2015.
 */
object SelectorConditions {

  val BOOLEAN_EXPR = typedCondition(BooleanType)

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
          _.extractClass(project).map {
            psiClass =>
              val base = manager.getCachedClass(ancestorFqn, GlobalSearchScope.allScope(project), ClassCategory.ALL)
              (psiClass != null && base != null && ScEquivalenceUtil.areClassesEquivalent(psiClass, base)) ||
                manager.cachedDeepIsInheritor(psiClass, base)
          }
        }.getOrElse(false)
      case _ => false
    }
  }

  def typedCondition(myType: ValType) = new Condition[PsiElement]{

    override def value(t: PsiElement): Boolean = t match {
      case expr: ScExpression => expr.getTypeIgnoreBaseType().getOrAny == myType
      case _ => false
    }
  }

  class ExpandedCondition[T](source: Condition[T]) extends Condition[T] {
    override def value(t: T): Boolean = source.value(t)

    def ||(other: Condition[_ >: T]) = {
      def f(t: T) = value(t) || other.value(t)
      new Condition[T] {
        override def value(t: T) = f(t)
      }
    }

    def &&(other: Condition[_ >: T]) = {
      def f(t: T) = value(t) && other.value(t)
      new Condition[T] {
        override def value(t: T) = f(t)
      }
    }

    def a: Boolean = false
  }
}
