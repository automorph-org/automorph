package test.api

import org.scalacheck.Arbitrary.{arbBool, arbByte, arbDouble, arbFloat, arbInt, arbLong, arbOption, arbShort, arbString, arbitrary}
import org.scalacheck.{Arbitrary, Gen}
import test.api.Enum.Enum

case object Generators {

  implicit val arbitraryEnum: Arbitrary[Enum] = Arbitrary(Gen.choose(0, Enum.values.size - 1).map(Enum.fromOrdinal))

  implicit val arbitraryStructure: Arbitrary[Structure] = Arbitrary(for {
    value <- Gen.asciiPrintableStr
  } yield Structure(value))

  implicit val arbitraryRecord: Arbitrary[Record] = {
    Arbitrary(for {
      string <- Gen.asciiPrintableStr
      boolean <- arbitrary[Boolean]
      byte <- arbitrary[Byte]
      short <- arbitrary[Short]
      int <- arbitrary[Option[Int]]
      long <- arbitrary[Long]
      float <- arbitrary[Float]
      double <- arbitrary[Double]
      enumeration <- arbitrary[Enum]
      list <- Gen.listOf(Gen.asciiPrintableStr)
      map <- Gen.mapOf(Gen.zip(Gen.asciiPrintableStr, arbitrary[Int]))
      structure <- arbitrary[Option[Structure]]
      none <- arbitrary[Option[String]]
    } yield Record(string, boolean, byte, short, int, long, float, double, enumeration, list, map, structure, none))
  }
}
