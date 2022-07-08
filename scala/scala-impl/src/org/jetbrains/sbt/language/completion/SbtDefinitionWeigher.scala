package org.jetbrains.sbt
package language
package completion

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

final class SbtDefinitionWeigher extends CompletionWeigher {

  override def weigh(element: LookupElement,
                     location: CompletionLocation): Comparable[_] = element match {
    case element: ScalaLookupItem
      if element.isSbtLookupItem &&
        element.getLookupString != "???" =>
      if (element.isLocalVariable) 2 else 1
    case _ => 0
  }
}
