package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.search._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiPackage, PsiReference}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl

import scala.annotation.tailrec

object ScalaUseScope {

  def mostNarrow(element: ScalaPsiElement): Option[SearchScope] = {

    val narrowScope = element match {
      case p: ScParameter    => Option(parameterScope(p))
      case n: ScNamedElement => namedScope(n)
      case m: ScMember       => memberScope(m)
      case _                 => None
    }

    val scriptScope =
      element.containingScalaFile
        .filter(f => f.isWorksheetFile || f.isScriptFile)
        .map(safeLocalScope(_))

    intersect(narrowScope, scriptScope)
  }

  def intersect(scope: SearchScope, scopeOption: Option[SearchScope]): SearchScope = {
    scopeOption.map(_.intersectWith(scope))
      .getOrElse(scope)
  }

  private def intersect(scope1: Option[SearchScope], scope2: Option[SearchScope]): Option[SearchScope] = {
    scope1.map(intersect(_, scope2))
      .orElse(scope2)
  }

  private def parameterScope(parameter: ScParameter): SearchScope = parameter.getDeclarationScope match {
    case null                 => GlobalSearchScope.EMPTY_SCOPE
    case expr: ScFunctionExpr => safeLocalScope(expr)
    case td: ScTypeDefinition => intersect(td.getUseScope, namedScope(parameter)) //class parameters
    case d                    => d.getUseScope //named parameters
  }

  private def namedScope(named: ScNamedElement): Option[SearchScope] = named.nameContext match {
    case member: ScMember if member.isLocal      => localDefinitionScope(member)
    case member: ScMember if member != named     => Some(member.getUseScope)
    case member: ScMember                        => memberScope(member)
    case caseClause: ScCaseClause                => Some(safeLocalScope(caseClause))
    case elem@(_: ScForBinding | _: ScGenerator) => localDefinitionScope(elem)
    case _                                       => None
  }


  private def localDefinitionScope(elem: PsiElement): Option[LocalSearchScope] =
    elem.parentOfType(Seq(classOf[ScFor], classOf[ScBlock], classOf[ScMember]))
      .map(safeLocalScope)
  
  private def safeLocalScope(elem: PsiElement): LocalSearchScope =
    if (elem.isValid && elem.getContainingFile != null) new LocalSearchScope(elem)
    else LocalSearchScope.EMPTY

  private def memberScope(member: ScMember): Option[SearchScope] = {

    def localSearchScope(typeDefinition: ScTypeDefinition, withCompanion: Boolean = true): SearchScope = {
      val scope = safeLocalScope(typeDefinition)
      if (withCompanion) {
        typeDefinition.baseCompanionModule match {
          case Some(td) => scope.union(safeLocalScope(td))
          case _ => scope
        }
      }
      else scope
    }

    //private top level classes may be used in the same package
    def forTopLevelPrivate(modifierListOwner: ScModifierListOwner) = modifierListOwner match {
      case td: ScTypeDefinition if td.isTopLevel =>
        for {
          qName <- Option(td.qualifiedName)
          parentPackage <- ScalaPsiUtil.parentPackage(qName, td.getProject)
        } yield new PackageScope(parentPackage, false, true)
      case _ => None
    }

    @tailrec
    def fromContainingBlockOrMember(elem: PsiElement): Option[SearchScope] = {
      val blockOrMember = PsiTreeUtil.getContextOfType(elem, true, classOf[ScBlock], classOf[ScMember])
      blockOrMember match {
        case null => None
        case b: ScBlock => Some(safeLocalScope(b))
        case o: ScObject => Some(o.getUseScope)
        case td: ScTypeDefinition => //can't use td.getUseScope because of inheritance
          fromUnqualifiedOrThisPrivate(td) match {
            case None => fromContainingBlockOrMember(td)
            case scope => scope
          }
        case member: ScMember => Some(member.getUseScope)
      }
    }

    //should be checked only for the member itself
    //member of a qualified private class may escape it's package with inheritance
    def fromQualifiedPrivate(): Option[SearchScope] = {
      def resolve(reference: PsiReference): Option[PsiNamedElement] = reference match {
        case ResolvesTo(target: PsiNamedElement) =>
          target match {
            case o: ScObject if o.isPackageObject =>
              val pName = o.qualifiedName.stripSuffix(".`package`")
              Some(ScPackageImpl.findPackage(o.getProject, pName))
            case _ => Some(target)
          }
        case _ => None
      }

      val maybeTarget = for {
        list <- Option(member.getModifierList)
        modifier <- list.accessModifier

        if modifier.isPrivate && !modifier.isUnqualifiedPrivateOrThis

        target <- resolve(modifier.getReference).orElse {
          modifier.parentOfType(classOf[ScTypeDefinition])
        }
      } yield target

      maybeTarget.collect {
        case p: PsiPackage => new PackageScope(p, /*includeSubpackages*/ true, /*includeLibraries*/ true)
        case td: ScTypeDefinition => localSearchScope(td)
      }
    }

    def fromUnqualifiedOrThisPrivate(owner: ScMember) = for {
      list <- Option(owner.getModifierList)
      modifier <- list.accessModifier

      if modifier.isUnqualifiedPrivateOrThis

      scope <- forTopLevelPrivate(owner).orElse {
        owner.containingClass match {
          case definition: ScTypeDefinition => Some(localSearchScope(definition, withCompanion = !modifier.isThis))
          case _ => owner.containingFile.map(safeLocalScope(_))
        }
      }
    } yield scope

    def fromContext = member match {
      case cp: ScClassParameter =>
        Option(cp.containingClass)
          .map(_.getUseScope)
      case fun: ScFunction if fun.isSynthetic =>
        Some(fun.syntheticNavigationElement.getUseScope)
      case _ =>
        fromQualifiedPrivate().orElse {
          fromContainingBlockOrMember(member)
        }
    }
    fromUnqualifiedOrThisPrivate(member).orElse(fromContext)
  }
}
