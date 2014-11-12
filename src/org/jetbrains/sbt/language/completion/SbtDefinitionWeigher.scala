package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * @author Nikolay Obedin
 * @since 7/10/14
 */

class SbtDefinitionWeigher extends CompletionWeigher {
  def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = element match {
    case element: ScalaLookupItem if element.name == "???"   => 0
    case element: ScalaLookupItem if element.isSbtLookupItem =>
      if (element.isLocalVariable) 2 else 1
    case _ => 0
  }
}
