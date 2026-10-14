package org.jetbrains.plugins.scala.autoImport

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenDefinition, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.StableValIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withAllInheritors
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

abstract class GlobalMember[M <: ScMember](val owner: GlobalMemberOwner,
                                           val pathToOwner: String,
                                           val member: M) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def substitutor: ScSubstitutor = owner.substitutor(member)

  def qualifiedName: String = pathToOwner + "." + named.name
}

object GlobalMember {

  private case class GlobalMemberImpl[M <: ScMember](override val owner: GlobalMemberOwner,
                                                     override val pathToOwner: String,
                                                     override val member: M)
    extends GlobalMember[M](owner, pathToOwner, member)

  def apply[M <: ScMember](owner: GlobalMemberOwner, pathToOwner: String, member: M): GlobalMember[M] =
    GlobalMemberImpl(owner, pathToOwner, member)

  def findGlobalMembers[M <: ScMember, GM <: GlobalMember[M]](member: M,
                                                              scope: GlobalSearchScope)
                                                             (constructor: (GlobalMemberOwner, String, M) => GM): Set[GM] = {
    val fromObjects =
      findInheritorObjectsForOwner(member)
        .map(o => constructor(GlobalMemberOwner.TypedDefinition(o), o.qualifiedName, member))

    if (fromObjects.nonEmpty) fromObjects
    else {
      val fromPackage = findPackageForTopLevelMember(member)
        .map(p => constructor(GlobalMemberOwner.Packaging(p), p.fullPackageName, member))
        .toSet

      val fromInheritors = for {
        clazz            <- withAllInheritors(member.containingClass)
        valueOfClassType <- StableValIndex.findValuesOfClassType(clazz, scope)
        named            <- valueOfClassType.declaredElements
        containingObject <- findAllInheritorObjectsForOwner(valueOfClassType)
      } yield constructor(GlobalMemberOwner.TypedDefinition(named), containingObject.qualifiedName + "." + named.name, member)

      val fromGivens = member match {
        case fn: ScFunction =>
          fn.extensionMethodOwner.toSet[ScExtension].collect {
            case Parent((_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(givenDef: ScGivenDefinition))) =>
              constructor(GlobalMemberOwner.GivenDefinition(givenDef), givenDef.qualifiedName, member)
          }
        case _ => Set.empty
      }

      fromPackage ++ fromInheritors ++ fromGivens
    }
  }

  //find objects which may be used to import this member
  //select a single one if possible
  def findInheritorObjectsForOwner(member: ScMember): Set[ScObject] = {
    val allObjects = findAllInheritorObjectsForOwner(member)
    if (allObjects.isEmpty || member.containingClass.hasTypeParameters) {
      allObjects
    } else {
      //if `clazz` is not generic, members in all objects are the same, so we return one with the shortest qualified name
      Set(allObjects.minBy(o => (o.isDeprecated, o.qualifiedName.length, o.qualifiedName)))
    }
  }

  //find objects which may be used to import this member
  def findAllInheritorObjectsForOwner(member: ScMember): Set[ScObject] =
    member.containingClass match {
      case null => Set.empty
      case clazz =>
        val inheritorObjectsInProject: Set[ScObject] = cachedInUserData("findAllInheritorObjectsForOwner.inheritorObjectsInProject", clazz, BlockModificationTracker(clazz), Tuple1(clazz)) {
          ScalaInheritors.allInheritorObjects(clazz)
        }

        val inheritorObjectsInLibraries: Set[ScObject] = cachedInUserData("findAllInheritorObjectsForOwner.inheritorObjectsInLibraries", clazz, ModTracker.libraryAware(clazz), Tuple1(clazz)) {
          ScalaInheritors.allInheritorObjects(clazz)
        }

        (inheritorObjectsInLibraries ++ inheritorObjectsInProject).filterNot(o => isInExcludedPackage(o.qualifiedName, member.getProject))
    }

  def findPackageForTopLevelMember(member: ScMember): Option[ScPackaging] =
    if (member.containingClass == null) {
      member match {
        case fn: ScFunction if fn.isExtensionMethod =>
          fn.extensionMethodOwner.flatMap(_.getContext.asOptionOf[ScPackaging])
        case _ =>
          member.getContext.asOptionOf[ScPackaging]
      }
    } else None

  def isInExcludedPackage(qualifiedName: String, project: Project): Boolean =
    ImportExclusions.instance.isExcluded(qualifiedName, project)
}
