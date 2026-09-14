package com.github.swagger.scala.converter

case class PagedReply[T](items: Seq[T], optT: Option[T], limit: Option[Int])
