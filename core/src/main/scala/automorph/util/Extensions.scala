package automorph.util

import automorph.spi.EffectSystem
import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import scala.util.{Failure, Success, Try}

/** Extension methods for utility types. */
private[automorph] object Extensions {

  implicit final class ThrowableOps(throwable: Throwable) {

    /**
     * Assemble detailed description of an exception and its causes.
     *
     * @return
     *   error messages
     */
    def description: String =
      trace.mkString("\n")

    /**
     * Assemble detailed trace of an exception and its causes.
     *
     * @return
     *   error messages
     */
    def trace: Seq[String] =
      trace()

    /**
     * Assemble detailed trace of an exception and its causes.
     *
     * @param maxCauses
     *   maximum number of included exception causes
     * @return
     *   error messages
     */
    def trace(maxCauses: Int = 100): Seq[String] =
      LazyList.iterate(Option(throwable))(_.flatMap(error => Option(error.getCause))).takeWhile(_.isDefined).flatten
        .take(maxCauses).map { throwable =>
          val exceptionName = throwable.getClass.getSimpleName
          val message = Option(throwable.getMessage).getOrElse("")
          s"[$exceptionName] $message"
        }
  }

  implicit final class ByteArrayOps(data: Array[Byte]) {

    /** Converts this byte array to input stream. */
    def toInputStream: InputStream =
      new ByteArrayInputStream(data)

    /** Converts this input stream to byte buffer. */
    def toByteBuffer: ByteBuffer =
      ByteBuffer.wrap(data)

    /** Converts this byte array to string using UTF-8 character encoding. */
    def asString: String =
      new String(data, charset)
  }

  implicit final class ByteBufferOps(data: ByteBuffer) {

    /** Converts this byte buffer to byte array. */
    def toByteArray: Array[Byte] =
      if (data.hasArray) {
        data.array
      } else {
        val array = Array.ofDim[Byte](data.remaining)
        data.get(array)
        array
      }
  }

  implicit final class InputStreamOps(data: InputStream) {

    /** Converts this input stream to byte array. */
    def toByteArray: Array[Byte] =
      try {
        data.readAllBytes()
      } finally {
        data.close()
      }
  }

  implicit final class StringOps(data: String) {

    /** Converts this string to byte array using UTF-8 character encoding. */
    def toByteArray: Array[Byte] =
      data.getBytes(charset)
  }

  implicit final class TryOps[T](tryValue: Try[T]) {

    /**
     * Invokes ''failure'' on `Failure` or returns this on `Success`.
     *
     * @param failure
     *   function to invoke if this is a `Failure`
     * @return
     *   the supplied `Try`
     */
    def onError(failure: Throwable => Unit): Try[T] =
      tryValue.recoverWith { case error =>
        failure(error)
        Failure(error)
      }

    /**
     * Invokes ''success'' on `Success` or returns this on `Success`.
     *
     * @param success
     *   function to invoke if this is a `Success`
     * @return
     *   the supplied `Try`
     */
    def success(success: T => Unit): Try[T] =
      tryValue.map { result =>
        success(result)
        result
      }

    /**
     * Applies ''failure'' on `Failure`.
     *
     * @param failure
     *   function to apply if this is a `Failure`
     * @return
     *   applied function result or success value
     */
    def foldError(failure: Throwable => T): T =
      tryValue match {
        case Failure(error) => failure(error)
        case Success(value) => value
      }
  }

  implicit final class EffectOps[Effect[_], T](effect: => Effect[T]) {

    /**
     * Creates a new effect by lifting an effect's errors into a value.
     *
     * The resulting effect cannot fail.
     *
     * @return
     *   effectful error or the original value
     */
    def either(implicit system: EffectSystem[Effect]): Effect[Either[Throwable, T]] =
      system.either(effect)

    /**
     * Creates a new effect by applying `failure` if an effect failed or `success` if an effect succeeded.
     *
     * @param failure
     *   function applied if the effect failed
     * @param success
     *   function applied if the effect succeded
     * @tparam R
     *   function result type
     * @return
     *   transformed effectful value
     */
    def fold[R](failure: Throwable => R, success: T => R)(implicit system: EffectSystem[Effect]): Effect[R] =
      system.fold(effect)(failure, success)

    /**
     * Creates a new effect by applying `failure` if an effect failed or `success` if an effect succeeded.
     *
     * @param failure
     *   effectful function applied if the effect failed
     * @param success
     *   effectful function applied if the effect succeded
     * @tparam R
     *   function result type
     * @return
     *   transformed effectful value
     */
    def flatFold[R](
      failure: Throwable => Effect[R],
      success: T => Effect[R],
    )(implicit system: EffectSystem[Effect]): Effect[R] =
      system.flatFold(effect)(failure, success)

    /**
     * Creates a new effect by applying a function to an effect's value.
     *
     * @param function
     *   function applied to the specified effect's value
     * @tparam R
     *   function result type
     * @return
     *   transformed effectful value
     */
    def map[R](function: T => R)(implicit system: EffectSystem[Effect]): Effect[R] =
      system.map(effect)(function)

    /**
     * Creates a new effect by applying an effectful function to an effect's value.
     *
     * @param function
     *   effectful function applied to the specified effect's value
     * @tparam R
     *   effectful function result type
     * @return
     *   effect containing the transformed value
     */
    def flatMap[R](function: T => Effect[R])(implicit system: EffectSystem[Effect]): Effect[R] =
      system.flatMap(effect)(function)

    /**
     * Executes an effect asynchronously without blocking and discard the result.
     *
     * @return
     *   nothing
     */
    def runAsync(implicit system: EffectSystem[Effect]): Unit =
      system.runAsync(effect)
  }

  /** String character set */
  private val charset: Charset = StandardCharsets.UTF_8
}
