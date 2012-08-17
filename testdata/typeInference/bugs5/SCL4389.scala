object SCL4389 {
  object Keys {
    val libraryDependencies : SettingKey[Seq[ModuleID]] = null
    val scalaVersion : SettingKey[String] = null
  }

  class ModuleID

  object Build {

    import Keys._
    val x: String => ModuleID = null
    /*start*/libraryDependencies.a(scalaVersion(x))/*end*/
  }

  sealed trait SettingKey[T] extends Project./*Keyed*/Initialize[T] with Scoped.ListSetting[T, Types.Id] {

  }

  object Scoped {
    sealed trait ListSetting[S, M[_]] {
      def a[V](value : Project.Initialize[M[V]]) = 1

      def a(s: String) = false
    }
  }

  trait Init {
    /*trait KeyedInitialize[T] extends Init.this.Keyed[T, T] {
}

sealed trait Keyed[S, T] extends Init.this.Initialize[T]*/
    sealed trait Initialize[T] extends {
      def apply[S](g : T => S) : Init.this.Initialize[S] = null
    }
  }

  object Project extends Init {

  }

  object Types {
    type Id[X] = X
  }
}
//Int