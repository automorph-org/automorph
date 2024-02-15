package test.transport.amqp

import automorph.transport.amqp.AmqpContext
import java.time.Instant
import org.scalacheck.{Arbitrary, Gen}

object AmqpContextGenerator {

  private val maxItems = 16
  private val maxNameSize = 16
  private val maxValueSize = 64

  private val header = for {
    name <- stringGenerator(1, maxNameSize, Gen.alphaNumChar)
    value <- stringGenerator(0, maxValueSize, Gen.asciiPrintableChar)
  } yield (name, value)

  def arbitrary[T]: Arbitrary[AmqpContext[T]] =
    Arbitrary(
      for {
        headers <- Gen.mapOfN(maxItems, header)
        deliveryMode <- Gen.option(Gen.choose(1, 2))
        priority <- Gen.option(Gen.choose(0, 9))
        expiration <- Gen.option(Gen.choose(0, Int.MaxValue).map(_.toString))
        messageId <- Gen.option(stringGenerator(1, maxValueSize, Gen.alphaNumChar))
        timestamp <- Gen.option(Gen.choose(0L, Long.MaxValue).map(Instant.ofEpochMilli))
        `type` <- Gen.option(stringGenerator(1, maxValueSize, Gen.alphaNumChar))
        appId <- Gen.option(stringGenerator(1, maxValueSize, Gen.alphaNumChar))
      } yield AmqpContext(
        headers = headers,
        deliveryMode = deliveryMode,
        priority = priority,
        correlationId = None,
        expiration = expiration,
        messageId = messageId,
        timestamp = timestamp,
        `type` = `type`,
        userId = None,
        appId = appId,
      )
    )

  private def stringGenerator(minSize: Int, maxSize: Int, charGenerator: Gen[Char]): Gen[String] =
    Gen.choose(minSize, maxSize).flatMap(size => Gen.stringOfN(size, charGenerator))
}
