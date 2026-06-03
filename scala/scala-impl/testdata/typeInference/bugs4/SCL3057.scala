val a1 = if (true) 1 else 1d
val a2 = if (true) 1d else 1
val b1 = try 1d catch { case _ => 1 }
val b2 = try 1 catch { case _ => 1d }
val c1 = 0 match { case a => 1d; case b => 1 }
val c2 = 0 match { case a => 1; case b => 1d }
/*start*/((a1, a2), (b1, b2), (c1, c2))/*end*/

//((Double, Double), (Double, Double), (Double, Double))