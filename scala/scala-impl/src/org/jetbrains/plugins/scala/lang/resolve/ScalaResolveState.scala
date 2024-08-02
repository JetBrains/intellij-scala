package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.util.Key
import com.intellij.psi.{ PsiClass, ResolveState }
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScExportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ResolveStateOps._

object ScalaResolveState extends ResolveStateOps {

  val empty: ResolveState = ResolveState.initial()

  override def resolveState: ResolveState = empty

  implicit class ResolveStateExt(override val resolveState: ResolveState) extends AnyVal with ResolveStateOps
}

trait ResolveStateOps extends Any {
  private def option[T](key: Key[T]): Option[T] = resolveState.get(key).toOption
  private def boolean(key: Key[TRUE.type]): Boolean = resolveState.get(key) == TRUE

  def resolveState: ResolveState

  def withSubstitutor(s: ScSubstitutor): ResolveState =
    resolveState.put(SUBSTITUTOR_KEY, s)

  def withImportsUsed(importsUsed: Set[ImportUsed]): ResolveState =
    resolveState.put(IMPORT_USED_KEY, importsUsed)

  def withFromType(fromType: ScType): ResolveState =
    resolveState.put(FROM_TYPE_KEY, fromType)

  def withFromType(fromType: Option[ScType]): ResolveState =
    resolveState.put(FROM_TYPE_KEY, fromType.orNull)

  def withCompoundOrSelfType(tp: ScType): ResolveState =
    resolveState.put(COMPOUND_OR_SELF_TYPE, tp)

  def withRename(name: String): ResolveState =
    resolveState.put(RENAMED_KEY, name)

  def withRename(name: Option[String]): ResolveState =
    name.fold(resolveState)(withRename)

  def withUnresolvedTypeParams(typeParams: Seq[TypeParameter]): ResolveState =
    resolveState.put(UNRESOLVED_TYPE_PARAMETERS_KEY, typeParams)

  def withNamedParam: ResolveState =
    resolveState.put(NAMED_PARAM_KEY, TRUE)

  def withForwardRef: ResolveState =
    resolveState.put(FORWARD_REFERENCE_KEY, TRUE)

  def withImplicitType(tp: ScType): ResolveState =
    resolveState.put(IMPLICIT_TYPE, tp)

  def withImplicitConversion(result: ScalaResolveResult): ResolveState =
    resolveState.put(IMPLICIT_CONVERSION, result)

  def withPrefixCompletion: ResolveState =
    resolveState.put(PREFIX_COMPLETION_KEY, TRUE)

  def withImplicitScopeObject(tpe: ScType): ResolveState =
    resolveState.put(IMPLICIT_SCOPE_OBJECT, tpe)

  def withMatchClauseSubstitutor(subst: ScSubstitutor): ResolveState =
    resolveState.put(MATCH_SUBSTITUTOR, subst)

  def withExtensionMethodMarker: ResolveState =
    resolveState.put(EXTENSION_METHOD, TRUE)

  def withExtensionContext(ext: ScExtension): ResolveState =
    resolveState.put(EXTENSION_CONTEXT, ext)

  def withStableTypeExpected: ResolveState =
    resolveState.put(STABLE_TYPE_EXPECTED, TRUE)

  def withIntersectedReturnType(tpe: ScType): ResolveState =
    resolveState.put(INTERSECTED_RETURN_TYPE, tpe)

  def withExportedIn(owner: ScExportsHolder): ResolveState =
    resolveState.put(EXPORTED_IN, owner)

  //
  // Getters
  //

  def substitutor: ScSubstitutor =
    option(SUBSTITUTOR_KEY).getOrElse(ScSubstitutor.empty)

  def substitutorWithThisType: ScSubstitutor =
    fromType.fold(substitutor)(substitutor.followUpdateThisType)

  def substitutorWithThisType(seenFromClass: PsiClass): ScSubstitutor =
    fromType.fold(substitutor)(substitutor.followUpdateThisType(_, seenFromClass))

  def fromType: Option[ScType] =
    option(FROM_TYPE_KEY)

  def compoundOrThisType: Option[ScType] =
    option(COMPOUND_OR_SELF_TYPE)

