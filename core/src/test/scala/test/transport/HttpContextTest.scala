package test.transport

import automorph.transport.HttpContext.SetCookie
import automorph.transport.{HttpContext, HttpMethod}
import java.net.URI
import scala.collection.immutable.Seq
import test.base.BaseTest
import scala.concurrent.duration.Duration

final class HttpContextTest extends BaseTest {
  private val url = new URI("test://test:test@test:0/test?a=b&c=d#test")
  private val userInfo = "test:test"
  private val scheme = "test"
  private val authority = "test:test@test:0"
  private val host = "test"
  private val port = 0
  private val path = "/test"
  private val query = "a=b&c=d"
  private val parameters = Seq("a" -> "b", "c" -> "d")
  private val fragment = "test"
  private val method = HttpMethod.Get
  private val headers = Seq("a" -> "b", "c" -> "d")
  private val statusCode = 0
  private val followRedirects = true
  private val timeout = Duration.Zero
  private val transportContext = 0
  private val contentType = "test/test"
  private val contentLength = "0"
  private val cookies = Seq("a" -> "b", "c" -> "d")
  private val setCookies = Seq("a" -> SetCookie("b"), "c" -> SetCookie("d"))
  private val authorizationScheme = "Bearer"
  private val authorizationCredentials = "test"

