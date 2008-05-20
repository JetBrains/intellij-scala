package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import com.intellij.psi._
import com.intellij.psi.search._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScParamClauses extends ScParameters with PsiParameterList {

  def params: Seq[ScParameter]

  // todo hack for applictiation running
  def getParameters = getParent match {
    case m: ScFunctionImpl => {
      if (m.isMainMethod) {
        val ps = new Array[PsiParameter](1)
        val p = new ScParameterImpl(params(0).getNode) {
          override def getType(): PsiType = new PsiArrayType(
          JavaPsiFacade.getInstance(m.getProject).getElementFactory.createTypeByFQClassName(
            "java.lang.String",
            GlobalSearchScope.allScope(m.getProject))
          )
        }
        ps(0) = p
        ps
      } else params.toArray
    }
    case _ => params.toArray
  }

  def getParametersAsString: String

}