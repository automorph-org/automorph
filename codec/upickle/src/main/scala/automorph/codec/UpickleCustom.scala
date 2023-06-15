package automorph.codec

import upickle.AttributeTagged
import upickle.core.{Abort, ByteUtils, CharUtils, ParseUtils, Visitor}

/**
 * uPickle message codec customization.
 *
 * Provides basic null-safe data types support.
 */
trait UpickleCustom extends AttributeTagged {

  implicit override def OptionWriter[T: Writer]: Writer[Option[T]] =
    new Writer.MapWriter(
      implicitly[Writer[T]],
      {
        case Some(value) => value
        case None => null.asInstanceOf[T]
      },
    )

  implicit override def OptionReader[T: Reader]: Reader[Option[T]] =
    new Reader.MapReader[T, T, Option[T]](implicitly[Reader[T]]) {

      override def mapNonNullsFunction(value: T): Option[T] =
        Some(value)

      override def visitNull(index: Int): Option[T] =
        None
    }

  implicit override val UnitWriter: Writer[Unit] = new Writer[Unit] {
    def write0[R](out: Visitor[?, R], v: Unit): R =
      out.visitObject(0, jsonableKeys = true, -1).visitEnd(-1)
  }

  implicit override val BooleanReader: Reader[Boolean] = new SimpleReader[Boolean] {

    override def expectedMsg =
      "expected boolean"

    override def visitTrue(index: Int) =
      true

    override def visitFalse(index: Int) =
      false

    override def visitString(s: CharSequence, index: Int): Boolean =
      s.toString.toBoolean

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val DoubleReader: Reader[Double] = new NumericReader[Double] {

    override def expectedMsg =
      "expected number"

    override def visitString(s: CharSequence, index: Int): Double =
      visitFloat64String(s.toString, index)

    override def visitInt32(d: Int, index: Int): Double =
      d.toDouble

    override def visitInt64(d: Long, index: Int): Double =
      d.toDouble

    override def visitUInt64(d: Long, index: Int): Double =
      d.toDouble

    override def visitFloat64(d: Double, index: Int): Double =
      d

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Double =
      s.toString.toDouble

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val IntReader: Reader[Int] = new NumericReader[Int] {

    override def expectedMsg =
      "expected number"

    override def visitString(s: CharSequence, index: Int): Int =
      visitFloat64String(s.toString, index)

    override def visitInt32(d: Int, index: Int): Int =
      d

    override def visitInt64(d: Long, index: Int): Int =
      d.toInt

    override def visitUInt64(d: Long, index: Int): Int =
      d.toInt

    override def visitFloat64(d: Double, index: Int): Int =
      d.toInt

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Int =
      ParseUtils.parseIntegralNum(s, decIndex, expIndex, index).toInt

    override def visitFloat64ByteParts(
      s: Array[Byte], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Int =
      ByteUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex).toInt

    override def visitFloat64CharParts(
      s: Array[Char], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Int =
      CharUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex).toInt

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val FloatReader: Reader[Float] = new NumericReader[Float] {

    override def expectedMsg =
      "expected number"

    override def visitString(s: CharSequence, index: Int): Float =
      visitFloat64String(s.toString, index)

    override def visitInt32(d: Int, index: Int): Float =
      d.toFloat

    override def visitInt64(d: Long, index: Int): Float =
      d.toFloat

    override def visitUInt64(d: Long, index: Int): Float =
      d.toFloat

    override def visitFloat32(d: Float, index: Int): Float =
      d

    override def visitFloat64(d: Double, index: Int): Float =
      d.toFloat

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Float =
      s.toString.toFloat

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val ShortReader: Reader[Short] = new NumericReader[Short] {

    override def expectedMsg =
      "expected number"

    override def visitString(s: CharSequence, index: Int): Short =
      visitFloat64String(s.toString, index)

    override def visitInt32(d: Int, index: Int): Short =
      d.toShort

    override def visitInt64(d: Long, index: Int): Short =
      d.toShort

    override def visitUInt64(d: Long, index: Int): Short =
      d.toShort

    override def visitFloat64(d: Double, index: Int): Short =
      d.toShort

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Short =
      ParseUtils.parseIntegralNum(s, decIndex, expIndex, index).toShort

    override def visitFloat64ByteParts(
      s: Array[Byte], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Short =
      ByteUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex).toShort

    override def visitFloat64CharParts(
      s: Array[Char], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Short =
      CharUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex).toShort

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val ByteReader: Reader[Byte] = new NumericReader[Byte] {

    override def expectedMsg =
      "expected number"

    override def visitString(s: CharSequence, index: Int): Byte =
      visitFloat64String(s.toString, index)

    override def visitInt32(d: Int, index: Int): Byte =
      d.toByte

    override def visitInt64(d: Long, index: Int): Byte =
      d.toByte

    override def visitUInt64(d: Long, index: Int): Byte =
      d.toByte

    override def visitFloat64(d: Double, index: Int): Byte =
      d.toByte

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Byte =
      ParseUtils.parseIntegralNum(s, decIndex, expIndex, index).toByte

    override def visitFloat64ByteParts(
      s: Array[Byte], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Byte =
      ByteUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex).toByte

    override def visitFloat64CharParts(
      s: Array[Char], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Byte =
      CharUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex).toByte

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val CharReader: Reader[Char] = new NumericReader[Char] {

    override def expectedMsg =
      "expected char"

    override def visitString(d: CharSequence, index: Int): Char =
      d.toString.charAt(0)

    override def visitChar(d: Char, index: Int): Char =
      d

    override def visitInt32(d: Int, index: Int): Char =
      d.toChar

    override def visitInt64(d: Long, index: Int): Char =
      d.toChar

    override def visitUInt64(d: Long, index: Int): Char =
      d.toChar

    override def visitFloat64(d: Double, index: Int): Char =
      d.toChar

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Char =
      ParseUtils.parseIntegralNum(s, decIndex, expIndex, index).toChar

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }

  implicit override val LongReader: Reader[Long] = new NumericReader[Long] {

    override def expectedMsg =
      "expected number"

    override def visitString(d: CharSequence, index: Int): Long =
      visitFloat64String(d.toString, index)

    override def visitInt32(d: Int, index: Int): Long =
      d.toLong

    override def visitInt64(d: Long, index: Int): Long =
      d

    override def visitUInt64(d: Long, index: Int): Long =
      d

    override def visitFloat64(d: Double, index: Int): Long =
      d.toLong

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Long =
      ParseUtils.parseIntegralNum(s, decIndex, expIndex, index)

    override def visitFloat64ByteParts(
      s: Array[Byte], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Long =
      ByteUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex)

    override def visitFloat64CharParts(
      s: Array[Char], arrOffset: Int, arrLength: Int, decIndex: Int, expIndex: Int, index: Int
    ): Long =
      CharUtils.parseIntegralNum(s, arrOffset, arrLength, decIndex, expIndex)

    override def visitNull(index: Int) =
      throw Abort(s"$expectedMsg got null")
  }
}
