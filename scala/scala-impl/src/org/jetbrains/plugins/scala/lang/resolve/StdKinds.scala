package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

object StdKinds {

  /**
   * NOTE: other checks for "stable" are done here:
   *  - [[lang.psi.api.toplevel.ScTypedDefinition.isStable]]
   *  - [[lang.psi.impl.base.ScFieldIdImpl.isStable]]
   *  - [[lang.psi.api.statements.ScValueOrVariable.isStable]]
   *  - [[lang.psi.impl.toplevel.typedef.StableTermsCollector.isStable]]
   *  - [[lang.psi.impl.toplevel.typedef.StableTermsCollector.mayContainStable]]
   */
  val stableQualRef: ResolveTargets.ValueSet             = ValueSet(PACKAGE, OBJECT, VAL)
  // (also see SCL-19477)
  val stableQualRefCandidates: ResolveTargets.ValueSet   = ValueSet(PACKAGE, OBJECT, VAL, METHOD, VAR)
  val stableQualOrClass: ResolveTargets.ValueSet         = stableQualRef + CLASS

  val noPackagesClassCompletion: ResolveTargets.ValueSet = ValueSet(OBJECT, VAL, CLASS)
  val stableImportSelector: ResolveTargets.ValueSet      = ValueSet(OBJECT, VAL, VAR, METHOD, PACKAGE, CLASS)
  val stableClass: ResolveTargets.ValueSet               = ValueSet(CLASS)

  val stableClassOrObject: ResolveTargets.ValueSet = ValueSet(CLASS, OBJECT)
  val objectOrValue: ResolveTargets.ValueSet       = ValueSet(OBJECT, VAL)

  val refExprLastRef: ResolveTargets.ValueSet = ValueSet(OBJECT, VAL, VAR, METHOD)
  val refExprQualRef: ResolveTargets.ValueSet = refExprLastRef + PACKAGE

  val methodRef: ResolveTargets.ValueSet   = ValueSet(VAL, VAR, METHOD)
  val methodsOnly: ResolveTargets.ValueSet = ValueSet(METHOD)

  val valuesRef: ResolveTargets.ValueSet = ValueSet(VAL, VAR)
  val varsRef: ResolveTargets.ValueSet   = ValueSet(VAR)

  val packageRef: ResolveTargets.ValueSet = ValueSet(PACKAGE)

  val annotCtor: ResolveTargets.ValueSet = ValueSet(CLASS, ANNOTATION)
}
