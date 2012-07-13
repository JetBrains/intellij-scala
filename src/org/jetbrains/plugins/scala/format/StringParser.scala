package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

trait StringParser {
  def parse(element: PsiElement): Option[Seq[StringPart]]
}
