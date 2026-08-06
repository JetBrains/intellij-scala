trait SCL7008 {
  trait N { self: F =>
    trait Name
  }
  trait SN { self: F =>
    object nme extends Z {

    }
  }
  class F extends N with SN with NM

  trait NM { self: F =>
    trait NMC
    trait Z extends NMC { self: nme.type =>
      def one(name: Name): Name = null
      def two(name: Name) = /*start*/one(name)/*end*/
    }
  }
}
//NM.this.Name