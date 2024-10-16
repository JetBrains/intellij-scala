package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern

trait ScBindingPatternStub[P <: ScBindingPattern] extends NamedStub[P]
