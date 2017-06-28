package org.jetbrains.plugins.scala
package lang
package resolve

import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

object StdKinds {
  val stableQualRef = ValueSet(PACKAGE, OBJECT, VAL)
  val stableQualOrClass = stableQualRef + CLASS
  val noPackagesClassCompletion = ValueSet(OBJECT, VAL, CLASS)
  val stableImportSelector = ValueSet(OBJECT, VAL, VAR, METHOD, PACKAGE, CLASS)
  val stableClass = ValueSet(CLASS)

  val stableClassOrObject = ValueSet(CLASS, OBJECT)
  val objectOrValue = ValueSet(OBJECT, VAL)

  val refExprLastRef = ValueSet(OBJECT, VAL, VAR, METHOD)
  val refExprQualRef = refExprLastRef + PACKAGE

  val methodRef = ValueSet(VAL, VAR, METHOD)
  val methodsOnly = ValueSet(METHOD)

  val valuesRef = ValueSet(VAL, VAR)

  val varsRef = ValueSet(VAR)

  val packageRef = ValueSet(PACKAGE)

  val annotCtor = ValueSet(CLASS, ANNOTATION)
}
