package org.jetbrains.plugins.scala.runner

import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiFile, PsiIdentifier, PsiManager, PsiNameIdentifierOwner, ResolveState}
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.runner.Scala3MainMethodSyntheticClass.MainMethodParameters

private final class Scala3MainMethodSyntheticClass(
  psiManager: PsiManager,
  containingFile: PsiFile,
  qualifiedName: String,
  val parameters: MainMethodParameters,
) extends LightElement(psiManager, ScalaLanguage.INSTANCE)
  with PsiNameIdentifierOwner
  with PsiClassAdapter
  with PsiClassFake {

  override val getName: String = qualifiedName.lastIndexOf('.') match {
    case -1 => qualifiedName // in root package
    case idx => qualifiedName.substring(idx + 1)
  }

  //noinspection UnstableApiUsage
  override def getQualifiedName: String = qualifiedName

  override def getText = ""
  override def getNameIdentifier: PsiIdentifier = null

  override def getContainingFile: PsiFile = containingFile
  override def getContext: PsiFile = containingFile

  override def toString = s"synthetic class for scala @main method: $qualifiedName"

  override def setName(newName: String): PsiElement = throw new IncorrectOperationException("nonphysical element")
  override def copy = throw new IncorrectOperationException("nonphysical element")
  override def accept(v: PsiElementVisitor): Unit = throw new IncorrectOperationException("should not call")

  override def processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    // NOTE: we probably need add some fake psi file with all fake @main method classes declarations
    // strictly speaking ScalaMainMethodSyntheticClass can't declare itself, but this solution works...
    processor.execute(this, state)
    false
  }
}

private object Scala3MainMethodSyntheticClass {

  sealed trait MainMethodParameters

  object MainMethodParameters {
    object Default extends MainMethodParameters // (args: String*)
    case class Custom(parameterNames: Seq[CustomParameter]) extends MainMethodParameters

    case class CustomParameter(name: String, typ: String, isVararg: Boolean)
  }
}
