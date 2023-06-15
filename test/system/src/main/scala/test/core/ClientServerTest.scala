package test.core

import test.base.{Await, Network}

trait ClientServerTest extends ProtocolCodecTest with Await with Network {

  def port(fixtureId: Int): Int =
    port(s"${getClass.getName}-$fixtureId")
}
