package org

import com.{Id, Meters, helper, instance}

class A {
  def f(id: Id, m: Meters): Int = id + helper() + instance.length
}
