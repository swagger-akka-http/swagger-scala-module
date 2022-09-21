package com.github.swagger.scala.converter

import co.blocke.scala_reflection.RType

import scala.collection.concurrent.TrieMap

object RTypeCache {
  private val trieMap = TrieMap[Class[_], RType]()

  def getRType(cls: Class[_]): RType = trieMap.getOrElseUpdate(cls, RType.of(cls))

  def remove(cls: Class[_]): Option[RType] = trieMap.remove(cls)

  def clear(): Unit = trieMap.clear()
}
