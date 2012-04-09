package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi.PsiReference
import lang.resolve.ResolvableReferenceElement
import java.util.Collection
import collection.JavaConverters.{asJavaCollectionConverter, iterableAsScalaIterableConverter}

object ScalaRenameUtil {
  def filterAliasedReferences(allReferences: Collection[PsiReference]): Collection[PsiReference] = {
    val filtered = allReferences.asScala.filter {
      case resolvableReferenceElement: ResolvableReferenceElement =>
        resolvableReferenceElement.bind() match {
          case Some(result) =>
            val renamed = result.isRenamed
            renamed.isEmpty
          case None => true
        }
      case _ => true
    }
    filtered.asJavaCollection
  }
}