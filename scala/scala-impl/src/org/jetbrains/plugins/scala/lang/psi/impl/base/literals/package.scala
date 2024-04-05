package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.annotations.Nullable

import java.lang.{Long => JLong}

package object literals {
  private def lowercaseLiteralPrefix(text: String): String = {
    if (text.length >= 2) {
      text(1) match {
        case 'X' => text.patch(1, "x", 1)
        case 'B' => text.patch(1, "b", 1)
        case _ => text
      }
    } else text
  }

  @Nullable
  def parseInteger(text: String): Integer = {
    PsiLiteralUtil.parseInteger(lowercaseLiteralPrefix(text))
  }

  @Nullable
  def parseLong(text: String): JLong = {
    PsiLiteralUtil.parseLong(lowercaseLiteralPrefix(text))
  }
}
