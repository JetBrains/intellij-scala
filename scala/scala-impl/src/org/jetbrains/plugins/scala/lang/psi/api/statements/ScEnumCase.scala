package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScEnum, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScEnumCase extends ScConstructorOwner
  with ScParameterOwner
  with ScTypeParametersOwner {

  def enumParent: ScEnum

  def enumCases: ScEnumCases

  /**
   * Returns type parameters from an explicit type parameter clause only,
   * as opposed to [[typeParameters]], which will also return
   * type params implicitly inherited from parent enum class.
   */
  def physicalTypeParameters: Seq[ScTypeParam]

  //TODO: it seems not entirely correct to return `true` for all enum cases
  // Singleton enum cases are not desugared to a "case class", so some code which uses `isCase` might work incorrectly
  // Maybe we should return `enumKind == ScEnumCaseKind.ClassCase` instead?
  override def isCase: Boolean = true

  def enumKind: ScEnumCaseKind

  def getSyntheticCounterpart: ScNamedElement
}

object ScEnumCase {
  object SingletonCase {
    def unapply(cse: ScEnumCase): Option[(String, Seq[ScType])] =
      Option.when(cse.constructor.isEmpty)((cse.name, cse.superTypes))
  }

  object Original {
    def unapply(e: ScNamedElement): Option[ScEnumCase] = {
      OriginalEnum.unapply(e).flatMap {
        enum =>
          val name = e match {
            case f: ScFunctionDefinition =>
              Option(f.context) match {
                case Some(n: ScNamedElement) => n.name
                case _ => e.name
              }
            case _ => e.name
          }
          enum.cases.find(_.name == name)
      }
    }
  }

  object OriginalEnum {
    def unapply(e: ScNamedElement): Option[ScEnum] = {
      val nameContextOriginal = e.nameContext

      /**
       * Consider example with calling parametrised enum case constructor {{{
       *   enum MyEnum { case MyCase(i: Int) }
       *   val b = MyCase(42)
       * }}}
       * When resolving `MyCase` in `MyCase(42)` it's not resolved to the `case MyCase`<br>
       * Instead it can resolve to two elements:
       *  1. synthetic `object MyCase`, generated for `case MyCase`
       *  1. synthetic `apply` method, which constructs MyCase synthetic class instance
       */
      val nameContextWithFixedEnumCaseSynthetics = nameContextOriginal match {
        case o: ScObject if o.isSynthetic =>
          o.syntheticNavigationElement match {
            case c: ScClass if c.isSynthetic && c.isCase =>
              c
            case _ =>
              nameContextOriginal
          }
        case f: ScFunctionDefinition if f.name == "apply" && f.isSynthetic =>
          //Here it's implied that for synthetic `apply` method, corresponding to the enum case,
          //`context` will return synthetic `case class` corresponding to same enum case
          //TODO: why the context of `apply` method is a class and not synthetic object?
          f.context

        case _ =>
          nameContextOriginal
      }

      //We are interested in two types of `member`:
      //  1. synthetic `val MyCase` (for singleton enum cases)
      //  2. synthetic `case class MyCase` (for class enum cases)
      //Both cases have enum object as a `syntheticNavigationElement` (whether synthetic or physical, if exists)
      nameContextWithFixedEnumCaseSynthetics match {
        case member: ScMember =>
          member.syntheticNavigationElement match {
            case obj: ScObject => obj.baseCompanion.filterByType[ScEnum]
            case _             => None
          }
        case _ => None
      }
    }
  }

  def isDesugaredEnumCase(cls: ScNamedElement): Boolean =
    OriginalEnum.unapply(cls).isDefined
}
