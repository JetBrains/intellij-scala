package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiMethod, PsiPackage}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGivenDefinition, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Finds classes, packages and top-level definitions by name.
 *
 * The lookups run on the indexes, which are backend data, so the PSI core only knows this interface;
 * [[impl.ScalaPsiManager]] implements it. Without an implementation (e.g. on a frontend) nothing is found.
 */
@ApiStatus.Internal
trait ScalaClassLookup {
  def project: Project

  def getCachedClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass]

  def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass]

  def getCachedPackage(fqn: String): Option[PsiPackage]

  def getCachedPackageInScope(fqn: String)(implicit scope: GlobalSearchScope): Option[PsiPackage]

  /** The `_root_` package */
  def emptyNamePackage: Option[ScPackageImpl]

  def getClasses(`package`: PsiPackage)(implicit scope: GlobalSearchScope): Array[PsiClass]

  /** The names of the classes in the `scala` package */
  def getScalaPackageClassNames(implicit scope: GlobalSearchScope): Set[String]

  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject]

  def getTopLevelImplicitClassesByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScClass]

  def getTopLevelGivenDefinitionsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScGivenDefinition]

  def getTopLevelDefinitionsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScMember]

  def getTopLevelExportsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScExportStmt]

  def findPackageObjectByName(fqn: String, scope: GlobalSearchScope): Option[ScObject]

  def methodsByName(name: String)(implicit scope: GlobalSearchScope): Iterable[PsiMethod]

  /** For a class in library sources, the compiled class of the same library; otherwise the class itself */
  def originalClass(psiClass: PsiClass): PsiClass

  /** The top-level packages of the current Java package migration (Refactor | Migrate), if one runs */
  def migrationRootPackages: Seq[PsiPackage]

  def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias]

  def scalaNamedTupleAlias(scope: GlobalSearchScope): Option[ScTypeAlias]

  def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType]
}

object ScalaClassLookup {

  def instance(
    implicit
    ctx: ProjectContext,
    d:   DummyImplicit
  ): ScalaClassLookup =
    instance(ctx.getProject)

  def instance(project: Project): ScalaClassLookup =
    ApplicationManager.getApplication.getService(classOf[Provider]) match {
      case null     => new NoClassLookup(project)
      case provider => provider.lookup(project)
    }

  /** Gives the [[ScalaClassLookup]] of a project; an application service, so that the lookup can be a project one */
  @ApiStatus.Internal
  trait Provider {
    def lookup(project: Project): ScalaClassLookup
  }

  private final class NoClassLookup(override val project: Project) extends ScalaClassLookup {
    override def getCachedClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] = None

    override def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = PsiClass.EMPTY_ARRAY

    override def getCachedPackage(fqn: String): Option[PsiPackage] = None

    override def getCachedPackageInScope(fqn: String)(implicit scope: GlobalSearchScope): Option[PsiPackage] = None

    override def emptyNamePackage: Option[ScPackageImpl] = None

    override def getClasses(`package`: PsiPackage)(implicit scope: GlobalSearchScope): Array[PsiClass] = PsiClass.EMPTY_ARRAY

    override def getScalaPackageClassNames(implicit scope: GlobalSearchScope): Set[String] = Set.empty

    override def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] = Seq.empty

    override def getTopLevelImplicitClassesByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScClass] = Seq.empty

    override def getTopLevelGivenDefinitionsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScGivenDefinition] = Seq.empty

    override def getTopLevelDefinitionsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScMember] = Iterable.empty

    override def getTopLevelExportsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScExportStmt] = Iterable.empty

    override def findPackageObjectByName(fqn: String, scope: GlobalSearchScope): Option[ScObject] = None

    override def methodsByName(name: String)(implicit scope: GlobalSearchScope): Iterable[PsiMethod] = Iterable.empty

    override def originalClass(psiClass: PsiClass): PsiClass = psiClass

    override def migrationRootPackages: Seq[PsiPackage] = Seq.empty

    override def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias] = None

    override def scalaNamedTupleAlias(scope: GlobalSearchScope): Option[ScTypeAlias] = None

    override def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType] = None
  }
}