  "" - {
    "URL" in {
      HttpContext().url.shouldBe(empty)
      HttpContext().url(url.toString).url.value.shouldBe(url)
      HttpContext().url(url).url.value.shouldBe(url)
      HttpContext().url(url).scheme.value.shouldBe(scheme)
      HttpContext().url(url).userInfo.value.shouldBe(userInfo)
      HttpContext().url(url).host.value.shouldBe(host)
      HttpContext().url(url).port.value.shouldBe(port)
      HttpContext().url(url).path.value.shouldBe(path)
      HttpContext().url(url).parameters.shouldBe(parameters)
      HttpContext().url(url).fragment.value.shouldBe(fragment)
      HttpContext(
        scheme = Some(scheme),
        userInfo = Some(userInfo),
        host = Some(host),
        port = Some(port),
        path = Some(path),
        parameters = parameters,
        fragment = Some(fragment),
      ).url.value.shouldBe(url)
    }
    "Scheme" in {
      HttpContext().scheme.shouldBe(empty)
      HttpContext().scheme(scheme).scheme.value.shouldBe(scheme)
      HttpContext(scheme = Some(scheme)).scheme.value.shouldBe(scheme)
    }
    "Authority" in {
      HttpContext().authority.shouldBe(empty)
      HttpContext().authority(authority).authority.value.shouldBe(authority)
      HttpContext().authority(authority).userInfo.value.shouldBe(userInfo)
      HttpContext().authority(authority).host.value.shouldBe(host)
      HttpContext().authority(authority).port.value.shouldBe(port)
      HttpContext(host = Some(host)).authority.value.shouldBe(host)
      HttpContext(host = Some(host), port = Some(port)).authority.value.shouldBe("test:0")
      HttpContext(userInfo = Some(userInfo), host = Some(host)).authority.value.shouldBe("test:test@test")
      HttpContext(userInfo = Some(userInfo), host = Some(host), port = Some(port)).authority.value.shouldBe(
        authority
      )
    }
    "User Info" in {
      HttpContext().userInfo.shouldBe(empty)
      HttpContext().userInfo(userInfo).userInfo.value.shouldBe(userInfo)
      HttpContext(userInfo = Some(userInfo)).userInfo.value.shouldBe(userInfo)
    }
    "Host" in {
      HttpContext().host.shouldBe(empty)
      HttpContext().host(host).host.value.shouldBe(host)
      HttpContext(host = Some(host)).host.value.shouldBe(host)
    }
    "Port" in {
      HttpContext().port.shouldBe(empty)
      HttpContext().port(port).port.value.shouldBe(port)
      HttpContext(port = Some(port)).port.value.shouldBe(port)
    }
    "Path" in {
      HttpContext().path.shouldBe(empty)
      HttpContext().path(path).path.value.shouldBe(path)
      HttpContext(path = Some(path)).path.value.shouldBe(path)
    }
    "Query" in {
      HttpContext().query.shouldBe(empty)
      HttpContext().query(query).query.value.shouldBe(query)
      HttpContext().query(query).parameters.shouldBe(parameters)
      HttpContext(parameters = parameters).query.value.shouldBe(query)
    }
    "Parameters" in {
      HttpContext().parameters.shouldBe(empty)
      HttpContext().parameters(parameters*).parameters.shouldBe(parameters)
      HttpContext().parameters(parameters, replace = false).parameters(parameters, replace = false).parameters.shouldBe(
        parameters ++ parameters
      )
      HttpContext().parameters(parameters, replace = true).parameters(parameters, replace = true).parameters.shouldBe(
        parameters
      )
      HttpContext().parameters(parameters*).parameter("a").value.shouldBe("b")
      HttpContext().parameters(parameters*).parameters("a").shouldBe(Seq("b"))
      HttpContext().parameters(parameters*).parameter("c").value.shouldBe("d")
      HttpContext().parameters(parameters*).parameters("c").shouldBe(Seq("d"))
      HttpContext().parameter("a", "b").parameters.shouldBe(Seq("a" -> "b"))
      HttpContext().parameter("a", "b").parameter("a", "b", replace = false).parameters.shouldBe(Seq(
        "a" -> "b",
        "a" -> "b",
      ))
      HttpContext().parameter("a", "b").parameter("a", "b", replace = true).parameters.shouldBe(Seq("a" -> "b"))
      HttpContext().parameter("a", "b").parameter("a", "b", replace = true).parameters("a").shouldBe(Seq("b"))
      HttpContext(parameters = parameters).parameters.shouldBe(parameters)
    }
    "Fragment" in {
      HttpContext().fragment.shouldBe(empty)
      HttpContext().fragment(fragment).fragment.value.shouldBe(fragment)
      HttpContext(fragment = Some(fragment)).fragment.value.shouldBe(fragment)
    }
    "Method" in {
      HttpContext().method.shouldBe(empty)
      HttpContext().method(method).method.value.shouldBe(method)
      HttpContext(method = Some(method)).method.value.shouldBe(method)
    }
    "Headers" in {
      HttpContext().headers.shouldBe(empty)
      HttpContext().headers(headers*).headers.shouldBe(headers)
      HttpContext().headers(headers, replace = false).headers(headers, replace = false).headers.shouldBe(
        headers ++ headers
      )
      HttpContext().headers(headers, replace = true).headers(headers, replace = true).headers.shouldBe(
        headers
      )
      HttpContext().headers(headers*).header("a").value.shouldBe("b")
      HttpContext().headers(headers*).headers("a").shouldBe(Seq("b"))
      HttpContext().headers(headers*).header("c").value.shouldBe("d")
      HttpContext().headers(headers*).headers("c").shouldBe(Seq("d"))
      HttpContext().header("a", "b").headers.shouldBe(Seq("a" -> "b"))
      HttpContext().header("a", "b").header("a", "b", replace = false).headers.shouldBe(Seq(
        "a" -> "b",
        "a" -> "b",
      ))
      HttpContext().header("a", "b").header("a", "b", replace = true).headers.shouldBe(Seq("a" -> "b"))
      HttpContext().header("a", "b").header("a", "b", replace = true).headers("a").shouldBe(Seq("b"))
      HttpContext(headers = headers).headers.shouldBe(headers)
    }
    "Status Code" in {
      HttpContext().statusCode.shouldBe(empty)
      HttpContext().statusCode(statusCode).statusCode.value.shouldBe(statusCode)
      HttpContext(statusCode = Some(statusCode)).statusCode.value.shouldBe(statusCode)
    }
    "Follow Redirects" in {
      HttpContext().followRedirects.shouldBe(empty)
      HttpContext().followRedirects(followRedirects).followRedirects.value.shouldBe(followRedirects)
      HttpContext(followRedirects = Some(followRedirects)).followRedirects.value.shouldBe(followRedirects)
    }
    "Timeout" in {
      HttpContext().timeout.shouldBe(empty)
      HttpContext().timeout(timeout).timeout.value.shouldBe(timeout)
      HttpContext(timeout = Some(timeout)).timeout.value.shouldBe(timeout)
    }
    "Transport Context" in {
      HttpContext[Int]().transportContext.shouldBe(empty)
      HttpContext[Int]().transportContext(transportContext).transportContext.value.shouldBe(transportContext)
      HttpContext[Int](transportContext = Some(transportContext)).transportContext.value.shouldBe(transportContext)
    }
    "Content Type" in {
      HttpContext().contentType.shouldBe(empty)
      HttpContext().header("Content-Type", contentType).contentType.value.shouldBe(contentType)
    }
    "Content Length" in {
      HttpContext().contentLength.shouldBe(empty)
      HttpContext().header("Content-Length", contentLength).contentLength.value.shouldBe(contentLength)
    }
    "Cookies" in {
      HttpContext().cookies.shouldBe(Map.empty)
      HttpContext().cookies(cookies*).cookie("a").value.shouldBe("b")
      HttpContext().cookies(cookies*).cookie("c").value.shouldBe("d")
      HttpContext().cookies(cookies*).cookies.shouldBe(cookies.toMap)
    }
    "Set Cookies" in {
      HttpContext().setCookies.shouldBe(Map.empty)
      HttpContext().setCookies(setCookies*).setCookie("a").value.shouldBe(SetCookie("b"))
      HttpContext().setCookies(setCookies*).setCookie("c").value.shouldBe(SetCookie("d"))
      HttpContext().setCookies(setCookies*).setCookies.shouldBe(setCookies.toMap)
    }
    "Authorization" in {
      HttpContext().authorization(authorizationScheme).shouldBe(empty)
      HttpContext().authorization(authorizationScheme, authorizationCredentials).authorization(
        authorizationScheme
      ).value.shouldBe(authorizationCredentials)
      HttpContext().authorization(authorizationScheme, authorizationCredentials).header("Authorization").value.shouldBe(
        s"$authorizationScheme $authorizationCredentials"
      )
    }
    "Proxy Authorization" in {
      HttpContext().proxyAuthorization(authorizationScheme).shouldBe(empty)
      HttpContext().proxyAuthorization(authorizationScheme, authorizationCredentials).proxyAuthorization(
        authorizationScheme
      ).value.shouldBe(authorizationCredentials)
      HttpContext().proxyAuthorization(authorizationScheme, authorizationCredentials).header(
        "Proxy-Authorization"
      ).value.shouldBe(
        s"$authorizationScheme $authorizationCredentials"
      )
    }
  }
}
