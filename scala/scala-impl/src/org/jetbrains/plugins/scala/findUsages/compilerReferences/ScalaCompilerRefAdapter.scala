package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util

import com.intellij.compiler.backwardRefs.SearchId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.{PsiClass, PsiFunctionalExpression}
import org.jetbrains.plugins.scala.ScalaFileType

private class ScalaCompilerRefAdapter extends JavaCompilerRefAdapterCompat {
  override def getFileTypes: util.Set[FileType] =
    new util.HashSet[FileType](
      util.Arrays.asList(ScalaFileType.INSTANCE)
    )

  override protected def directInheritorCandidatesInFile(
    internalNames: Array[SearchId],
    file: PsiFileWithStubSupport
  ): Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override protected def funExpressionsInFile(
    funExpressions: Array[SearchId],
    file: PsiFileWithStubSupport
  ): Array[PsiFunctionalExpression] = PsiFunctionalExpression.EMPTY_ARRAY
}
