package org.example4.usage

// !!! NOTE:
// 1. wrong order of imports shouldn't be changed after refactoring
//   though modified import (of moved class) should be inserted to a "proper place"
// 2. unused imports shouldn't be removed during refactoring
//   We could do it (e.g. Java does it), but in Scala it might be quite a dangerous and unexpected operation,
//   taking into account the complexity of Scala imports
//

import org.example4.declaration.beta.BetaClass
import org.example4.declaration.data.A
import org.example4.declaration.eta.EtaClass
import org.example4.declaration.data.C
import org.example4.declaration.alpha.AlphaClass //NOTE: unused
import org.example4.declaration.data.B
import org.example4.declaration.X

class Usage_SortOnlyModifiedImport_DoNotTouchOther {

  def foo(
    x: X,
    a: A,
    b: B,
    c: C,
    //ac: AlphaClass,
    bc: BetaClass,
    cc: EtaClass,
  ): Unit ={

  }
}
