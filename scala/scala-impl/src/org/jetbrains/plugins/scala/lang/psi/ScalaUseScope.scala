package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, PackageScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiPackage, PsiReference}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ResolvesTo}
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
    val maybeNarrowScope = element match {
      case p: ScParameter    => Some(parameterScope(p))
      case n: ScNamedElement => namedScope(n)
      case m: ScMember       => memberScope(m)
      case _                 => None
    }

    for {
      narrowScope <- maybeNarrowScope

      file <- element.containingScalaFile
      if file.isWorksheetFile || file.isScriptFile

      scriptScope = new LocalSearchScope(file)
    } yield narrowScope.intersectWith(scriptScope)
  }

  private def parameterScope(parameter: ScParameter): SearchScope = parameter.getDeclarationScope match {
    case null => GlobalSearchScope.EMPTY_SCOPE
    case expr: ScFunctionExpr =>
      if (expr.isValid && expr.getContainingFile != null) new LocalSearchScope(expr)
      else LocalSearchScope.EMPTY
    case d => d.getUseScope //named parameters or usages of class parameters
  }

  private def namedScope(named: ScNamedElement): Option[SearchScope] = named.nameContext match {
    case member: ScMember if member != named => Some(member.getUseScope)
    case caseClause: ScCaseClause => Some(new LocalSearchScope(caseClause))
    case elem @ (_: ScEnumerator | _: ScGenerator) =>
      elem.parentOfType(classOf[ScForStatement]).orElse {
        elem.parentOfType(Seq(classOf[ScBlock], classOf[ScMember]))
      }.map {
        new LocalSearchScope(_)
      }
    case _ => None
  }

  private def memberScope(member: ScMember): Option[SearchScope] = {

    def localSearchScope(typeDefinition: ScTypeDefinition, withCompanion: Boolean = true): SearchScope = {
      val scope = new LocalSearchScope(typeDefinition)
      if (withCompanion) {
        typeDefinition.baseCompanionModule match {
          case Some(td) => scope.union(new LocalSearchScope(td))
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
        case b: ScBlock => Some(new LocalSearchScope(b))
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

    def fromUnqualifiedOrThisPrivate(owner: ScModifierListOwner) = for {
      list <- Option(owner.getModifierList)
      modifier <- list.accessModifier

      if modifier.isUnqualifiedPrivateOrThis

      scope <- forTopLevelPrivate(owner).orElse {
        member.containingClass match {
          case definition: ScTypeDefinition => Some(localSearchScope(definition, withCompanion = !modifier.isThis))
          case _ => member.containingFile.map(new LocalSearchScope(_))
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
