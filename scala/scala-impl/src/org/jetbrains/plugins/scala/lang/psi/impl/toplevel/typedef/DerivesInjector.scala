package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDerivesClauseOwner, ScObject, ScTypeDefinition}

/**
 * Injects synthetic given definitions generated from data-type derives clause
 * into companion object.
 */
class DerivesInjector extends SyntheticMembersInjector{
  override def needsCompanionObject(source: ScTypeDefinition): Boolean = source match {
    case owner: ScDerivesClauseOwner => owner.derivesClause.exists(_.derivedReferences.nonEmpty)
    case _                           => false
  }

  override def injectFunctions(
    source: ScTypeDefinition
  ): Seq[String] = source match {
    case obj: ScObject => obj.fakeCompanionClassOrCompanionClass match {
      case owner: ScDerivesClauseOwner =>
        val tcs = owner.derivesClause.toSeq.flatMap(_.derivedReferences)
        tcs.map { ref =>
          for {
            tc  <- DerivesUtil.resolveTypeClassReference(ref)
            sig <- DerivesUtil.checkIfCanBeDerived(tc, ref.refName, owner)
          } yield sig
        }.collect { case Right(sig) => sig }
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }
}
