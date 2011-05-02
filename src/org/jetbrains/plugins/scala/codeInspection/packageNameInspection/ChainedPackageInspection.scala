package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import config.ScalaFacet
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language

class ChainedPackageInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Chained Package Clause Inspection"

  def getShortName: String = "Chained Package Clause"

  override def isEnabledByDefault: Boolean = true

  @Language("HTML")
  override val getStaticDescription =
"""Files within a module should consistently use <a href="http://www.scala-lang.org/docu/files/package-clauses/packageclauses.html">Chained Package Clauses</a>
 starting with the base package configured in the Scala Facet.
"""

  override def getID: String = "ScalaChainedPackageClause"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val basePackage: Option[String] = for {
      m <- Option(ScalaPsiUtil.getModule(file))
      facet <- ScalaFacet.findIn(m)
      p <- facet.basePackage
    } yield p
    (file, basePackage) match {
      case (sf: ScalaFile, Some(base)) if !sf.isScriptFile() =>
        sf.packagings.toList match {
          case p0 :: ps if p0.getPackageName != base && p0.getPackageName.startsWith(base) =>
            val fixes = Array[LocalQuickFix](new UseChainedPackageQuickFix(sf, base))
            val range: TextRange = sf.getPackagingRange
            val problem = manager.createProblemDescriptor(file, range, "Package declaration could use chained package clauses",
              ProblemHighlightType.WEAK_WARNING, false, fixes: _*)
            Array(problem)
          case _ => Array()
        }
      case _ => Array()
    }
  }
}

class UseChainedPackageQuickFix(file: ScalaFile, basePackage: String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    val packageName = file.packageName
    file.setPackageName(packageName)
  }

  def getFamilyName: String = "Use chained package clauses"

  def getName: String = "Use chained package clauses: package %s; package ...".format(basePackage)
}