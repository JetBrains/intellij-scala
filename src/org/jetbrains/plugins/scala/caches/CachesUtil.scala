package org.jetbrains.plugins.scala
package caches


import com.intellij.openapi.util.Key
import com.intellij.psi.util.{CachedValueProvider, CachedValue}
import lang.psi.types.{Signature, ScType}
import lang.psi.types.result.TypeResult
import collection.mutable.HashMap
import com.intellij.psi._
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import lang.psi.api.statements.params.ScParameterClause
import lang.psi.api.expr.ScExpression.ExpressionTypeResult
import lang.psi.api.base.types.ScTypeElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */
//todo: copy from TypeDefinitionMembers, rewrite or remove duplicates
object CachesUtil {
  val REF_ELEMENT_SHAPE_RESOLVE_CONSTR_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.shape.resolve.constr.key")
  val REF_ELEMENT_SHAPE_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.element.shape.resolve.key")
  val REF_EXPRESSION_SHAPE_RESOLVE_KEY: Key[CachedValue[Array[ResolveResult]]] =
    Key.create("ref.expression.shape.resolve.key")
  val IS_SCRIPT_FILE_KEY: Key[CachedValue[Boolean]] = Key.create("is.script.file.key")
  val FUNCTION_EFFECTIVE_PARAMETER_CLAUSE_KEY: Key[CachedValue[Seq[ScParameterClause]]] =
    Key.create("function.effective.parameter.clause.key")
  val NON_VALUE_TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("non.value.type.key")
  val EXPECTED_TYPES_KEY: Key[CachedValue[Array[(ScType, Option[ScTypeElement])]]] = Key.create("expected.types.key")
  val TYPE_AFTER_IMPLICIT_KEY: Key[CachedValue[ExpressionTypeResult]] = Key.create("type.after.implicit.key")
  val TYPE_WITHOUT_IMPLICITS_WITHOUT_UNDERSCORE: Key[CachedValue[TypeResult[ScType]]] =
    Key.create("type.without.implicits.without.underscore.key")
  val ALIASED_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("alised.type.key")
  val SCRIPT_KEY: Key[CachedValue[java.lang.Boolean]] = Key.create("is.script.key")
  val EXPR_TYPE_KEY: Key[CachedValue[ScType]] = Key.create("expr.type.key")
  val TYPE_KEY: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.element.type.key")
  val PSI_RETURN_TYPE_KEY: Key[CachedValue[PsiType]] = Key.create("psi.return.type.key")
  val SUPER_TYPES_KEY: Key[CachedValue[List[ScType]]] = Key.create("super.types.key")
  val EXTENDS_BLOCK_SUPER_TYPES_KEY: Key[CachedValue[List[ScType]]] = Key.create("extends.block.super.types.key")
  val SIGNATURES_MAP_KEY: Key[CachedValue[HashMap[Signature, ScType]]] = Key.create("signatures.map.key")
  val LINEARIZATION_KEY: Key[CachedValue[Seq[ScType]]] = Key.create("linearization.key")
  val IMPLICIT_PARAM_TYPES_KEY: Key[Map[Thread, List[ScType]]] = Key.create("implicit.param.types.key")
  val CYCLIC_HELPER_KEY: Key[Map[Thread, List[PsiNamedElement]]] = Key.create("cyclic.helper.key")
  val EXPRESSION_TYPING_KEY: Key[java.lang.Boolean] = Key.create("expression.typing.key")
  val IMPLICIT_TYPE: Key[ScType] = Key.create("implicit.type")
  val IMPLICIT_FUNCTION: Key[PsiNamedElement] = Key.create("implicit.function")
  val NAMED_PARAM_KEY: Key[java.lang.Boolean] = Key.create("named.key")
  val PACKAGE_OBJECT_KEY: Key[(ScTypeDefinition, java.lang.Long)] = Key.create("package.object.key")
  val FAKE_CLASS_COMPANION: Key[CachedValue[Option[ScObject]]] = Key.create("fake.class.companion.key")
  val EFFECTIVE_PARAMETER_CLAUSE: Key[CachedValue[Seq[ScParameterClause]]] =
    Key.create("effective.parameter.clause.key")
  val PATTERN_EXPECTED_TYPE: Key[CachedValue[Option[ScType]]] = Key.create("pattern.expected.type.key")
  val TYPE_WITHOUT_IMPLICITS: Key[CachedValue[TypeResult[ScType]]] = Key.create("type.without.implicits.key")

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
    def compute() = new CachedValueProvider.Result(builder(e), dependencyItem)
  }
}