package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createParameterFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitResolveResult.ResolverStateBuilder
import org.jetbrains.plugins.scala.lang.psi.implicits._
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeSystem, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, CachedWithRecursionGuard, ModCount}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait ResolvableReferenceExpression

object ResolvableReferenceExpression {

  implicit class Resolver(val reference: ScReferenceExpression) extends AnyVal {

    @CachedMappedWithRecursionGuard(reference, Array.empty, ModCount.getBlockModificationCount)
    def multiResolveImpl(incomplete: Boolean): Array[ResolveResult] =
      ReferenceExpressionResolver.resolve(reference, shapesOnly = false, incomplete)

    @CachedWithRecursionGuard[ScReferenceExpression](reference, Array.empty[ResolveResult], ModCount.getBlockModificationCount)
    def shapeResolveImpl: Array[ResolveResult] =
      ReferenceExpressionResolver.resolve(reference, shapesOnly = true, incomplete = false)
  }

  implicit class Ext(val ref: ScReferenceExpression) extends AnyVal {
    private implicit def manager: PsiManager = ref.getManager
    private implicit def typeSystem: TypeSystem = ref.typeSystem

    def isAssignmentOperator: Boolean = {
      val context = ref.getContext
      val refName = ref.refName
      (context.isInstanceOf[ScInfixExpr] || context.isInstanceOf[ScMethodCall]) &&
        refName.endsWith("=") &&
        !(refName.startsWith("=") || Seq("!=", "<=", ">=").contains(refName) || refName.exists(_.isLetterOrDigit))
    }

    def isUnaryOperator: Boolean = {
      ref.getContext match {
        case pref: ScPrefixExpr if pref.operation == ref => true
        case _ => false
      }
    }

    def rightAssoc: Boolean = ref.refName.endsWith(":")
  }
}
