package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.PsiDocTag
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocTag extends ScalaPsiElement with PsiDocTag {
  /**
   * @inheritdoc
   */
  override def getName: String

  /**
   * @inheritdoc
   *
   * @note The name element also includes the `@` symbol.
   *       So the name elements text doesn't equal to the [[getName]]
   */
  override def getNameElement: PsiElement
}