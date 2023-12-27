package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiModifierListOwner}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScNullLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScBlockStatement, ScExpression, ScThrow, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.util.matching.Regex

sealed trait Declaration {
  def entity: Entity
  def visibility: Visibility
  def isImplicit: Boolean
  def isConstant: Boolean
  def hasUnitType: Boolean
  def hasAccidentalStructuralType: Boolean
  def typeMatches(patterns: collection.Set[String]): Boolean
  def isAnnotatedWith(annotations: collection.Set[String]): Boolean

  /**
   * @return true when
   *         - method is abstract
   *         - method returns `null`
   *         - method throws an exception
   *
   *         false - otherwise
   */
  def isAbstractOrReturnsNullOrThrows: Boolean
}

object Declaration {
  private val AsteriskPattern = new Regex("(.*)\\*(.*)")

  def apply(element: PsiElement): Declaration = new PhysicalDeclaration(element)

  def apply(element: PsiElement, newVisibility: Visibility): Declaration = new PhysicalDeclaration(element) {
    override def visibility: Visibility = newVisibility
  }

  def apply(visibility: Visibility = Visibility.Default,
            isImplicit: Boolean = false,
            isConstant: Boolean = false,
            hasUnitType: Boolean = false,
            hasStructuralType: Boolean = false): Declaration =
    SyntheticDeclaration(visibility, isImplicit, isConstant, hasUnitType, hasStructuralType)

  private class PhysicalDeclaration(element: PsiElement) extends Declaration {
    override def entity: Entity = element match {
      case _: ScValue => Entity.Value
      case _: ScVariable => Entity.Variable
      case _: ScParameter => Entity.Parameter
      case _: ScUnderscoreSection => Entity.UnderscoreParameter
      case _ => Entity.Method
    }

    override def visibility: Visibility = element match {
      case owner: ScModifierListOwner =>
        if (owner.hasModifierPropertyScala("private")) Visibility.Private
        else if (owner.hasModifierPropertyScala("protected")) Visibility.Protected
        else Visibility.Default
      case owner: PsiModifierListOwner =>
        if (owner.hasModifierProperty("public")) Visibility.Default
        else if (owner.hasModifierProperty("private")) Visibility.Private
        else Visibility.Protected
      case _ => Visibility.Default
    }

    override def isImplicit: Boolean = element match {
      case owner: ScModifierListOwner => owner.hasModifierPropertyScala("implicit")
      case _ => false
    }

    override def isConstant: Boolean = element match {
      case value: ScValue => value.hasModifierPropertyScala("final")
      case _ => false
    }

    override def hasUnitType: Boolean = element match {
      case f: ScFunction => f.hasUnitResultType
      case v: Typeable => v.`type`().exists(_.isUnit)
      case _ => false
    }

    override def typeMatches(patterns: collection.Set[String]): Boolean = element match {
      case v: Typeable => v.`type`().exists(t => patterns.exists(matches(t, _)))
      case _ => false
    }

    override def isAnnotatedWith(annotations: collection.Set[String]): Boolean = element match {
      case holder: ScAnnotationsHolder => annotations.exists(holder.hasAnnotation)
      case _ => false
    }

    override def hasAccidentalStructuralType: Boolean = {
      def effectivelyEmpty(comps: Iterable[ScType]): Boolean =
        comps.isEmpty || (comps.size == 1 && comps.head.canonicalText == "_root_.java.lang.Object")

      element match {
        case Typeable(tpe @ ScCompoundType(comps, defs, _)) if !effectivelyEmpty(comps) =>
          implicit val ctx: ProjectContext = tpe.projectContext
          val noAliases = ScCompoundType(comps, defs, Map.empty)
          !ScCompoundType(comps).conforms(noAliases)
        case _ => false
      }
    }

    override def isAbstractOrReturnsNullOrThrows: Boolean = {
      element match {
        case member: ScMember =>
          //NOTE: Unfortunately there is no general API to get body of val/var/def for us here it's enough to use this AST-based approach
          val body = PsiTreeUtil.findChildOfType(member, classOf[ScExpression], false)
          body == null || {
            val lastStatement = getStatementAtReturnPosition(body)
            lastStatement.exists(_.is[ScNullLiteral, ScThrow])
          }
        case _ => false
      }
    }

    /**
     * Examples for various definition bodies
     *  - def f ---> None (abstract method)
     *  - def f = 42 ---> 42
     *  - def f = { 42 } ---> 42
     *  - def f = { println(); 42 } ---> 42
     *  - def f = { println(); 42; println() } ---> println()
     */
    @tailrec
    private def getStatementAtReturnPosition(stmt: ScBlockStatement): Option[ScBlockStatement] = {
      stmt match {
        case block: ScBlockExpr =>
          block.lastStatement match {
            case Some(lastExpr) => getStatementAtReturnPosition(lastExpr)
            case _ => None
          }
        case other => Some(other)
      }
    }
  }

  private def matches(t: ScType, pattern: String): Boolean = {
    val s = t.canonicalText.stripPrefix("_root_.")

    pattern match {
      case AsteriskPattern(prefix, suffix) =>
        s.length > prefix.length + suffix.length && s.startsWith(prefix) && s.endsWith(suffix)
      case plainText =>
        s == plainText
    }
  }

  private case class SyntheticDeclaration(override val visibility: Visibility,
                                          override val isImplicit: Boolean,
                                          override val isConstant: Boolean,
                                          override val hasUnitType: Boolean,
                                          override val hasAccidentalStructuralType: Boolean) extends Declaration {

    override def entity: Entity = Entity.Method

    override def typeMatches(patterns: collection.Set[String]): Boolean = false

    override def isAnnotatedWith(annotations: collection.Set[String]): Boolean = false

    //always returning false just because in all usages where SyntheticDeclaration this value seems to be irrelevant
    override def isAbstractOrReturnsNullOrThrows: Boolean = false
  }
}
