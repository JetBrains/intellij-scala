package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope, PackageScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement, PsiPackage, PsiReference}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl

import scala.annotation.tailrec

private object ScalaUseScope {

  def apply(baseUseScope: SearchScope, element: ScalaPsiElement): SearchScope = {
    val useScope = element.containingScalaFile.fold(baseUseScope)(apply(baseUseScope, _))
    val narrowScope = element match {
      case cp: ScClassParameter => classParameterScope(cp)
      case p: ScParameter       => Option(parameterScope(p))
      case n: ScNamedElement    => namedScope(n)
      case m: ScMember          => memberScope(m)
      case _                    => None
    }
    intersect(useScope, narrowScope)
  }

  // Keep in mind that:
  // 1. Elements in worksheets can only be used in those files
  // 2. Elements in scratch files can only be used in those files
  // 3. Scratch files can be configured to be treated like worksheets (enabled by default)
  // 4. Scratch files have no concept of Project, so GlobalSearchScope should not be used
  def apply(baseUseScope: SearchScope, file: ScalaFile): SearchScope =
    file match {
      case ScFile.VirtualFile(virtualFile) =>
        if (ScratchUtil.isScratch(virtualFile) || file.isWorksheetFile)
          fileScopeForScratchFiles(file)
        else
          baseUseScope
      case _ =>
        baseUseScope
    }

  /**
   * There are two possible ways to define "file scope":
   *  1. new LocalSearchScope(file)
   *  2. GlobalSearchScope.fileScope(project, virtualFile)
   *
   * With both approaches "Show hierarchy" action doesn't work in Scratch files due to this bug in IntelliJ Platform:<br>
   * [[https://youtrack.jetbrains.com/issue/IDEA-313012/type-hierarchy-doesnt-detect-inheritors-for-classes-in-scratch-files]]
   *
   * Also "Is mixed into" and "Member has implementation/overrides" gutter icons do not work for same root cause.
   *
   * However if we use `new LocalSearchScope(file)` gutter icons still work.<br>
   * This is due to a workaround in
   * [[org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaLocalInheritorsSearcher.processQuery]]
   * which only can handle `LocalSearchScope`
   *
   * [[com.intellij.refactoring.rename.RenameUtil#processUsages(com.intellij.psi.PsiElement, com.intellij.refactoring.rename.RenamePsiElementProcessorBase, java.lang.String, com.intellij.psi.search.SearchScope, boolean, boolean, boolean, com.intellij.util.Processor)]]
   * adds an intersection with a searchScope for all scopes except LocalSearchScope. This could break rename refactorings
   * for external worksheets (for useScope=GlobalSearchScope.fileScope and searchScope=ProjectScope an intersection doesn't include external files)
   * See #SCL-21278
   */
  private def fileScopeForScratchFiles(file: PsiFile): SearchScope = {
    //GlobalSearchScope.fileScope(file.getProject, virtualFile)
    new LocalSearchScope(file)
  }

  private def intersect(scope: SearchScope, scopeOption: Option[SearchScope]): SearchScope =
    scopeOption.fold(scope)(_.intersectWith(scope))

  private def intersectOptions(scope1: Option[SearchScope], scope2: Option[SearchScope]): Option[SearchScope] =
    scope1.map(intersect(_, scope2)).orElse(scope2)

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
    if (elem.isValid && elem.getContainingFile != null)
      new LocalSearchScope(elem)
    else
      LocalSearchScope.EMPTY

  private def classParameterScope(cp: ScClassParameter): Option[SearchScope] = {
    val asNamedArgument =
      Option(PsiTreeUtil.getContextOfType(cp, classOf[ScPrimaryConstructor]))
        .map(_.getUseScope)
        .getOrElse(LocalSearchScope.EMPTY)

    val classScope = Option(cp.containingClass).map(_.getUseScope)
    val asMember = intersectOptions(byAccessModifier(cp), classScope)

    asMember.map(_.union(asNamedArgument))
  }

  private def memberScope(member: ScMember): Option[SearchScope] = {
    syntheticMethodScope(member)
      .orElse(byAccessModifier(member))
      .orElse(fromContainingBlockOrMember(member))
  }

  private def byAccessModifier(member: ScMember): Option[SearchScope] =
    fromUnqualifiedOrThisPrivate(member) orElse fromQualifiedPrivate(member)

  private def localSearchScope(typeDefinition: ScTypeDefinition, withCompanion: Boolean = true): SearchScope = {
    val scope = safeLocalScope(typeDefinition)
    if (withCompanion) {
      typeDefinition.baseCompanion match {
        case Some(td) => scope.union(safeLocalScope(td))
        case _ => scope
      }
    }
    else scope
  }

  //private top level classes may be used in the same package
  private def forTopLevelPrivate(modifierListOwner: ScModifierListOwner) = modifierListOwner match {
    case td: ScTypeDefinition if td.isTopLevel =>
      for {
        qName <- Option(td.qualifiedName)
        parentPackage <- ScalaPsiUtil.parentPackage(qName, td.getProject)
      } yield new PackageScope(parentPackage, /*includeSubpackages*/ true, /*includeLibraries*/ true)
    case _ => None
  }

  @tailrec
  private def fromContainingBlockOrMember(elem: PsiElement): Option[SearchScope] = {
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
  //member of a qualified private class may escape its package with inheritance
  private def fromQualifiedPrivate(member: ScMember): Option[SearchScope] = {
    def resolve(reference: PsiReference): Option[PsiNamedElement] = reference match {
      case ResolvesTo(target: PsiNamedElement) =>
        target match {
          case o: ScObject if o.isPackageObject =>
            val pName = ScObjectImpl.stripLegacyPackageObjectSuffixWithDot(o.qualifiedName)
            ScPackageImpl.findPackage(o.getProject, pName)
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
      case p: PsiPackage =>

        /**
         * This case is only entered if `private[foo]` refers to a valid package.
         * com.intellij.psi.search.PackageScope correctly handles local scopes of files that are placed in the directory
         * that is congruent with a file's package declaration. If the file is not in such a directory, PackageScope
         * does not include the file that contains the ScMember.
         */

        new PackageScope(p, /*includeSubpackages*/ true, /*includeLibraries*/ true)

      case td: ScTypeDefinition =>

        /**
         * This case is entered if `private[ExistingClass]` syntax is used.
         * This case is also entered if `private[foo]` or `private[Foo]` does not refer to a valid, accessible
         * package or class, yielding a reasonable fallback that doesn't impede the editing experience.
         */

        localSearchScope(td)
    }
  }

  private def fromUnqualifiedOrThisPrivate(owner: ScMember) = for {
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

  private def syntheticMethodScope(member: ScMember): Option[SearchScope] = {
    member match {
      case fun: ScFunction if fun.isSynthetic =>
        Some(fun.syntheticNavigationElement.getUseScope)
      case _ => None
    }
  }
}
