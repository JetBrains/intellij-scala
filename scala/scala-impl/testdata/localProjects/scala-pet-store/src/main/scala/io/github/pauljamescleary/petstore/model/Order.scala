package io.github.pauljamescleary.petstore.model

import java.time.Instant

case class Order(
    petId: Long,
    shipDate: Option[Instant] = None,
    status: OrderStatus = Placed,
    complete: Boolean = false,
    id: Option[Long] = None
)
