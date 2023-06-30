package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScEnum, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.annotation.tailrec

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

  override def isCase: Boolean = true

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
    @tailrec
    def unapply(e: ScNamedElement): Option[ScEnum] = {

      e.nameContext match {

        // When calling a parameterized enum case's synthetic apply constructor. For example:
        // enum Foo { case Bar(i: Int) }
        // val b = Bar(42)
        case f: ScFunctionDefinition if f.isSynthetic =>
          Option(f.context) match {
            case Some(n: ScNamedElement) => OriginalEnum.unapply(n)
            case _ => None
          }

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
