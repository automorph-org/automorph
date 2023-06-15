package test.api

import automorph.RpcResult
import automorph.spi.EffectSystem
import test.api

trait SimpleApi[Effect[_]] {

  def method(argument: String): Effect[String]
}

final case class SimpleApiImpl[Effect[_]](backend: EffectSystem[Effect]) extends SimpleApi[Effect] {

  override def method(argument: String): Effect[String] =
    backend.successful(argument)
}

trait ComplexApi[Effect[_], Context] {

  /** Test method. */
  def method0(): Effect[Unit]

  def method1(): Effect[Double]

  def method2(p0: String): Effect[Unit]

  def method3(p0: Float, p1: Long, p2: Option[List[Int]]): Effect[List[String]]

  def method4(p0: BigDecimal, p1: Byte, p2: Map[String, Int], p3: Option[String]): Effect[Long]

  def method5(p0: Boolean, p1: Short)(p2: List[Int]): Effect[Map[String, String]]

  def method6(p0: api.Record, p1: Double): Effect[Option[Int]]

  def method7(p0: api.Record, p1: Boolean)(implicit context: Context): Effect[api.Record]

  def method8(p0: api.Record, p1: String, p2: Option[Double]): Effect[RpcResult[String, Context]]

  def method9(p0: String): Effect[String]

  protected def protectedMethod(): Unit
}

final case class ComplexApiImpl[Effect[_], Context](backend: EffectSystem[Effect], defaultContext: Context)
  extends ComplexApi[Effect, Context] {

  override def method0(): Effect[Unit] =
    backend.successful {}

  override def method1(): Effect[Double] =
    backend.successful(1.2d)

  override def method2(p0: String): Effect[Unit] =
    backend.successful {}

  override def method3(p0: Float, p1: Long, p2: Option[List[Int]]): Effect[List[String]] =
    backend.successful(p2.getOrElse(List(0)).map(number => (p1 + p0 + number).toString))

  override def method4(p0: BigDecimal, p1: Byte, p2: Map[String, Int], p3: Option[String]): Effect[Long] =
    backend.successful(p0.sign.toLong + p1 + p2.values.sum + p3.map(_.length).getOrElse(0))

  override def method5(p0: Boolean, p1: Short)(p2: List[Int]): Effect[Map[String, String]] =
    backend.successful(Map("boolean" -> p0.toString, "float" -> p1.toString, "list" -> p2.mkString(", ")))

  override def method6(p0: api.Record, p1: Double): Effect[Option[Int]] =
    backend.successful(Some((p0.double + p1).toInt))

  override def method7(p0: api.Record, p1: Boolean)(implicit context: Context): Effect[api.Record] =
    backend.successful(p0.copy(
      string = s"${p0.string} - ${p0.long} - ${p0.double} - $p1 - ${context.getClass.getName}",
      double = if (p1) 1 else 0,
      enumeration = Enum.fromOrdinal(1),
    ))

  override def method8(p0: api.Record, p1: String, p2: Option[Double]): Effect[RpcResult[String, Context]] =
    backend.successful(p0.int match {
      case Some(int) => RpcResult(s"${int.toString} - $p1", defaultContext)
      case _ => RpcResult(s"${p2.getOrElse(0)}", defaultContext)
    })

  override def method9(p0: String): Effect[String] =
    backend.failed(new IllegalArgumentException(p0))

  protected def protectedMethod(): Unit =
    privateMethod()

  private def privateMethod(): Unit =
    ()
}

trait InvalidApi[Effect[_]] {

  def nomethod(p0: String): Effect[Unit]

  def method1(p0: String): Effect[Unit]

  def method2(p0: String): Effect[String]

  def method3(p0: Float, p1: Option[Long]): Effect[List[String]]

  def method4(p0: BigDecimal, p1: Option[Boolean], p2: Option[String]): Effect[String]

  def method5(p0: Boolean, p1: Short): Effect[String]
}
