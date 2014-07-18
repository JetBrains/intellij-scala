package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * Created by Nikolay Obedin on 7/10/14.
 */

class SbtDefinitionWeigher extends CompletionWeigher {
  def weigh(_element: LookupElement, location: CompletionLocation): Comparable[_] = _element match {
    case element: ScalaLookupItem if element.name == "???" => new Integer(0)
    case element: ScalaLookupItem if element.isSbtLookupItem =>
      if (element.isVariable)
        new Integer(2)
      else
        new Integer(1)
    case _ => null
  }
}
