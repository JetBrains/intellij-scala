package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi.PsiElement

import scala.collection.Set

class ImplicitCompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                                  override val getPlace: PsiElement)
  extends CompletionProcessor(kinds, getPlace)
