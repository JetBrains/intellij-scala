package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiPackage, PsiTypeParameter}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGivenDefinition, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{MixinNodes, TypeDefinitionMembers}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScAndType, ScCompoundType, ScParameterizedType, ScType, Signature, TermSignature, TypeSignature}
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util.concurrent.ConcurrentMap

trait ScalaPsiManager {

  implicit val project: Project

  val implicitCollectorCache: ImplicitCollectorCache
  val collectImplicitObjectsCache: ConcurrentMap[(ScType, GlobalSearchScope), Seq[ScType]]

  val TopLevelModificationTracker: SimpleModificationTracker

  val TermNodesCache: SignatureCaches[TermSignature]
  val StableNodesCache: SignatureCaches[TermSignature]
  val TypeNodesCache: SignatureCaches[TypeSignature]

  private[psi] def noNamePackage: ScPackage
  private[psi] def clearOnLowMemory(): Unit

  private[psi] def projectOpened(): Unit


  def isInJavaPsiFacade: Boolean

  def clearAllCachesAndWait(): Unit
  def clearAllCaches(): Unit

  def syntheticPackage(fqn: String): ScSyntheticPackage
  def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType]
  def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias]
  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType
  def simpleAliasProjectionCached(projection: ScProjectionType): ScType

  def getCachedClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass]
  def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass]
  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[PsiClass]
  def getTypeAliasesByName(name: String, scope: GlobalSearchScope): Iterable[ScTypeAlias]
  def getCachedPackage(inFqn: String): Option[PsiPackage]
  def getStableAliasesByFqn(fqn: String, scope: GlobalSearchScope): Iterable[ScTypeAlias]
  def getScalaPackageClassNames(implicit scope: GlobalSearchScope): Set[String]
  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject]
  def getTopLevelImplicitClassesByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScClass]
  def getTopLevelGivenDefinitionsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScGivenDefinition]
  def getCachedPackageInScope(fqn: String)(implicit scope: GlobalSearchScope): Option[PsiPackage]
  def getClasses(`package`: PsiPackage)(implicit scope: GlobalSearchScope): Array[PsiClass]
  def getJavaPackageClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String]
  def getStableTypeAliasesNames: Iterable[String]
  def getTopLevelDefinitionsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScMember]
  def getTopLevelExportsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScExportStmt]

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TypeDefinitionMembers.StableNodes.Map
  def getStableSignatures(tp: ScAndType): TypeDefinitionMembers.StableNodes.Map
  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TypeDefinitionMembers.TypeNodes.Map
  def getTypes(tp: ScAndType): TypeDefinitionMembers.TypeNodes.Map
  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TypeDefinitionMembers.TermNodes.Map
  def getSignatures(tp: ScAndType): TypeDefinitionMembers.TermNodes.Map
}

object ScalaPsiManager {
  def instance(
    implicit
    ctx: ProjectContext,
    d: DummyImplicit
  ): ScalaPsiManager =
    instance(ctx.getProject)

  def instance(project: Project): ScalaPsiManager =
    project.getService(classOf[ScalaPsiManagerHolder]).get
}

private[psi] trait ScalaPsiManagerHolder {
  def get: ScalaPsiManager
  def dispose(): Unit
}

trait SignatureCaches[T <: Signature] {

  protected val nodes: MixinNodes[T]

  protected val forLibraryMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map]
  protected val forTopLevelMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map]

  protected def forLibraryClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map

  protected def forTopLevelClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map

  def cachedMap(clazz: PsiClass, withSupers: Boolean): nodes.Map
}