package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.lang.PsiBuilder
/**
 * User: Dmitry.Krasilschikov
 * Date: 19.10.2006
 * Time: 17:35:24
 */
abstract class Constr {
   def parse (builder : PsiBuilder ): Unit
  //def getBuilder = {builder}

}
