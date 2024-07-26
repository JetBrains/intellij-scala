package org.jetbrains.plugins.scala.lang

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.util.matching.Regex

package object transformation {
  private val SimpleName = new Regex("(?:.+\\.)?(.+)")
  private val PartiallyQualifiedName = new Regex(".+\\.(.+\\..+)")
  private val RelativeName = new Regex("_root_\\.(.+)")
  private val AbsoluteName = new Regex("(_root_\\..+)")
  private val FullName = new Regex("(.+)")

  def quote(s: String): String = "\"" + s + "\""

  def simpleNameOf(qualifiedName: String): String = qualifiedName match {
    case SimpleName(name) => name
  }

  // TODO create a separate unit test for this method
  // Tries to use simple name, then partially qualified name, then fully qualified name instead of adding imports
  def bindTo(reference: ScReference, target: String): Unit = {
    implicit val context: PsiElement = reference.getParent

    implicit val isExpression: Boolean = reference.isInstanceOf[ScReferenceExpression]

    @tailrec
    def bindTo0(r1: ScReference, paths: Seq[String]): Unit = {
      paths match {
        case Seq(path, alternatives @ _*)  =>
          implicit val projectContext: ProjectContext = r1.projectContext
          val r2 = r1.replace(createReferenceElement(path, r1)).asInstanceOf[ScReference]
          if (!isResolvedTo(r2, target)) {
            bindTo0(r2, alternatives)
          }
        case _ =>
      }
    }

    val variants = variantsOf(target)

    if (!(reference.textMatches(variants.head) && isResolvedTo(reference, target))) {
      bindTo0(reference, variants)
    }
  }

  private def variantsOf(reference: String): Seq[String] =
    Seq(SimpleName, PartiallyQualifiedName, RelativeName, AbsoluteName, FullName)
      .flatMap(_.findFirstMatchIn(reference)).map(_.group(1)).distinct

  private def relative(reference: String): String = reference.replaceFirst("^_root_.", "")

  private def isResolvedTo(reference: ScReference, target: String)
                          (implicit context: PsiElement, isExpression: Boolean): Boolean =
    reference.bind().exists(result =>
      qualifiedNameOf(result.element) == relative(target))

  private def createReferenceElement(reference: String, ctx: PsiElement)(implicit isExpression: Boolean): ScReference =
    if (isExpression) createReferenceExpressionFromText(reference)(ctx.getProject)
    else createTypeElementFromText(reference, ctx)(ctx).getFirstChild.asInstanceOf[ScReference]

  // TODO define PsiMember.qualifiedName
  def qualifiedNameOf(e: PsiNamedElement): String = e match {
    // TODO support complex types, how to handle aliases?
    case it: ScTypeAliasDefinition =>
      it.aliasedType.toOption
        .map(_.canonicalText)
        .map(relative)
        .getOrElse(it.name)
    case it: PsiClass => it.qualifiedName
    case it: PsiMember => Option(it.containingClass).map(_.qualifiedName + ".").getOrElse("") + it.name
    case it => it.name
  }

  def targetFor(result: ScalaResolveResult): String = {
    ScSubstitutor.updateThisTypeDeep(result.substitutor).collect {
      case t: ScThisType => t.element.qualifiedName + "." + result.element.name
      case t: ScDesignatorType => qualifiedNameOf(t.element) + "." + result.element.name
    } getOrElse {
      qualifiedNameOf(result.element)
    }
  }

  object RenamedReference {
    def unapply(r: ScReference): Option[(String, String)] = {
      val id = r.nameId
      r.bind().map(_.element) collect  {
        case target: PsiNamedElement if !id.textMatches(target.name) => (id.getText, target.name)
      }
    }
  }

  object QualifiedReference {
    def unapply(r: ScReference): Some[(Option[ScalaPsiElement], PsiElement)] =
      Some(r.qualifier, r.nameId)
  }
}
