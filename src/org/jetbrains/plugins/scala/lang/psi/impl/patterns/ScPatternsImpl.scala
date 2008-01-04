package org.jetbrains.plugins.scala.lang.psi.impl.patterns
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi._
import com.intellij.psi.tree._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._


/**
*  Sequence of patterns
*/
trait ScPatterns extends ScalaPsiElement

trait ScPattern extends ScPatterns with ScReferenceIdContainer{

  /**
  *  Returns references to binded values
  */
  def getNames = {
    val children = allChildrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET)
    if (children != null) {
      children.toList
    } else {
      Nil: List[ScReferenceId]
    }
  }
}

trait ScPattern1 extends ScPattern

trait ScPattern2 extends ScPattern1

trait ScPattern3 extends ScPattern2

case class ScTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Tuple pattern"
}

case class ScSimplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPattern2 {
  override def toString: String = "Simple pattern"
}

case class ScPattern1Impl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPattern1 {
  override def toString: String = "Common pattern"
}

case class ScPattern2Impl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPattern2 {
  override def toString: String = "Binding pattern"

  override def copy(): PsiElement = ScalaPsiElementFactory.createPattern2FromText(this.getText, this.getManager).getPsi

  def getVarid: PsiElement = getChild(ScalaTokenTypes.tIDENTIFIER)
}

case class ScPattern3Impl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPattern3 {
  override def toString: String = "Pattern 3"
}

case class ScPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPattern {
  override def toString: String = "Composite pattern"
}

case class ScPatternsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatterns {
  override def toString: String = "Argument patterns"
}

case class ScWildPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Wild pattern"
}


/**
*   Case clause
*
*/
case class ScCaseClauseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with IfElseIndent with LocalContainer{

  val PATTERN_SET = TokenSet.create(Array(ScalaElementTypes.SIMPLE_PATTERN,
              ScalaElementTypes.SIMPLE_PATTERN1,
              ScalaElementTypes.PATTERN3,
              ScalaElementTypes.PATTERN2,
              ScalaElementTypes.PATTERN1,
              ScalaElementTypes.PATTERN))


  import com.intellij.psi.scope._
  override def getVariable(processor: PsiScopeProcessor,
          substitutor: PsiSubstitutor): Boolean = {
    for (val pat <- childrenOfType[ScPattern](PATTERN_SET); pat.getTextOffset <= varOffset) {
      if (pat != null && ! processor.execute(pat, substitutor)) {
        return false
      }
    }
    return true
  }

  import com.intellij.psi.scope._
  override def processDeclarations(processor: PsiScopeProcessor,
          substitutor: PsiSubstitutor,
          lastParent: PsiElement,
          place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve.processors._

    if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){
        this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
      getVariable(processor, substitutor)
    } else true
  }

  override def toString: String = "Case Clause"
}

case class ScCaseClausesImpl(node: ASTNode) extends ScalaPsiElementImpl(node){
  override def toString: String = "Case Clauses"
}
