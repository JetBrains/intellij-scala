package org.jetbrains.sbt
package annotator.quickfix

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.sbt.resolvers.{SbtResolverIndexesManager, SbtResolverUtils}

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */
class SbtUpdateResolverIndexesQuickFix extends AbstractIntentionAction {

  def getText = "Update project resolvers' indexes"

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    val resolvers = SbtResolverUtils.getProjectResolvers(Option(file))
    val indexManager = SbtResolverIndexesManager()
    indexManager.update(resolvers)
  }
}
