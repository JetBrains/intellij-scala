package org.jetbrains.plugins.scala.compiler.references.indices

import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiField, PsiFile, PsiFunctionalExpression, PsiMember, PsiMethod}
import org.jetbrains.jps.backwardRefs.{CompilerRef, NameEnumerator}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import java.util

object ScalaCompilerRefAdapter extends JavaCompilerRefAdapterCompat {

  override def getFileTypes: util.Set[FileType] =
    new util.HashSet[FileType](
      util.Arrays.asList(ScalaFileType.INSTANCE)
    )

  private[this] def tryEnumerate(enumerator: NameEnumerator, name: String): Option[Int] =
    enumerator.tryEnumerate(name).toOption.filter(_ != 0)

  private[this] def toCompilerRef(element: PsiElement, enumerator: NameEnumerator): Option[CompilerRef] = {
    def ownerId(member: PsiMember): Option[Int] =
      for {
        owner     <- member.containingClass.toOption
        ownerName <- ClassUtil.getJVMClassName(owner).toOption
        id        <- tryEnumerate(enumerator, ownerName)
      } yield id

    def fieldLikeRef(member: PsiMember): Option[CompilerRef] =
      for {
        owner <- ownerId(member)
        name  <- tryEnumerate(enumerator, member.getName)
      } yield new CompilerRef.JavaCompilerFieldRef(owner, name)

    element match {
      case cParam: ScClassParameter                                                    => fieldLikeRef(cParam)
      case (pat: ScBindingPattern) & inNameContext(v: ScValueOrVariable) if !v.isLocal => fieldLikeRef(pat)
      case field: PsiField                                                             => fieldLikeRef(field)
      case method: PsiMethod =>
        def parametersCount(m: PsiMethod): Int = m match {
          case fn: ScFunction => fn.typeParameters.flatMap(_.contextBound).length + fn.parameters.length
          case _              => m.getParameterList.getParametersCount
        }

        for {
          owner <- ownerId(method)
          name  <- tryEnumerate(enumerator, method.name)
        } yield new CompilerRef.JavaCompilerMethodRef(owner, name, parametersCount(method))
      case aClass: PsiClass =>
        for {
          name <- ClassUtil.getJVMClassName(aClass).toOption
          id   <- tryEnumerate(enumerator, name)
        } yield new CompilerRef.JavaCompilerClassRef(id)
      case _ => None
    }
  }

  override def asCompilerRef(element: PsiElement, enumerator: NameEnumerator): CompilerRef =
    toCompilerRef(bytecodeElement(element), enumerator).orNull

  override protected def directInheritorCandidatesInFile(
    internalNames: Array[SearchId],
    file:          PsiFileWithStubSupport
  ): Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override protected def funExpressionsInFile(
    funExpressions: Array[SearchId],
    file:           PsiFileWithStubSupport
  ): Array[PsiFunctionalExpression] = PsiFunctionalExpression.EMPTY_ARRAY

  private def bytecodeElement(element: PsiElement): PsiElement =
    inReadAction(element match {
      case HasSyntheticGetter(getter) => getter
      case _                          => element
    })

  private[this] class BytecodeMethod(e: ScTypedDefinition, name: String) extends FakePsiMethod(e, None, name) {
    override def params: Array[Parameter]   = Array.empty
    override def retType: ScType            = e.`type`().getOrAny
    override def getContainingFile: PsiFile = ScalaPsiUtil.fileContext(e)
  }

  object HasSyntheticGetter {
    private[this] def syntheticGetter(e: ScTypedDefinition): FakePsiMethod =
      new BytecodeMethod(e, e.name)

    private[this] def isPrivateThis(mod: ScModifierListOwner): Boolean =
      mod.getModifierList.accessModifier.exists(m => m.isPrivate && m.isThis)

    def unapply(e: PsiElement): Option[FakePsiMethod] = e match {
      case c: ScClassParameter if !c.isPrivateThis => syntheticGetter(c).toOption
      case (bp: ScBindingPattern) & inNameContext(v: ScValueOrVariable) if !v.isLocal && !isPrivateThis(v) =>
        syntheticGetter(bp).toOption
      case _ => None
    }
  }
}
