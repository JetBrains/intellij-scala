package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

case class Parameters(newExpression: PsiNamedElement,
                      oldExpression: ScExpression,
                      project: Project,
                      editor: Editor,
                      elements: Seq[PsiNamedElement])
