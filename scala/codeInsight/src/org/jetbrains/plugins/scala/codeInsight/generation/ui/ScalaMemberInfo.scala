package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberInfoBase

/**
 * Nikolay.Tropin
 * 8/20/13
 */
final class ScalaMemberInfo(member: ScNamedElement) extends ScalaMemberInfoBase(member: ScNamedElement)
