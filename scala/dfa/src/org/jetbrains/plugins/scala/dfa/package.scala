package org.jetbrains.plugins.scala

package object dfa extends lattice.HasTopOps
  with lattice.HasBottomOps
  with lattice.SemiLatticeOps
  with lattice.JoinSemiLatticeOps
  with lattice.MeetSemiLatticeOps