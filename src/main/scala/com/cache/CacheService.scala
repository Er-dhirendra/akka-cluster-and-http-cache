package com.cache

import akka.actor.typed.receptionist.ServiceKey

object CacheService {
  val key: ServiceKey[CacheActor.Command] =
    ServiceKey[CacheActor.Command]("cache-service")
}

