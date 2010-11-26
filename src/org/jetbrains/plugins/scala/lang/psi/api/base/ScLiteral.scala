package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.{PsiLiteral, PsiLanguageInjectionHost}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost