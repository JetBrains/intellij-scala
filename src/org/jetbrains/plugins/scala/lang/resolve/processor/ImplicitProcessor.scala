package org.jetbrains.plugins.scala.lang.resolve.processor

import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

/**
 * @author Alexander Podkhalyuzin
 */

/**
 * This class mark processor that only implicit object important among all PsiClasses
 */
abstract class ImplicitProcessor(kinds: Set[Value]) extends BaseProcessor(kinds)