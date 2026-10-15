package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.IndexSink
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAlias, ScGivenDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaStubIndexKeys.GIVEN_KEY

trait ScGivenStub {
  def isGiven: Boolean

  def givenClassNames: Array[String]

  def indexGivens(sink: IndexSink): Unit = givenClassNames.foreach(sink.occurrence(GIVEN_KEY, _))
}

object ScGivenStub {
  def givenAliasClassNames(alias: ScGivenAlias): Array[String] =
    alias.returnTypeElement.toArray.flatMap(classNames)

  def givenDefinitionClassNames(givenDef: ScGivenDefinition): Array[String] = for {
    templateParent <- givenDef.extendsBlock.templateParents.toArray
    typeElement <- templateParent.typeElements
    className <- classNames(typeElement)
  } yield className
}
