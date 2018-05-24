package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util

import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiField, PsiFile, PsiFunctionalExpression, PsiMember, PsiMethod}
import org.jetbrains.jps.backwardRefs.{CompilerRef, NameEnumerator}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

private class ScalaCompilerRefAdapter extends JavaCompilerRefAdapterCompat {
  import ScalaCompilerRefAdapter._

  override def getFileTypes: util.Set[FileType] =
    new util.HashSet[FileType](
      util.Arrays.asList(ScalaFileType.INSTANCE)
    )

  private[this] def tryEnumerate(enum: NameEnumerator, name: String): Option[Int] =
    enum.tryEnumerate(name).toOption.filter(_ != 0)

  private[this] def toCompilerRef(element: PsiElement, enumerator: NameEnumerator): Option[CompilerRef] = {
    def ownerId(member: PsiMember): Option[Int] =
      for {
        owner     <- member.containingClass.toOption
        ownerName <- ClassUtil.getJVMClassName(owner).toOption
        id        <- tryEnumerate(enumerator, ownerName)
      } yield id

    referencingBytecodeElement(element) match {
      case field: PsiField =>
        for {
          owner <- ownerId(field)
          name  <- tryEnumerate(enumerator, field.name)
        } yield new CompilerRef.JavaCompilerFieldRef(owner, name)
      case method: PsiMethod =>
        for {
          owner <- ownerId(method)
          name  <- tryEnumerate(enumerator, method.name)
        } yield new CompilerRef.JavaCompilerMethodRef(owner, name, method.getParameterList.getParametersCount)
      case aClass: PsiClass =>
        for {
          name <- ClassUtil.getJVMClassName(aClass).toOption
          id   <- tryEnumerate(enumerator, name)
        } yield new CompilerRef.JavaCompilerClassRef(id)
    }
  }

  override def asCompilerRef(element: PsiElement, enumerator: NameEnumerator): CompilerRef =
    toCompilerRef(element, enumerator).orNull

  override protected def directInheritorCandidatesInFile(
    internalNames: Array[SearchId],
    file:          PsiFileWithStubSupport
  ): Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override protected def funExpressionsInFile(
    funExpressions: Array[SearchId],
    file:           PsiFileWithStubSupport
  ): Array[PsiFunctionalExpression] = PsiFunctionalExpression.EMPTY_ARRAY
}

object ScalaCompilerRefAdapter {
  private[findUsages] def referencingBytecodeElement(element: PsiElement): PsiElement =
    inReadAction(element match {
      case hasSyntheticGetter(getter) => getter
      case _                          => element
    })

  private[this] class BytecodeMethod(e: ScTypedDefinition, name: String)
      extends FakePsiMethod(
        e,
        name,
        Array.empty,
        e.`type`().getOrAny,
        Function.const(false)
      )

  object hasSyntheticGetter {
    private[this] def syntheticGetterMethod(e: ScTypedDefinition): FakePsiMethod =
      new BytecodeMethod(e, e.name) {
        override def getContainingFile: PsiFile = e.getContainingFile
      }

    def unapply(e: PsiElement): Option[FakePsiMethod] = e match {
      case c: ScClassParameter if !c.isPrivateThis => Option(syntheticGetterMethod(c))
      case (bp: ScBindingPattern) && ScalaPsiUtil.inNameContext(v: ScValueOrVariable)
          if !v.isLocal && !v.isPrivateThis =>
        Option(syntheticGetterMethod(bp))
      case _ => None
    }
  }
}
