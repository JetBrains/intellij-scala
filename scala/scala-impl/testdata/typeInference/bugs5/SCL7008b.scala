trait SCL7008b {
  trait N { self: F =>
    trait Name
  }
  trait SN { self: F =>
    object nme extends Z {

    }
    def viaNme(name: Name) = /*start*/nme.one(name)/*end*/
  }
  class F extends N with SN with NM

  trait NM { self: F =>
    trait NMC
    trait Z extends NMC { self: nme.type =>
      def one(name: Name): Name = null
    }
  }
}
//SN.this.Name
