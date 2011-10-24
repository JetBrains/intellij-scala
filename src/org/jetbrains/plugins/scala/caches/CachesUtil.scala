package org.jetbrains.plugins.scala
package caches


import com.intellij.psi.util.{CachedValueProvider, CachedValue}
import lang.psi.types.result.TypeResult
import com.intellij.psi._
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import lang.psi.api.expr.ScExpression.ExpressionTypeResult
import lang.psi.api.base.types.ScTypeElement
import com.intellij.openapi.util.{Computable, RecursionManager, RecursionGuard, Key}
import util.CachedValueProvider.Result
import lang.psi.api.expr.ScExpression
import lang.psi.api.toplevel.imports.usages.ImportUsed
import collection.mutable.{ArrayBuffer, Map, HashMap}
import lang.resolve.ScalaResolveResult
import lang.psi.types.{ScUndefinedSubstitutor, ScSubstitutor, Signature, ScType}
import lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause, ScParameterClause}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */
object CachesUtil {
  //keys for cachedValue
  val REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.shape.resolve.constr.key")
  val REF_ELEMENT_RESOLVE_CONSTR_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.resolve.constr.key")
  val IMPLICIT_SIMPLE_MAP_KEY: Key[CachedValue[ArrayBuffer[(ScalaResolveResult, ScType,
    ScType, ScSubstitutor, ScUndefinedSubstitutor)]]] =
    Key.create("implicit.simple.map.key")
  val NO_CONSTRUCTOR_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] = Key.create("no.constructor.resolve.key")
  val IMPLICIT_MAP1_KEY: Key[CachedValue[Seq[(ScType, PsiNamedElement, collection.Set[ImportUsed])]]] =
    Key.create("implicit.map1.key")
  val IMPLICIT_MAP2_KEY: Key[CachedValue[Seq[(ScType, PsiNamedElement, collection.Set[ImportUsed])]]] =
    Key.create("implicit.map2.key")
  val OBJECT_SYNTHETIC_MEMBERS_KEY: Key[CachedValue[Seq[PsiMethod]]] = Key.create("object.synthetic.members.key")
  val SYNTHETIC_MEMBERS_KEY: Key[CachedValue[Seq[PsiMethod]]] = Key.create("stynthetic.members.key")
  val DESUGARIZED_EXPR_KEY: Key[CachedValue[Option[ScExpression]]] = Key.create("desugarized.expr.key")
  val TYPE_ELEMENT_TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.element.type.key")
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
  val NON_VALUE_TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("non.value.type.key")
  val EXPECTED_TYPES_KEY: Key[CachedValue[Array[(ScType, Option[ScTypeElement])]]] = Key.create("expected.types.key")
  val SMART_EXPECTED_TYPE: Key[CachedValue[Option[ScType]]] = Key.create("smart.expected.type")
  val TYPE_AFTER_IMPLICIT_KEY: Key[CachedValue[ExpressionTypeResult]] = Key.create("type.after.implicit.key")
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
  val SIGNATURES_MAP_KEY: Key[CachedValue[HashMap[Signature, ScType]]] = Key.create("signatures.map.key")
  val LINEARIZATION_KEY: Key[CachedValue[Seq[ScType]]] = Key.create("linearization.key")
  val FAKE_CLASS_COMPANION: Key[CachedValue[Option[ScObject]]] = Key.create("fake.class.companion.key")
  val EFFECTIVE_PARAMETER_CLAUSE: Key[CachedValue[Seq[ScParameterClause]]] =
    Key.create("effective.parameter.clause.key")
  val PATTERN_EXPECTED_TYPE: Key[CachedValue[Option[ScType]]] = Key.create("pattern.expected.type.key")
  val TYPE_WITHOUT_IMPLICITS: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.without.implicits.key")

  //keys for getUserData
  val EXPRESSION_TYPING_KEY: Key[java.lang.Boolean] = Key.create("expression.typing.key")
  val IMPLICIT_TYPE: Key[ScType] = Key.create("implicit.type")
  val IMPLICIT_FUNCTION: Key[PsiNamedElement] = Key.create("implicit.function")
  val NAMED_PARAM_KEY: Key[java.lang.Boolean] = Key.create("named.key")
  val PACKAGE_OBJECT_KEY: Key[(ScTypeDefinition, java.lang.Long)] = Key.create("package.object.key")

  def getWithRecurisionPreventing[Dom <: PsiElement, T](e: Dom, key: Key[CachedValue[T]],
                                                        provider: MyProvider[Dom, T],
                                                        defaultValue: => T): T = {
    var computed: CachedValue[T] = e.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(e.getProject).getCachedValuesManager
      computed = manager.createCachedValue(new CachedValueProvider[T] {
        def compute(): Result[T] = {
          val guard = getRecursionGuard(key.toString)
          if (guard.currentStack().contains(e)) {
            return new CachedValueProvider.Result(defaultValue, provider.getDependencyItem)
          }
          guard.doPreventingRecursion(e, false /* todo: true? */, new Computable[Result[T]] {
            def compute(): Result[T] = provider.compute()
          }) match {
            case null => new CachedValueProvider.Result(defaultValue, provider.getDependencyItem)
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
      val manager = PsiManager.getInstance(e.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      e.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[Dom, T](e: Dom, builder: Dom => T)(dependencyItem: Object) extends CachedValueProvider[T] {
    def getDependencyItem: Object = dependencyItem

    def compute() = new CachedValueProvider.Result(builder(e), dependencyItem)
  }

  private val guards: Map[String, RecursionGuard] = Map()
  private def getRecursionGuard(id: String): RecursionGuard = {
    guards.getOrElseUpdate(id, RecursionManager.createGuard(id))
  }
}