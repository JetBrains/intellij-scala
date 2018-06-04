package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.util.containers.SmartHashSet
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * Nikolay.Tropin
  * 31-May-18
  */
private class UniqueNamesSet(nameUniquenessStrategy: NameUniquenessStrategy) extends SmartHashSet[ScalaResolveResult](nameUniquenessStrategy) {
  override def add(result: ScalaResolveResult): Boolean = {
    if (nameUniquenessStrategy.isValid(result)) super.add(result)
    else false
  }

  override def contains(obj: scala.Any): Boolean = obj match {
    case result: ScalaResolveResult if nameUniquenessStrategy.isValid(result) => super.contains(result)
    case _ => false
  }
}