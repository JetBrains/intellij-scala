package org.jetbrains.plugins.scala.lang.psi.impl.expr

import _root_.scala.collection.mutable.HashMap
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.psi.util.PsiTreeUtil
import api.statements.ScTypeAlias
import _root_.scala.collection.immutable.Set
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import lexer.ScalaTokenTypes
import types._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import psi.ScDeclarationSequenceHolder
import api.expr._
import _root_.scala.collection.mutable.HashSet

/**
* @author ilyas
*/

class ScBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScBlock {

  override def toString: String = "BlockOfExpressions"

  override def getType = lastExpr match {
      case None => Unit
      case Some(e) => {
        def createSubst (oldVars : List[ScTypeVariable], newVars : List[ScTypeVariable]) = {
          var s = ScSubstitutor.empty
          for ((tv, tv1) <- oldVars zip newVars) {
            s = s + (tv, tv1)
          }
          s
        }

        def newTypeVar(v : ScTypeVariable) : ScTypeVariable = {
          val i1 = v.inner.map {newTypeVar _}
          var s = createSubst(v.inner, i1)
          new ScTypeVariable(v.name, i1, s.subst(v.lower), s.subst(v.upper))
        }

        val m = new HashMap[String, ScExistentialArgument]
        def existize (t : ScType) : ScType = t match {
          case ScFunctionType(ret, params) => new ScFunctionType(existize(ret), params.map {existize _})
          case ScTupleType(comps) => new ScTupleType(comps.map {existize _})
          case ScDesignatorType(des) if PsiTreeUtil.isAncestor(this, des, true) => des match {
            case clazz : ScClass => {
              val oldVars = clazz.typeParameters.map{tp => ScalaPsiManager.typeVariable(tp)}.toList
              val newVars = oldVars.map{newTypeVar _}
              val s = createSubst(oldVars, newVars)
              val t = s.subst(existize(leastClassType(clazz)))
              m.put(clazz.name, new ScExistentialArgument(clazz.name, newVars, t, t))
              new ScTypeAliasType(clazz.name, newVars, t, t) //to be substed by name
            }
            case obj : ScObject => {
              val t = existize(leastClassType(obj))
              m.put(obj.name, new ScExistentialArgument(obj.name, Nil, t, t))
              new ScTypeAliasType(obj.name, Nil, t, t) //to be substed by name
            }
            case typed : ScTyped => {
              val t = existize(typed.calcType); new ScTypeVariable(typed.name, Nil, t, t)
              m.put(typed.name, new ScExistentialArgument(typed.name, Nil, t, t))
              new ScTypeAliasType(typed.name, Nil, t, t) //to be substed by name
            }
          }
          case ScProjectionType(p, name) => new ScProjectionType(existize(p), name)
          case ScCompoundType(comps, decls, types) => new ScCompoundType(comps.map {existize _}, decls, types)
          case ScParameterizedType (des, typeArgs) =>
            new ScParameterizedType(existize(des), typeArgs.map {existize _})
          case ScExistentialArgument(name, args, lower, upper) => new ScExistentialArgument(name, args, existize(lower), existize(upper))
          case ex@ScExistentialType(q, wildcards) => {
             new ScExistentialType(existize(q), wildcards.map {ex =>
                     new ScExistentialArgument(ex.name, ex.args, existize(ex.lowerBound), existize(ex.upperBound))})
          }
          case singl : ScSingletonType => existize(singl.pathType)
          case _ => t
        }
        val t = existize(e.getType)
        if (m.size == 0) t else new ScExistentialType(t, m.values.toList)
      }
    }
  private def leastClassType(t : ScTypeDefinition) = {
    val (holders, aliases) = t.extendsBlock.templateBody match {
      case Some(b) => (b.holders, b.aliases)
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = t.extendsBlock.superTypes
    if (superTypes.length > 1 || !holders.isEmpty || !aliases.isEmpty) {
      new ScCompoundType(superTypes, holders, aliases)
    } else superTypes(0)
  }

  def lastStatement: Option[PsiElement] = {
    def testChild(child: PsiElement): Boolean = child match {
      case null => false
      case _: PsiWhiteSpace => true
      case _: PsiComment => true
      case _ => {
        child.getNode.getElementType match {
          case ScalaTokenTypes.tRBRACE => true
          case ScalaTokenTypes.tLBRACE => true
          case ScalaTokenTypes.tLINE_TERMINATOR => true
          case _ => false
        }
      }
    }
    var child = this.getLastChild
    while (testChild(child)) child = child.getPrevSibling
    child match {
      case null => None
      case x => Some(x)
    }
  }
}