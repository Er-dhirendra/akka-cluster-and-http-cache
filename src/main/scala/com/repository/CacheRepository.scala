package com.repository

trait CacheRepository {
  def put(key: String, value: String): Unit
  def get(key: String): Option[String]
  def delete(key: String): Boolean
}