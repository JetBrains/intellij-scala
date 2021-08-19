package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}

object SbtDependencyVersionWeigher extends LookupElementWeigher("sbtDependencyVersionWeigher", true, false){
  override def weigh(element: LookupElement): Comparable[_] = {
    var splitted = element.getLookupString.split("\\.").slice(0, 3)
    while (splitted.length < 3) splitted = splitted :+ "0"
    val pattern = "^\\d+".r
    var res: BigInt = 0
    splitted.foreach(s => {
      res += pattern.findFirstIn(s).getOrElse("0").toInt
      res *= 1000
    })
    res
  }
}
