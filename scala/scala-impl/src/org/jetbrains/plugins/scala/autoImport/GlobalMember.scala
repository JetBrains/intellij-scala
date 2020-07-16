package org.jetbrains.plugins.scala.autoImport

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.{findAllInheritorObjectsForOwner, findInheritorObjectsForOwner}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.hasStablePath
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.StableValIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.withAllInheritors
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

class GlobalMember[M <: ScMember](val owner: ScTypedDefinition,
                                  val pathToOwner: String,
                                  val member: M) {

  def named: ScNamedElement = member match {
    case named: ScNamedElement => named
    case vs: ScValueOrVariable => vs.declaredElements.head
  }

  def substitutor: ScSubstitutor = owner match {
    case o: ScObject if hasStablePath(o) => MixinNodes.asSeenFromSubstitutor(o, member.containingClass)
    case td: ScTypedDefinition =>
      val maybeSubstitutor =
        for {
          valType        <- td.`type`().toOption
          (clazz, subst) <- valType.extractClassType
        } yield MixinNodes.asSeenFromSubstitutor(clazz, member.containingClass).followed(subst)

      maybeSubstitutor.getOrElse(ScSubstitutor.empty)
  }

  def qualifiedName: String = pathToOwner + "." + named.name
}

object GlobalMember {

  def findGlobalMembers[M <: ScMember, GM <: GlobalMember[M]](member: M,
                                                              scope: GlobalSearchScope)
                                                             (constructor: (ScTypedDefinition, String, M) => GM): Set[GM] = {
    val fromObjects =
      findInheritorObjectsForOwner(member)
        .map(o => constructor(o, o.qualifiedName, member))

    if (fromObjects.nonEmpty) fromObjects
    else {
      for {
        clazz            <- withAllInheritors(member.containingClass, scope)
        (value, named)   <- valuesOfClass(clazz, scope)
        containingObject <- findAllInheritorObjectsForOwner(value)
      } yield constructor(named, containingObject.qualifiedName + "." + named.name, member)
    }
  }

  private def valuesOfClass(c: PsiClass, scope: GlobalSearchScope): Set[(ScValue, ScTypedDefinition)] =
    StableValIndex.findValuesOfClass(c, scope)
      .flatMap(v => v.declaredElements.map((v, _)))
      .toSet
}