  def importsUsed: Set[ImportUsed] =
    option(IMPORT_USED_KEY).getOrElse(Set.empty)

  def renamed: Option[String] =
    option(RENAMED_KEY)

  def unresolvedTypeParams: Option[Seq[TypeParameter]] =
    option(UNRESOLVED_TYPE_PARAMETERS_KEY)

  def implicitType: Option[ScType] =
    option(IMPLICIT_TYPE)

  def implicitConversion: Option[ScalaResolveResult] =
    option(IMPLICIT_CONVERSION)

  def isNamedParameter: Boolean =
    boolean(NAMED_PARAM_KEY)

  def isForwardRef: Boolean =
    boolean(FORWARD_REFERENCE_KEY)

  def isPrefixCompletion: Boolean =
    boolean(PREFIX_COMPLETION_KEY)

  def implicitScopeObject: Option[ScType] =
    option(IMPLICIT_SCOPE_OBJECT)

  def matchClauseSubstitutor: ScSubstitutor =
    option(MATCH_SUBSTITUTOR).getOrElse(ScSubstitutor.empty)

  def isExtensionMethod: Boolean =
    boolean(EXTENSION_METHOD)

  def extensionContext: Option[ScExtension] =
    option(EXTENSION_CONTEXT)

  def stableTypeExpected: Boolean =
    boolean(STABLE_TYPE_EXPECTED)

  def intersectedReturnType: Option[ScType] =
    option(INTERSECTED_RETURN_TYPE)

  def exportedIn: Option[ScExportsHolder] =
    option(EXPORTED_IN)
}

/**
 * Most of these keys have are meant to store intermediate resolve data, that will eventually make it into
 * [[ScalaResolveResult]] fields. In case of confusion, it can be useful to refer to the corresponding field's
 * documentation (e.g. [[ResolveStateOps.EXPORTED_IN]] corresponds to [[ScalaResolveResult#exportedIn]]).
 */
private object ResolveStateOps {
  private object TRUE

  private val FROM_TYPE_KEY: Key[ScType] = Key.create("from.type.key")

  private val UNRESOLVED_TYPE_PARAMETERS_KEY: Key[Seq[TypeParameter]] = Key.create("unresolved.type.parameters.key")

  private val COMPOUND_OR_SELF_TYPE: Key[ScType] = Key.create("compound.or.this.type.key")

  private val FORWARD_REFERENCE_KEY: Key[TRUE.type] = Key.create("forward.reference.key")

  private val IMPLICIT_TYPE: Key[ScType] = Key.create("implicit.type")

  private val IMPLICIT_CONVERSION: Key[ScalaResolveResult] = Key.create("implicit.function")

  private val NAMED_PARAM_KEY: Key[TRUE.type] = Key.create("named.parameter.key")

  private val IMPORT_USED_KEY: Key[Set[ImportUsed]] = Key.create("scala.used.imports.key")

  private val SUBSTITUTOR_KEY: Key[ScSubstitutor] =  Key.create("scala substitutor key")

  private val PREFIX_COMPLETION_KEY: Key[TRUE.type] = Key.create("prefix.completion.key")

  //covers several cases like import alias, deprecated parameter names and default to "_root_" package
  private val RENAMED_KEY: Key[String] = Key.create("scala.renamed.key")

  //specifies the designator type of an object in an implicit scope, which provided this resolve result
  private val IMPLICIT_SCOPE_OBJECT: Key[ScType] = Key.create("scala.implicit.scope.object")

  private val MATCH_SUBSTITUTOR: Key[ScSubstitutor] = Key.create("scala.match.subsitutor")

  private val EXTENSION_METHOD: Key[TRUE.type] = Key.create("scala.extension.method.marker")

  private val EXTENSION_CONTEXT: Key[ScExtension] = Key.create("scala.extension.context")

  private val STABLE_TYPE_EXPECTED: Key[TRUE.type] = Key.create("scala.stable.type.expected")

  private val INTERSECTED_RETURN_TYPE: Key[ScType] = Key.create("scala.intersected.return.type")

  private val EXPORTED_IN: Key[ScExportsHolder] = Key.create("scala.exported.in")
}
