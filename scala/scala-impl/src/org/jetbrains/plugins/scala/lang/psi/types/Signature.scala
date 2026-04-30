package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiModifierListOwner, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiModifierListOwnerExt}
import org.jetbrains.plugins.scala.lang.psi.ScExportsHolder
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.types.Signature.ExportedSigInfo
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

/**
 * [[Signature]] is a semantic representation of a class member as seen from an inheritor.
 * It describes what member participates in resolve and override checks
 * (for example, declaration origin, visible identity, type adaptation, etc...; see member section below for details).
 *
 * == Concrete implementations ==
 *   - [[TermSignature]] for term members (methods/vals/vars/objects and synthetic property methods)
 *   - [[TypeSignature]] for type members (type aliases and nested type definitions)
 *
 * == Entity lifecycle in resolve/type-member processing ==
 *   - collection stage:<br>
 *     [[impl.toplevel.typedef.SignatureProcessor]] creates [[Signature]] instances from Scala/Java members (including exported members)
 *   - merge/override stage:<br>
 *     [[equiv]] and [[equivHashCode]] define semantic identity used while merging inherited and local members inside [[impl.toplevel.typedef.MixinNodes]]
 *   - processing stage:<br>
 *     [[impl.toplevel.typedef.TypeDefinitionMembers]] iterates resulting maps and executes resolve processors using data carried by [[Signature]]
 *   - cache stage:<br>
 *     maps keyed by these signatures are cached by [[impl.ScalaPsiManager]]
 *
 * @see [[impl.toplevel.typedef.MixinNodes.Node]]:<br>
 *      inheritance-graph wrapper around a signature; see Node ScalaDoc for details.
 */
trait Signature {

  /**
   * Declaration element this semantic member view is built from.
   *
   * This is intentionally `PsiNamedElement` (not `PsiMember`) because signatures are built for a broader set of declarations:
   *   - Scala members (`ScMember` implementations)
   *   - Java declarations such as `PsiMethod` and `PsiField`
   *   - named Scala declarations that are not always `PsiMember` (for example, class parameters used as members, val/var fields)
   *
   * The common contract required by signature processing is: stable name + PSI identity.
   */
  def namedElement: PsiNamedElement

  /**
   * Implicit typing context anchored at [[namedElement]].<br>
   * It is used by signature type-equivalence checks to evaluate types in the
   * correct declaration/use-site context.
   *
   * Example:
   * {{{
   * tp2.equiv(tp1, constraints, falseUndef)
   * }}}
   */
  protected implicit def thisContext: Context = Context(namedElement)

  /**
   * Visible identity used for member lookup and merge.
   *
   * Sometimes one element generates several signatures with different identities.
   * Typical examples:
   *   - setter method synthesized for a `var`
   *   - exported member renamed by an `export` clause (see [[renamed]])
   */
  def name: String

  /**
   * Visible use-site name override (import alias or export rename).
   * It does not change [[namedElement]]; [[name]] is derived from this value when present.
   *
   * Example:
   * {{{
   * import java.util.{List => JList}
   * // renamed = Some("JList"), namedElement.name == "List"
   * }}}
   */
  def renamed: Option[String]

  /** Type adaptation from declaration owner context to use-site context */
  def substitutor: ScSubstitutor

  /**
   * Whether this member is abstract in signature semantics.<br>
   * This may differ from raw declaration status when signature processing adapts it for inheritance/resolve behavior.<br>
   *
   * Example:
   * {{{
   * export foo.bar
   * // exported signature is treated as concrete
   * }}}
   */
  def isAbstract: Boolean

  /** Whether the member participates in implicit lookup */
  def isImplicit: Boolean

  /** Whether the signature is synthetic (not declared directly in the source) */
  def isSynthetic: Boolean

  /** Whether the signature corresponds to an extension-method entry point */
  def isExtensionMethod: Boolean = false

  /**
   * Export-origin metadata for signatures produced from Scala 3 `export`.<br>
   * Contains export owner and optional qualifier type (`exportedFrom`).
   *
   * Example:
   * {{{
   * class C {
   *   export x.y
   * }
   * // exportedInfo.nonEmpty for signature y
   * }}}
   */
  def exportedInfo: Option[ExportedSigInfo]

  /**
   * Template definition that owns the export clause which produced this signature.
   * Returns `None` for non-exported signatures.
   *
   * Example:
   * {{{
   * class C {
   *   export x.y
   * }
   * // exportedInCls == Some(C)
   * }}}
   */
  def exportedInCls: Option[ScTemplateDefinition] =
    exportedInfo.flatMap(
      _.exportedIn.getContext.getContext.asOptionOf[ScTemplateDefinition]
    )

  /**
   * Signature-level privacy used by inheritance merge.
   * Private signatures are tracked per owner and do not override across classes.
   * Non-member constructor parameters are treated as private.
   *
   * Example:
   * {{{
   * class C(x: Int)
   * // x contributes a private signature
   * }}}
   */
  def isPrivate: Boolean = namedElement match {
    case param: ScClassParameter if !param.isClassMember => true
    case inNameContext(s: ScModifierListOwner) =>
      s.getModifierList.accessModifier match {
        case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
        case _                         => false
      }
    case _: ScNamedElement       => false
    case n: PsiModifierListOwner => n.hasModifierPropertyScala("private")
    case _                       => false
  }

  /**
   * Semantic signature equivalence used for inherited-member merge/override keying.
   * It is intentionally not the same contract as `equals`.
   */
  def equiv(other: Signature): Boolean

  /**
   * Hash companion of [[equiv]], used by custom signature-key maps in member merge logic.
   */
  def equivHashCode: Int
}

object Signature {
  /**
   * Extractor exposing the minimal pair most consumers need:
   * declaration PSI + use-site substitutor.
   */
  def unapply(arg: Signature): Option[(PsiNamedElement, ScSubstitutor)] = Some((arg.namedElement, arg.substitutor))

  case class ExportedSigInfo(
    exportedIn:   ScExportsHolder,
    exportedFrom: Option[ScType]
  )
}
