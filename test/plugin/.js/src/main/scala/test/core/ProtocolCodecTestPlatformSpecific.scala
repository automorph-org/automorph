package test.core

trait ProtocolCodecTestPlatformSpecific {
  self: ProtocolCodecTest =>

  protected def platformSpecificFixtures(implicit context: Context): Seq[TestFixture] =
    Seq(
    )

}
