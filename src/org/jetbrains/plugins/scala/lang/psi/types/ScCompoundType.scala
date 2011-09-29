package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements._
import collection.mutable.{ListBuffer, HashMap}
import result.{TypingContext, Failure}
import com.intellij.psi.PsiElement

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder],
                          typeDecls: Seq[ScTypeAlias], subst: ScSubstitutor) extends ValueType {
  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitCompoundType(this)
  }

  private[types] def this(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder],
           typeDecls: Seq[ScTypeAlias], subst: ScSubstitutor, signatureMap: HashMap[Signature, ScType],
            typesMap: HashMap[String, (ScType, ScType)], problems: List[Failure]) {
    this(components, decls, typeDecls, subst)
    isInitialized = true
    signatureMapVal ++= signatureMap
    typesVal ++= typesMap
    problemsVal ++= problems
  }

  type Bounds = Pair[ScType, ScType]

  //compound types are checked by checking the set of signatures in their refinements
  val signatureMapVal: HashMap[Signature, ScType] = new HashMap[Signature, ScType] {
    override def elemHashCode(s : Signature) = s.name.hashCode * 31 + s.paramLength
  }
  private val typesVal = new HashMap[String, Bounds]
  private val problemsVal: ListBuffer[Failure] = new ListBuffer

  def problems: ListBuffer[Failure] = {
    init()
    problemsVal
  }

  def types = {
    init()
    typesVal
  }

  def signatureMap = {
    init()
    signatureMapVal
  }

  private var isInitialized = false
  private def init() {
    synchronized {
      if (isInitialized) return
      isInitialized = true

      for (typeDecl <- typeDecls) {
        typesVal += ((typeDecl.name, (typeDecl.lowerBound.getOrNothing, typeDecl.upperBound.getOrAny)))
      }


      for (decl <- decls) {
        decl match {
          case fun: ScFunction =>
            signatureMapVal += ((new PhysicalSignature(fun, subst), fun.getType(TypingContext.empty).getOrAny))
          case varDecl: ScVariable => {
            varDecl.typeElement match {
              case Some(te) => for (e <- varDecl.declaredElements) {
                val varType = te.getType(TypingContext.empty(varDecl.declaredElements))
                varType match {case f@Failure(_, _) => problemsVal += f; case _ =>}
                signatureMapVal += ((new Signature(e.name, Stream.empty, 0, subst, Some(e)), varType.getOrAny))
                signatureMapVal += ((new Signature(e.name + "_=", Stream(varType.getOrAny), 1, subst, Some(e)), Unit)) //setter
              }
              case None =>
            }
          }
          case valDecl: ScValue => valDecl.typeElement match {
            case Some(te) => for (e <- valDecl.declaredElements) {
              val valType = te.getType(TypingContext.empty(valDecl.declaredElements))
              valType match {case f@Failure(_, _) => problemsVal += f; case _ =>}
              signatureMapVal += ((new Signature(e.name, Stream.empty, 0, subst, Some(e)), valType.getOrAny))
            }
            case None =>
          }
        }
      }
    }
  }

  def typesMatch(types1 : HashMap[String, Bounds], subst1: ScSubstitutor,
                         types2 : HashMap[String, Bounds], subst2: ScSubstitutor) : Boolean = {
    if (types1.size != types.size) return false
    else {
      for ((name, bounds1) <- types1) {
        types2.get(name) match {
          case None => return false
          case Some (bounds2) => if (!(subst1.subst(bounds1._1) equiv subst2.subst(bounds2._1)) ||
                                     !(subst1.subst(bounds1._2) equiv subst2.subst(bounds2._2))) return false
        }
      }
      true
    }
  }

  override def removeAbstracts = ScCompoundType(components.map(_.removeAbstracts), decls, typeDecls, subst)

  override def recursiveUpdate(update: ScType => (Boolean, ScType)): ScType = {
    update(this) match {
      case (true, res) => res
      case _ =>
        ScCompoundType(components.map(_.recursiveUpdate(update)), decls, typeDecls, subst)
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        ScCompoundType(components.map(_.recursiveVarianceUpdate(update, variance)), decls, typeDecls, subst)
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case r: ScCompoundType => {
        if (r == this) return (true, undefinedSubst)
        if (components.length != r.components.length) return (false, undefinedSubst)
        val list = components.zip(r.components)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next
          val t = Equivalence.equivInner(w1, w2, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }

        if (signatureMap.size != r.signatureMap.size) return (false, undefinedSubst)

        val iterator2 = signatureMap.iterator
        while (iterator2.hasNext) {
          val (sig, t) = iterator2.next
          r.signatureMap.get(sig) match {
            case None => false
            case Some(t1) => {
              val f = Equivalence.equivInner(t, t1, undefinedSubst, falseUndef)
              if (!f._1) return (false, undefinedSubst)
              undefinedSubst = f._2
            }
          }
        }

        val types1 = types
        val subst1 = subst
        val types2 = r.types
        val subst2 = r.subst
        if (types1.size != types.size) return (false, undefinedSubst)
        else {
          val types1iterator = types1.iterator
          while (types1iterator.hasNext) {
            val (name, bounds1) = types1iterator.next
            types2.get(name) match {
              case None => return (false, undefinedSubst)
              case Some (bounds2) => {
                var t = Equivalence.equivInner(subst1.subst(bounds1._1), subst2.subst(bounds2._1), undefinedSubst, falseUndef)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = Equivalence.equivInner(subst1.subst(bounds1._2), subst2.subst(bounds2._2), undefinedSubst, falseUndef)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
              }
            }
          }
          (true, undefinedSubst)
        }
      }
      case _ => (false, undefinedSubst)
    }
  }
}