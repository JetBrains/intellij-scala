package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaMemberInfoBase

final class ScalaMemberInfo(member: ScNamedElement) extends ScalaMemberInfoBase(member: ScNamedElement)
