package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.{PsiElement, PsiPackage}
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, PackageScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}

import scala.annotation.tailrec

object ScalaUseScope {

  def mostNarrow(element: ScalaPsiElement): Option[SearchScope] = {
    val narrowScope = element match {
      case p: ScParameter    => Some(parameterScope(p))
      case n: ScNamedElement => namedScope(n)
      case m: ScMember       => memberScope(m)
      case _                 => None
    }
    for (narrow <- narrowScope; script <- scriptScope(element))
      yield narrow.intersectWith(script)
  }

  private def scriptScope(element: ScalaPsiElement): Option[LocalSearchScope] = {
    element.containingScalaFile.filter { file =>
      file.isWorksheetFile || file.isScriptFile
    }.map {
      new LocalSearchScope(_)
    }
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
      Option(PsiTreeUtil.getContextOfType(elem, true, classOf[ScForStatement]))
        .orElse(Option(PsiTreeUtil.getContextOfType(elem, true, classOf[ScBlock], classOf[ScMember])))
        .map(new LocalSearchScope(_))
    case _ => None
  }

  private def memberScope(member: ScMember): Option[SearchScope] = {
    def accessModifier(modifierListOwner: ScModifierListOwner) =
      Option(member.getModifierList).flatMap(_.accessModifier)

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

    def containingClassScope(withCompanion: Boolean) = member.containingClass match {
      case definition: ScTypeDefinition => Some(localSearchScope(definition, withCompanion))
      case _ => member.containingFile.map(new LocalSearchScope(_))
    }

    //private top level classes may be used in the same package
    def forTopLevelPrivate(modifierListOwner: ScModifierListOwner) = modifierListOwner match {
      case td: ScTypeDefinition if td.isTopLevel =>
        val qName = Option(td.qualifiedName)
        val parentPackage = qName.flatMap(ScalaPsiUtil.parentPackage(_, td.getProject))
        parentPackage.map(new PackageScope(_, /*includeSubpackages*/ false, /*includeLibraries*/ true))
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
      accessModifier(member).filter(am => am.isPrivate && !am.isUnqualifiedPrivateOrThis).map(_.scope) collect {
        case p: PsiPackage => new PackageScope(p, /*includeSubpackages*/true, /*includeLibraries*/true)
        case td: ScTypeDefinition => localSearchScope(td)
      }
    }

    def fromUnqualifiedOrThisPrivate(modifierListOwner: ScModifierListOwner) = {
      accessModifier(modifierListOwner) match {
        case Some(mod) if mod.isUnqualifiedPrivateOrThis =>
          forTopLevelPrivate(modifierListOwner).orElse {
            containingClassScope(withCompanion = !mod.isThis)
          }
        case _ => None
      }
    }

    def fromContext = member match {
      case cp: ScClassParameter =>
        Option(cp.containingClass)
          .map(_.getUseScope)
      case fun: ScFunction if fun.isSynthetic =>
        fun.getSyntheticNavigationElement
          .map(_.getUseScope)
      case _ =>
        fromQualifiedPrivate().orElse {
          fromContainingBlockOrMember(member)
        }
    }
    fromUnqualifiedOrThisPrivate(member).orElse(fromContext)
  }


}
