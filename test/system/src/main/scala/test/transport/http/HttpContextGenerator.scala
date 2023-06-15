package test.transport.http

import automorph.transport.http.{HttpContext, HttpMethod}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.scalacheck.{Arbitrary, Gen}

case object HttpContextGenerator {

  private val charset = StandardCharsets.UTF_8
  private val methods = Seq(HttpMethod.Post)
  private val maxItems = 16
  private val maxNameSize = 16
  private val maxValueSize = 64

  private val header = for {
    name <- stringGenerator(1, maxNameSize, Gen.alphaNumChar)
    value <- stringGenerator(0, maxValueSize, Gen.alphaNumChar)
  } yield (name, value)

  private val parameter = for {
    name <- stringGenerator(1, maxNameSize, Gen.alphaNumChar).map { value =>
      URLEncoder.encode(value, charset)
    }
    value <- stringGenerator(0, maxValueSize, Gen.alphaNumChar).map { value =>
      URLEncoder.encode(value, charset)
    }
  } yield (name, value)

  def arbitrary[T]: Arbitrary[HttpContext[T]] =
    Arbitrary(for {
      method <- Gen.oneOf(methods)
      headers <- Gen.listOfN(maxItems, header)
      parameters <- Gen.listOfN(maxItems, parameter)
    } yield HttpContext(
      method = Some(method),
      headers = headers,
      parameters = parameters
    ))

  private def stringGenerator(minSize: Int, maxSize: Int, charGenerator: Gen[Char]): Gen[String] =
    Gen.choose(minSize, maxSize).flatMap(size => Gen.stringOfN(size, charGenerator))
}
