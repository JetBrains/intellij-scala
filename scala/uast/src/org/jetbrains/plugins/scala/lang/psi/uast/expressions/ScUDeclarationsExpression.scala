package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.uast.{
  UAnnotation,
  UDeclaration,
  UDeclarationsExpression,
  UDeclarationsExpressionAdapter
}

import scala.collection.JavaConverters._

/**
  * [[ScValueOrVariable]] adapter for the [[UDeclarationsExpression]]
  *
  * @param scElement Scala PSI element representing local val/var declaration
  */
class ScUDeclarationsExpression(
  override protected val scElement: ScValueOrVariable,
  override protected val parent: LazyUElement
) extends UDeclarationsExpressionAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  override def getDeclarations: util.List[UDeclaration] = {
    val declarations = for {
      elem <- scElement.declaredElements
      refPattern <- Option(elem.nameId.getParent)
      // does not specify parent as `this` here to allow `UField`
      // to skip this uDeclaration as a parent and return `UClass` instead
      valOrVar <- refPattern.convertWithParentTo[UDeclaration]()
    } yield valOrVar

    declarations.asJava
  }

  // escapes looping caused by the default implementation
  override def getUAnnotations: util.List[UAnnotation] =
    JavaCollectionsCommon.newEmptyJavaList
}
