package com.repository

import scala.collection.concurrent.TrieMap

class InMemoryCacheRepository extends CacheRepository {
  private val store = TrieMap.empty[String, String]

  override def put(key: String, value: String): Unit = store.put(key, value)

  override def get(key: String): Option[String] = store.get(key)

  override def delete(key: String): Boolean = store.remove(key).isDefined
}
