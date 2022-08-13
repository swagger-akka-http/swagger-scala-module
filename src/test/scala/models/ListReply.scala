package models

case class ListReply[T](items: List[T], total: Int, nextPageToken: Option[String], offset: Int, limit: Option[Int])
