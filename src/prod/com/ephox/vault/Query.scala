package com.ephox.vault

trait Query {
  val sql: String
  val bindings: List[JDBCType]
}

trait Querys {
  def query(s: String, b: JDBCType*): Query = new Query {
    val sql = s
    val bindings = b.toList
  }
}