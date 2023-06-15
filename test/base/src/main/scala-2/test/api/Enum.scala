package test.api

case object Enum extends Enumeration {
  type Enum = Value
  val Zero, One = Value

  def fromOrdinal(ordinal: Int): Enum =
    this.values.toSeq(ordinal)

  def toOrdinal(value: Enum): Int =
    value.id
}
