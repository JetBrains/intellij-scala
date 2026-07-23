package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExportStmtStub, ScImportOrExportStmtStub, ScImportStmtStub}

abstract sealed class ScImportOrExportStmtStubImpl[T <: ScImportOrExportStmt](
  parent: StubElement[_ <: PsiElement],
  elementType: IElementType,
  override val importText: String
) extends StubBase[T](parent, elementType)
  with ScImportOrExportStmtStub[T]

class ScImportStmtStubImpl(
  parent: StubElement[_ <: PsiElement],
  elementType: IElementType,
  importText: String
) extends ScImportOrExportStmtStubImpl[ScImportStmt](parent, elementType, importText) with ScImportStmtStub

class ScExportStmtStubImpl(
  parent: StubElement[_ <: PsiElement],
  elementType: IElementType,
  importText: String,
  override val isTopLevel: Boolean,
  override val topLevelQualifier: Option[String]
) extends ScImportOrExportStmtStubImpl[ScExportStmt](parent, elementType, importText)
  with ScExportStmtStub
