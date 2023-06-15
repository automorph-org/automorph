package examples

import examples.metadata.{HttpAuthentication, HttpRequest, HttpResponse}
import examples.basic.{AsynchronousCall, OptionalParameters, SynchronousCall}
import examples.special.{ApiDiscovery, DynamicPayload, OneWayMessage, PositionalArguments}
import examples.customization.{ClientFunctionNames, DataSerialization, ServerFunctionNames}
import examples.errors.{ClientExceptions, HttpStatusCode, ServerErrors}
import examples.integration.{EffectSystem, MessageCodec, RpcProtocol}
import examples.transport.{AmqpTransport, ClientTransport, EndpointTransport, ServerTransport, WebSocketTransport}
import test.base.{BaseTest, Mutex}

class ExamplesTest extends BaseTest with Mutex {

  "" - {
    "Quickstart" in {
      runTest(Quickstart)
    }
    "Basic" - {
      Seq[Any](
        SynchronousCall,
        AsynchronousCall,
        OptionalParameters,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Customization" - {
      Seq[Any](
        DataSerialization,
        ClientFunctionNames,
        ServerFunctionNames,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Errors" - {
      Seq[Any](
        ClientExceptions,
        ServerErrors,
        HttpStatusCode,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Metadata" - {
      Seq[Any](
        HttpAuthentication,
        HttpRequest,
        HttpResponse,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Special" - {
      Seq[Any](
        ApiDiscovery,
        DynamicPayload,
        OneWayMessage,
        PositionalArguments,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Integration" - {
      Seq[Any](
        EffectSystem,
        MessageCodec,
        RpcProtocol,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
    }
    "Transport" - {
      Seq[Any](
        ClientTransport,
        ServerTransport,
        EndpointTransport,
        WebSocketTransport,
      ).foreach { instance =>
        testName(instance) in {
          runTest(instance)
        }
      }
      testName(AmqpTransport) in {
        lock()
        try {
          AmqpTransport.main(Array())
        } finally {
          unlock()
        }
      }
    }
  }

  private def testName(instance: Any): String = {
    val className = instance.getClass.getSimpleName
    className.substring(0, className.length - 1)
  }

  private def runTest(instance: Any): Unit =
    synchronized {
      val mainMethod = instance.getClass.getMethod("main", classOf[Array[String]])
      mainMethod.invoke(instance, Array[String]())
      ()
    }
}
