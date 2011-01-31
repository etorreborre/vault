package com.ephox.vault2

import scalaz._
import Scalaz._
import java.sql._

sealed trait Connector[A] {
  val connect: Connection => SQLValue[A]

  import Connector._

  def apply(c: Connection) = connect(c)

  def bracket[B, C](after: (=> A) => Connector[B], k: (=> A) => Connector[C]): Connector[C] =
    this >>= (a => try {
      k(a)
    } finally {
      after(a)
    })

  def finaly[B](b: => Connector[B]): Connector[A] =
    connector(c => try {
      apply(c)
    } finally {
      b(c)
    })

  def finalyClose: Connector[A] =
    finaly(close)

  /**
   * Commits the connection and if this fails with an exception then rollback the connection.
   *
   * If the failure is an `SQLException` then this is returned in the `SQLValue`, otherwise, the exception is rethrown.
   */
  def commitRollback: Connector[A] =
    connector(c => try {
      val r = connect(c)
      c.commit
      r
    } catch {
      case ex: SQLException => {
        c.rollback
        sqlErr(ex)
      }
      case ex => {
        c.rollback
        throw ex
      }
    })
}

object Connector {
  def connector[A](f: Connection => SQLValue[A]): Connector[A] = new Connector[A] {
    val connect = f
  }

  implicit def ConnectorFunctor: Functor[Connector] = new Functor[Connector] {
    def fmap[A, B](k: Connector[A], f: A => B) =
      connector((c: Connection) => k(c) map f)
  }

  implicit def ConnectorPure[M[_]]: Pure[Connector] = new Pure[Connector] {
    def pure[A](a: => A) =
      valueConnector(_ => a)
  }

  implicit def ConnectorApply[M[_]]: Apply[Connector] = new Apply[Connector] {
    def apply[A, B](f: Connector[A => B], a: Connector[A]) = {
      import SQLValue._
      connector(c => a(c) <*> f(c))
    }
  }

  implicit def ConnectorBind[M[_]]: Bind[Connector] = new Bind[Connector] {
    def bind[A, B](a: Connector[A], f: A => Connector[B]) =
      connector(c => a(c) >>= (a => f(a)(c)))
  }
}
