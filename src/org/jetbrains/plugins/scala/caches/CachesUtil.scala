package org.jetbrains.plugins.scala
package caches


import com.intellij.openapi.util.{Computable, Key, RecursionGuard, RecursionManager}
import com.intellij.psi._
import com.intellij.psi.util.{CachedValue, CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import com.intellij.util.containers.ConcurrentHashMap
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer
import scala.util.control.ControlThrowable

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */
object CachesUtil {
  //Map cache keys
  type MappedKey[Data, Result] = Key[CachedValue[ConcurrentHashMap[Data, Result]]]
  val TYPE_AFTER_IMPLICIT_KEY: MappedKey[(Boolean, Boolean, Option[ScType], Boolean, Boolean), ExpressionTypeResult] =
    Key.create("type.after.implicit.key")
  val TYPE_OF_SPECIAL_EXPR_AFTER_IMPLICIT_KEY: MappedKey[(ScType, Option[ScType]), (TypeResult[ScType], collection.Set[ImportUsed])] =
    Key.create("type.of.special.expr.after.implicit.key")
  val TYPE_WITHOUT_IMPLICITS: MappedKey[(Boolean, Boolean), TypeResult[ScType]] =
    Key.create("type.without.implicits.key")
  val NON_VALUE_TYPE_KEY: MappedKey[(Boolean, Boolean), TypeResult[ScType]] = Key.create("non.value.type.key")
  val EXPECTED_TYPES_KEY: MappedKey[Boolean, Array[(ScType, Option[ScTypeElement])]] = Key.create("expected.types.key")
  val SMART_EXPECTED_TYPE: MappedKey[Boolean, Option[ScType]] = Key.create("smart.expected.type")
  val IMPLICIT_MAP1_KEY: MappedKey[(Option[ScType], Boolean, Option[ScType]), Seq[ImplicitResolveResult]] =
    Key.create("implicit.map1.key")
  val IMPLICIT_MAP2_KEY: MappedKey[(Option[ScType], Boolean, Seq[ScType], Option[ScType]), Seq[ImplicitResolveResult]] =
    Key.create("implicit.map2.key")
  val IMPLICIT_SIMPLE_MAP_KEY: MappedKey[(Boolean, Option[ScType]), ArrayBuffer[ScImplicitlyConvertible.ImplicitMapResult]] =
    Key.create("implicit.simple.map.key")
  val RESOLVE_KEY: MappedKey[Boolean, Array[ResolveResult]] =
    Key.create("resolve.key")
  val EXPRESSION_APPLY_SHAPE_RESOLVE_KEY: MappedKey[(ScType, Seq[ScExpression], Option[MethodInvocation]), Array[ScalaResolveResult]] =
    Key.create("expression.apply.shape.resolve.key")

  //keys for cachedValue
  val REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.shape.resolve.constr.key")
  val REF_ELEMENT_RESOLVE_CONSTR_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.resolve.constr.key")
  val NO_CONSTRUCTOR_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] = Key.create("no.constructor.resolve.key")
  val OBJECT_SYNTHETIC_MEMBERS_KEY: Key[CachedValue[Seq[PsiMethod]]] = Key.create("object.synthetic.members.key")
  val SYNTHETIC_MEMBERS_KEY: Key[CachedValue[Seq[PsiMethod]]] = Key.create("stynthetic.members.key")
  val DESUGARIZED_EXPR_KEY: Key[CachedValue[Option[ScExpression]]] = Key.create("desugarized.expr.key")
  val STRING_CONTEXT_EXPANDED_EXPR_KEY: Key[CachedValue[Option[ScExpression]]] = Key.create("string.context.expanded.expr.key")
  val TYPE_ELEMENT_TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.element.type.key")
  val SIMPLE_TYPE_ELEMENT_TYPE_NO_CONSTRUCTOR_KEY: Key[CachedValue[TypeResult[ScType]]] =
    Key.create("simple.type.element.type.no.constructor.key")
  val NON_VALUE_TYPE_ELEMENT_TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.element.type.key")
  val IS_FUNCTION_INHERITOR_KEY: Key[CachedValue[Boolean]] = Key.create("is.function1.inheritor.key")
  val CONSTRUCTOR_TYPE_PARAMETERS_KEY: Key[CachedValue[Option[ScTypeParamClause]]] =
    Key.create("constructor.type.parameters.key")
  val REF_ELEMENT_SHAPE_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.shape.resolve.key")
  val REF_EXPRESSION_SHAPE_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.expression.shape.resolve.key")
  val REF_EXPRESSION_NON_VALUE_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.expression.non.value.resolve.key")
  val IS_SCRIPT_FILE_KEY: Key[CachedValue[Boolean]] = Key.create("is.script.file.key")
  val FUNCTION_EFFECTIVE_PARAMETER_CLAUSE_KEY: Key[CachedValue[Seq[ScParameterClause]]] =
    Key.create("function.effective.parameter.clause.key")
  val TYPE_WITHOUT_IMPLICITS_WITHOUT_UNDERSCORE: Key[CachedValue[TypeResult[ScType]]] =
    Key.create("type.without.implicits.without.underscore.key")
  val ALIASED_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("alised.type.key")
  val SCRIPT_KEY: Key[CachedValue[java.lang.Boolean]] = Key.create("is.script.key")
  val SCALA_PREDEFINED_KEY: Key[CachedValue[java.lang.Boolean]] = Key.create("scala.predefined.key")
  val EXPR_TYPE_KEY: Key[CachedValue[ScType]] = Key.create("expr.type.key")
  val TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.element.type.key")
  val PSI_RETURN_TYPE_KEY: Key[CachedValue[PsiType]] = Key.create("psi.return.type.key")
  val SUPER_TYPES_KEY: Key[CachedValue[List[ScType]]] = Key.create("super.types.key")
  val EXTENDS_BLOCK_SUPER_TYPES_KEY: Key[CachedValue[List[ScType]]] = Key.create("extends.block.super.types.key")
  val EXTENDS_BLOCK_SUPERS_KEY: Key[CachedValue[Seq[PsiClass]]] = Key.create("extends.block.supers.key")
  val LINEARIZATION_KEY: Key[CachedValue[Seq[ScType]]] = Key.create("linearization.key")
  val FAKE_CLASS_COMPANION: Key[CachedValue[Option[ScObject]]] = Key.create("fake.class.companion.key")
  val EFFECTIVE_PARAMETER_CLAUSE: Key[CachedValue[Seq[ScParameterClause]]] =
    Key.create("effective.parameter.clause.key")
  val PATTERN_EXPECTED_TYPE: Key[CachedValue[Option[ScType]]] = Key.create("pattern.expected.type.key")
  val PROJECTION_TYPE_ACTUAL_INNER: Key[CachedValue[ConcurrentHashMap[ScType, Option[(PsiNamedElement, ScSubstitutor)]]]] =
    Key.create("projection.type.actual.inner.key")

  //keys for getUserData
  val EXPRESSION_TYPING_KEY: Key[java.lang.Boolean] = Key.create("expression.typing.key")
  val IMPLICIT_TYPE: Key[ScType] = Key.create("implicit.type")
  val IMPLICIT_FUNCTION: Key[PsiNamedElement] = Key.create("implicit.function")
  val NAMED_PARAM_KEY: Key[java.lang.Boolean] = Key.create("named.key")
  val PACKAGE_OBJECT_KEY: Key[(ScTypeDefinition, java.lang.Long)] = Key.create("package.object.key")

  def getWithRecursionPreventingWithRollback[Dom <: PsiElement, Result](e: Dom, key: Key[CachedValue[Result]],
                                                        provider: => MyProviderTrait[Dom, Result],
                                                        defaultValue: => Result): Result = {
    var computed: CachedValue[Result] = e.getUserData(key)
    if (computed == null) {
      val manager = CachedValuesManager.getManager(e.getProject)
      computed = manager.createCachedValue(new CachedValueProvider[Result] {
        def compute(): CachedValueProvider.Result[Result] = {
          val guard = getRecursionGuard(key.toString)
          if (guard.currentStack().contains(e)) {
            if (ScPackageImpl.isPackageObjectProcessing) {
              throw new ScPackageImpl.DoNotProcessPackageObjectException
            }
            val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
            if (fun == null || fun.isProbablyRecursive) {
              provider.getDependencyItem match {
                case Some(item) =>
                  return new CachedValueProvider.Result(defaultValue, item)
                case _ =>
                  return new CachedValueProvider.Result(defaultValue)
              }
            } else {
              fun.setProbablyRecursive(true)
              throw new ProbablyRecursionException(e, (), key, Set(fun))
            }
          }
          guard.doPreventingRecursion(e, false /* todo: true? */, new Computable[CachedValueProvider.Result[Result]] {
            def compute(): CachedValueProvider.Result[Result] = {
              try {
                provider.compute()
              }
              catch {
                case ProbablyRecursionException(`e`, (), k, set) if k == key =>
                  try {
                    provider.compute()
                  }
                  finally set.foreach(_.setProbablyRecursive(false))
                case t@ProbablyRecursionException(ee, data, k, set) if k == key =>
                  val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
                  if (fun == null || fun.isProbablyRecursive) throw t
                  else {
                    fun.setProbablyRecursive(true)
                    throw ProbablyRecursionException(ee, data, k, set + fun)
                  }
              }
            }
          }) match {
            case null =>
              provider.getDependencyItem match {
                case Some(item) => new CachedValueProvider.Result(defaultValue, item)
                case _ => new CachedValueProvider.Result(defaultValue)
              }
            case notNull => notNull
          }
        }
      }, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  def get[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]): T = {
    var computed: CachedValue[T] = e.getUserData(key)
    if (computed == null) {
      val manager = CachedValuesManager.getManager(e.getProject)
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  trait MyProviderTrait[Dom, T] extends CachedValueProvider[T] {
    private[CachesUtil] def getDependencyItem: Option[Object]
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T)(dependencyItem: Object) extends MyProviderTrait[Dom, T] {
    private[CachesUtil] def getDependencyItem: Option[Object] = Some(dependencyItem)

    def compute() = new CachedValueProvider.Result(builder(e), dependencyItem)
  }

  class MyOptionalProvider[Dom, T](e: Dom, builder: Dom => T)(dependencyItem: Option[Object]) extends MyProviderTrait[Dom, T] {
    private[CachesUtil] def getDependencyItem: Option[Object] = dependencyItem

    def compute() = {
      dependencyItem match {
        case Some(depItem) =>
          new CachedValueProvider.Result(builder(e), depItem)
        case _ => new CachedValueProvider.Result(builder(e))
      }
    }
  }

  private val guards: ConcurrentHashMap[String, RecursionGuard] = new ConcurrentHashMap()
  private def getRecursionGuard(id: String): RecursionGuard = {
    val guard = guards.get(id)
    if (guard == null) {
      val result = RecursionManager.createGuard(id)
      guards.put(id, result)
      result
    } else guard
  }

  def getMappedWithRecursionPreventingWithRollback[Dom <: PsiElement, Data, Result](e: Dom, data: Data,
                                                                        key: Key[CachedValue[ConcurrentHashMap[Data, Result]]],
                                                                        builder: (Dom, Data) => Result,
                                                                        defaultValue: => Result,
                                                                        dependencyItem: Object): Result = {
    var computed: CachedValue[ConcurrentHashMap[Data, Result]] = e.getUserData(key)
    if (computed == null) {
      val manager = CachedValuesManager.getManager(e.getProject)
      computed = manager.createCachedValue(new CachedValueProvider[ConcurrentHashMap[Data, Result]] {
        def compute(): CachedValueProvider.Result[ConcurrentHashMap[Data, Result]] = {
          new CachedValueProvider.Result(new ConcurrentHashMap[Data, Result](), dependencyItem)
        }
      }, false)
      e.putUserData(key, computed)
    }
    val map = computed.getValue
    var result = map.get(data)
    if (result == null) {
      var isCache = true
      result = {
        val guard = getRecursionGuard(key.toString)
        if (guard.currentStack().contains((e, data))) {
          if (ScPackageImpl.isPackageObjectProcessing) {
            throw new ScPackageImpl.DoNotProcessPackageObjectException
          }
          val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
          if (fun == null || fun.isProbablyRecursive) {
            isCache = false
            defaultValue
          } else {
            fun.setProbablyRecursive(true)
            throw new ProbablyRecursionException(e, data, key, Set(fun))
          }
        } else {
          guard.doPreventingRecursion((e, data), false, new Computable[Result] {
            def compute(): Result = {
              try {
                builder(e, data)
              }
              catch {
                case ProbablyRecursionException(`e`, `data`, k, set) if k == key =>
                  try {
                    builder(e, data)
                  } finally set.foreach(_.setProbablyRecursive(false))
                case t@ProbablyRecursionException(ee, innerData, k, set) if k == key =>
                  val fun = PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction])
                  if (fun == null || fun.isProbablyRecursive) throw t
                  else {
                    fun.setProbablyRecursive(true)
                    throw ProbablyRecursionException(ee, innerData, k, set + fun)
                  }
              }
            }
          }) match {
            case null => defaultValue
            case notNull => notNull
          }
        }
      }
      if (isCache) {
        map.put(data, result)
      }
    }
    result
  }
  
  private case class ProbablyRecursionException[Dom <: PsiElement, Data, T](elem: Dom,
                                                                            data: Data,
                                                                            key: Key[T],
                                                                            set: Set[ScFunction]) extends ControlThrowable
}