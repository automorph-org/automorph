package test.core

import test.base.{Await, Network}

trait HttpClientServerTest extends HttpProtocolCodecTest with Await with Network {

  def port(fixtureId: Int): Int =
    port(s"${getClass.getName}-$fixtureId")
}
