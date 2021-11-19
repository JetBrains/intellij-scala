package org.jetbrains.plugins.scala.traceLogger.macros

import scala.language.experimental.macros

//noinspection ScalaUnusedSymbol
trait LoggingMacros {
  final def log(msg: String, args: Any*): Unit = macro LoggingMacros.logImpl
  final def block[T](msg: String, args: Any*)(body: => T): T = macro LoggingMacros.blockImpl[T]
  final def func[T](body: => T): T = macro LoggingMacros.funcImpl[T]
}

object LoggingMacros {
  type Context = scala.reflect.macros.blackbox.Context

  def logImpl(c: Context)(msg: c.Expr[String], args: c.Expr[Any]*): c.Expr[Unit] = {
    InContext[c.type](c).logImpl(msg, args)
  }

  def blockImpl[T](c: Context)(msg: c.Expr[String], args: c.Expr[Any]*)(body: c.Expr[T])(implicit wk: c.WeakTypeTag[T]): c.Expr[T] = {
    InContext[c.type](c).blockImpl(msg, args, body)
  }

  def funcImpl[T](c: Context)(body: c.Expr[T])(implicit wk: c.WeakTypeTag[T]): c.Expr[T] = {
    InContext[c.type](c).funcImpl(body)
  }

  //noinspection SameParameterValue
  private final case class InContext[Ctx <: Context](c: Ctx) {
    import c.universe._

    private val traceLogFqn: Tree =
      q"_root_.org.jetbrains.plugins.scala.traceLogger.TraceLog"

    private val toDataFqn: Tree =
      q"_root_.org.jetbrains.plugins.scala.traceLogger.ToData"

    private val stackTrace: Tree =
      q"_root_.java.lang.Thread.currentThread().getStackTrace"

    private def toData(expr: Tree, ty: Type): Tree = {
      // we have to typecheck here, so the correct ToData is selected
      q"$toDataFqn[$ty]($expr)"
    }

    private def textOfExpr(v: Tree): String = {
      val fileContent = new String(v.pos.source.content)
      val start = v.collect {
        case treeVal => treeVal.pos match {
          case NoPosition => Int.MaxValue
          case p => p.start
        }
      }.min
      if (start == Int.MaxValue) {
        throw new Error(s"Cannot determine source code for $v")
      } else {
        val g = c.asInstanceOf[reflect.macros.runtime.Context].global
        val parser = g.newUnitParser(fileContent.drop(start))
        parser.expr()
        val end = parser.in.lastOffset
        fileContent.slice(start, start + end)
      }
    }

    private def wrapReturns[R](tree: Tree, f: Tree => Tree): Tree = {
      val transformer = new Transformer {
        override def transform(tree: Tree): Tree = {
          tree match {
            case Return(expr) =>
              // We have to type check the returned tree
              // because we insert it into an already typed tree.
              // Otherwise we will get compiler errors,
              // because the compiler will not typecheck trees
              // that have typechecked parents.
              val transformedExpr = c.typecheck(f(expr))
              treeCopy.Return(tree, transformedExpr)

            //skip local functions and classes
            case dd: DefDef @unchecked => dd
            case cd: ClassDef @unchecked => cd
            case md: ModuleDef @unchecked => md
            case _ => super.transform(tree)
          }
        }
      }
      transformer.transform(tree)
    }

    private def wrapResulting[R: WeakTypeTag](tree: Tree, f: Tree => Tree): c.Expr[R] =
      c.Expr[R](f(wrapReturns(tree, f)))

    private def enclosingOwner: c.Symbol = c.internal.enclosingOwner

    private def withOwners(startSymbol: c.Symbol): Iterator[c.Symbol] = new Iterator[c.Symbol] {
      private var cur = startSymbol
      override def hasNext: Boolean = cur != NoSymbol
      override def next(): c.Symbol = {
        val result = cur
        cur = cur.owner
        result
      }
    }

    private def enclosingMethod(start: Symbol): Option[MethodSymbol] =
      withOwners(start).collectFirst {
        case method if method.isMethod => method.asMethod
        case cls if cls.isClass => cls.asClass.primaryConstructor.asMethod
      }

    private def enclosingClass(start: Symbol): Option[ClassSymbol] =
      withOwners(start).collectFirst {
        case cls if cls.isClass => cls.asClass
      }

    private def inActiveIf[T: WeakTypeTag](inner: c.Expr[T]): c.Expr[T] = {
      c.Expr[T](
        q"""
          if ($traceLogFqn.isActiveInAtLeastOneThread) {
            $inner
          }
        """)
    }

    private def convertExprToData(v: Tree, ty: Type): c.Expr[_] =
      convertExprToData(textOfExpr(v), v, ty)

    private def convertExprToData(name: String, v: Tree, ty: Type): c.Expr[_] =
      c.Expr(q"""($name, ${toData(v, ty)})""")

    private def convertExpressionsToData(v: Seq[c.Expr[Any]]): c.Expr[Seq[_]] = {
      assert(v.forall(_.actualType != null))
      c.Expr(q"Seq(..${v.map(e => convertExprToData(e.tree, e.actualType))})")
    }

    private def enclosingStart(msg: c.Expr[String], data: c.Expr[Seq[_]]): c.Expr[Unit] =
      c.Expr[Unit](q"$traceLogFqn.inst.startEnclosing($msg, $data, $stackTrace)")

    private def success(result: Tree, ty: Type): c.Expr[Unit] =
      c.Expr[Unit](q"$traceLogFqn.inst.enclosingSuccess(${toData(result, ty)}, $stackTrace)")

    private def fail(e: Tree): c.Expr[Unit] =
      c.Expr[Unit](q"$traceLogFqn.inst.enclosingFail($e, $stackTrace)")

    private def enclosing[T](body: c.Expr[T])(implicit ttag: WeakTypeTag[T]): c.Expr[T] = {
      val resultType = ttag.tpe
      val wrappedBody = wrapResulting(body.tree, result => {
        val resultVar = q"${TermName(c.freshName("result"))}"
        q"""
          val $resultVar: $resultType = $result
          ${inActiveIf(success(resultVar, resultType))}
          $resultVar
         """
      })

      c.Expr[T](
        q"""
        try {
          $wrappedBody
        } catch {
          case e: scala.runtime.NonLocalReturnControl[_] =>
            ${inActiveIf(success(q"e.value.asInstanceOf[$resultType]", resultType))}
            throw e
          case e: scala.Throwable =>
            ${inActiveIf(fail(q"e"))}
            throw e
        }
        """
      )
    }

    def logImpl(msg: c.Expr[String], args: Seq[c.Expr[Any]]): c.Expr[Unit] = {
      val logExpr = c.Expr[Unit](
        q"$traceLogFqn.inst.log($msg, ${convertExpressionsToData(args)}, $stackTrace)"
      )

      inActiveIf(logExpr)
    }

    def blockImpl[T: WeakTypeTag](msg: c.Expr[String], args: Seq[c.Expr[Any]], body: c.Expr[T]): c.Expr[T] = {
      c.Expr[T](q"""
        ${inActiveIf(enclosingStart(msg, convertExpressionsToData(args)))}
        ${enclosing(body)}
       """)
    }

    def funcImpl[T: WeakTypeTag](body: c.Expr[T]): c.Expr[T] = {
      val owner = enclosingOwner
      if (!owner.isClass && !owner.isMethod) {
        c.abort(c.enclosingPosition, "use TraceLogger.func only at the start of methods or in class constructors")
      }
      val method = enclosingMethod(owner).get

      def convertUntypedTree(name: String, tree: Tree): c.Expr[_] = {
        val typed = c.typecheck(tree)
        convertExprToData(name, typed, typed.tpe)
      }

      val thisDesc = enclosingClass(owner) match {
        case Some(c) if !c.isPackageClass && !c.isModuleClass && !c.isImplementationArtifact && !c.isSynthetic =>
          Seq(convertExprToData("this", q"this", c.toType))
        case _ =>
          Seq.empty
      }

      val params = thisDesc ++ method.paramLists.flatten.map(param => convertUntypedTree(param.name.toString, q"$param"))
      val paramsSeq = c.Expr[Seq[_]](q"Seq(..$params)")
      val emptyMessage = c.Expr[String](q"null")
      c.Expr[T](q"""
        ${inActiveIf(enclosingStart(emptyMessage, paramsSeq))}
        ${enclosing(body)}
       """)
    }
  }
}