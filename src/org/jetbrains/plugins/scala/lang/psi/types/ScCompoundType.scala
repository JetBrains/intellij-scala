package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements._
import result.{TypingContext, Failure}
import collection.mutable.{ListBuffer, HashMap}
import com.intellij.psi.PsiClass
import extensions.toPsiClassExt
import lang.psi
import collection.mutable

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder],
                          typeDecls: Seq[ScTypeAlias], subst: ScSubstitutor) extends ValueType {
  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitCompoundType(this)
  }

  private[types] def this(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder],
           typeDecls: Seq[ScTypeAlias], subst: ScSubstitutor, signatureMap: mutable.HashMap[Signature, Suspension[ScType]],
            typesMap: mutable.HashMap[String, (ScType, ScType)], problems: List[Failure]) {
    this(components, decls, typeDecls, subst)
    isInitialized = true
    signatureMapVal ++= signatureMap
    typesVal ++= typesMap
    problemsVal ++= problems
  }

  type Bounds = Pair[ScType, ScType]

  //compound types are checked by checking the set of signatures in their refinements
  @volatile
  var signatureMapVal: mutable.HashMap[Signature, Suspension[ScType]] = new mutable.HashMap[Signature, Suspension[ScType]] {
    override def elemHashCode(s : Signature) = s.name.hashCode * 31 + {
      val length = s.paramLength
      if (length.sum == 0) List(0).hashCode()
      else length.hashCode()
    }
  }
  @volatile
  private var typesVal = new mutable.HashMap[String, Bounds]
  @volatile
  private var problemsVal: ListBuffer[Failure] = new ListBuffer

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

  @volatile
  private var isInitialized = false
  private def init() {
    if (isInitialized) return

    val signatureMapVal: mutable.HashMap[Signature, Suspension[ScType]] = new mutable.HashMap[Signature, Suspension[ScType]] {
      override def elemHashCode(s : Signature) = s.name.hashCode * 31 + {
        val length = s.paramLength
        if (length.sum == 0) List(0).hashCode()
        else length.hashCode()
      }
    }
    val typesVal = new mutable.HashMap[String, Bounds]
    val problemsVal: ListBuffer[Failure] = new ListBuffer

    for (typeDecl <- typeDecls) {
      typesVal += ((typeDecl.name, (typeDecl.lowerBound.getOrNothing, typeDecl.upperBound.getOrAny)))
    }


    for (decl <- decls) {
      decl match {
        case fun: ScFunction =>
          signatureMapVal += ((new PhysicalSignature(fun, subst), new Suspension(() => fun.getType(TypingContext.empty).getOrAny)))
        case varDecl: ScVariable => {
          varDecl.typeElement match {
            case Some(te) => for (e <- varDecl.declaredElements) {
              val varType = te.getType(TypingContext.empty(varDecl.declaredElements))
              varType match {case f@Failure(_, _) => problemsVal += f; case _ =>}
              signatureMapVal += ((new Signature(e.name, Stream.empty, 0, subst, Some(e)), new Suspension(() => varType.getOrAny)))
              signatureMapVal += ((new Signature(e.name + "_=", Stream(varType.getOrAny), 1, subst, Some(e)), psi.types.Unit)) //setter
            }
            case None =>
          }
        }
        case valDecl: ScValue => valDecl.typeElement match {
          case Some(te) => for (e <- valDecl.declaredElements) {
            val valType = te.getType(TypingContext.empty(valDecl.declaredElements))
            valType match {case f@Failure(_, _) => problemsVal += f; case _ =>}
            signatureMapVal += ((new Signature(e.name, Stream.empty, 0, subst, Some(e)), new Suspension(() => valType.getOrAny)))
          }
          case None =>
        }
      }
    }

    this.signatureMapVal = signatureMapVal
    this.typesVal = typesVal
    this.problemsVal = problemsVal

    isInitialized = true
  }

  override def removeAbstracts = ScCompoundType(components.map(_.removeAbstracts), decls, typeDecls, subst)

  import collection.immutable.{HashSet => IHashSet}

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: IHashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ =>
        init()
        new ScCompoundType(components.map(_.recursiveUpdate(update, visited + this)), decls, typeDecls, subst, signatureMapVal.map {
          case (signature: Signature, tp) => (signature, new Suspension[ScType](() => subst.subst(tp.v).recursiveUpdate(update, visited + this)))
        }, typesVal.map {
          case (s: String, (tp1, tp2)) => (s, (subst.subst(tp1).recursiveUpdate(update, visited + this), subst.subst(tp2).recursiveUpdate(update, visited + this)))
        }, problemsVal.toList)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        init()
        new ScCompoundType(components.map(_.recursiveVarianceUpdateModifiable(newData, update, variance)), decls, typeDecls, subst, signatureMapVal.map {
          case (signature: Signature, tp) => (signature, new Suspension[ScType](() => tp.v.recursiveVarianceUpdateModifiable(newData, update, 1)))
        }, typesVal.map {
          case (s: String, (tp1, tp2)) => (s, (tp1.recursiveVarianceUpdateModifiable(newData, update, 1), tp2.recursiveVarianceUpdateModifiable(newData, update, 1)))
        }, problemsVal.toList)
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
          val (w1, w2) = iterator.next()
          val t = Equivalence.equivInner(w1, w2, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }

        if (signatureMap.size != r.signatureMap.size) return (false, undefinedSubst)

        val iterator2 = signatureMap.iterator
        while (iterator2.hasNext) {
          val (sig, t) = iterator2.next()
          r.signatureMap.get(sig) match {
            case None => false
            case Some(t1) => {
              val f = Equivalence.equivInner(t.v, t1.v, undefinedSubst, falseUndef)
              if (!f._1) return (false, undefinedSubst)
              undefinedSubst = f._2
            }
          }
        }

        val types1 = types
        val subst1 = subst
        val types2 = r.types
        val subst2 = r.subst
        if (types1.size != types.size) (false, undefinedSubst)
        else {
          val types1iterator = types1.iterator
          while (types1iterator.hasNext) {
            val (name, bounds1) = types1iterator.next()
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
      case _ =>
        if (decls.length == 0 && typeDecls.length == 0) {
          val filtered = components.filter {
            case psi.types.Any => false
            case psi.types.AnyRef =>
              if (!r.conforms(psi.types.AnyRef)) return (false, undefinedSubst)
              false
            case ScDesignatorType(obj: PsiClass) if obj.qualifiedName == "java.lang.Object" =>
              if (!r.conforms(psi.types.AnyRef)) return (false, undefinedSubst)
              false
            case _ => true
          }
          if (filtered.length == 1) Equivalence.equivInner(filtered(0), r, undefinedSubst, falseUndef)
          else (false, undefinedSubst)
        } else (false, undefinedSubst)

    }
  }
}