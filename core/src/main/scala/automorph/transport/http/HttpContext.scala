package automorph.transport.http

import automorph.RpcException.{FunctionNotFound, InvalidRequest, ServerError}
import automorph.transport.http.HttpContext.SetCookie
import java.io.IOException
import java.net.URI
import scala.concurrent.duration.Duration

/**
 * HTTP transport message context.
 *
 * Message transport plugins must use message context properties in the descending order of priority by source:
 *   - This context
 *   - Message properties for specific transport plugin (this.message)
 *   - Default values
 *
 * @see
 *   [[https://datatracker.ietf.org/doc/html/rfc7232 HTTP specification]]
 * @param scheme
 *   request URL scheme
 * @param userInfo
 *   request URL authority user information
 * @param host
 *   request URL authority host
 * @param port
 *   request URL authority port
 * @param path
 *   request URL path
 * @param fragment
 *   request URL fragment
 * @param headers
 *   request or response headers
 * @param method
 *   request method
 * @param statusCode
 *   response status code
 * @param followRedirects
 *   automatically follow redirects if true
 * @param timeout
 *   response timeout
 * @param transportContext
 *   message properties for specific transport plugin
 * @tparam TransportContext
 *   type of context for specific transport plugin
 */
final case class HttpContext[TransportContext](
  scheme: Option[String] = None,
  userInfo: Option[String] = None,
  host: Option[String] = None,
  port: Option[Int] = None,
  path: Option[String] = None,
  parameters: Seq[(String, String)] = Seq.empty,
  fragment: Option[String] = None,
  headers: Seq[(String, String)] = Seq.empty,
  method: Option[HttpMethod] = None,
  statusCode: Option[Int] = None,
  followRedirects: Option[Boolean] = None,
  timeout: Option[Duration] = None,
  transportContext: Option[TransportContext] = None,
) {

  private val headerAuthorization = "Authorization"
  private val headerContentLength = "Content-Length"
  private val headerContentType = "Content-Type"
  private val headerCookie = "Cookie"
  private val headerProxyAuthorization = "Proxy-Authorization"
  private val headerSetCookie = "Set-Cookie"

  /** Request URL. */
  def url: Option[URI] =
    (scheme, authority, path, query, fragment) match {
      case (Some(scheme), Some(authority), Some(path), query, fragment) =>
        Some(new URI(scheme, authority, path, query.orNull, fragment.orNull))
      case _ => None
    }

  /**
   * Set request URL.
   *
   * @param url
   *   URL
   * @return
   *   HTTP message context
   */
  def url(url: String): HttpContext[TransportContext] =
    this.url(new URI(url))

  /**
   * Set request URL.
   *
   * @param url
   *   URL
   * @return
   *   HTTP message context
   */
  def url(url: URI): HttpContext[TransportContext] = {
    val httpContext = copy(
      scheme = Option(url.getScheme),
      userInfo = Option(url.getUserInfo),
      host = Option(url.getHost),
      port = Option.when(url.getPort >= 0)(url.getPort),
      path = Option(url.getPath),
      fragment = Option(url.getFragment),
    )
    Option(url.getQuery).map(httpContext.query).getOrElse(httpContext)
  }

  /**
   * Set request URL scheme.
   *
   * @param scheme
   *   URL scheme
   * @return
   *   HTTP message context
   */
  def scheme(scheme: String): HttpContext[TransportContext] =
    copy(scheme = Some(scheme))

  /** Request URL authority. */
  def authority: Option[String] =
    host.map { host =>
      val userInfoText = userInfo.map(userInfo => s"$userInfo@").getOrElse("")
      val portText = port.map(port => s":$port").getOrElse("")
      s"$userInfoText$host$portText"
    }

  /**
   * Set request URL authority.
   *
   * @param authority
   *   URL authority
   * @return
   *   HTTP message context
   */
  def authority(authority: String): HttpContext[TransportContext] = {
    val (userInfo, endpoint) = authority.split("@", 2) match {
      case Array(userInfo, endpoint) => Some(userInfo) -> Some(endpoint)
      case Array(endpoint) => None -> Some(endpoint)
      case _ => None -> None
    }
    val (host, port) = endpoint.map(_.split(":", 2) match {
      case Array(host, port) => Some(host) -> Some(port.toInt)
      case Array(host) => Some(host) -> None
      case _ => None -> None
    }).getOrElse(None -> None)
    copy(userInfo = userInfo, host = host, port = port)
  }

  /**
   * Set request URL user information.
   *
   * @param userInfo
   *   URL user information
   * @return
   *   HTTP message context
   */
  def userInfo(userInfo: String): HttpContext[TransportContext] =
    copy(userInfo = Some(userInfo))

  /**
   * Set request URL host.
   *
   * @param host
   *   URL host
   * @return
   *   HTTP message context
   */
  def host(host: String): HttpContext[TransportContext] =
    copy(host = Some(host))

  /**
   * Set request URL port.
   *
   * @param port
   *   URL port
   * @return
   *   HTTP message context
   */
  def port(port: Int): HttpContext[TransportContext] =
    copy(port = Some(port))

  /**
   * Set request URL user information.
   *
   * @param path
   *   URL userinfo
   * @return
   *   HTTP message context
   */
  def path(path: String): HttpContext[TransportContext] =
    copy(path = Some(path))

  /** Request URL query. */
  def query: Option[String] =
    parameters match {
      case Seq() => None
      case _ => Some(s"${parameters.map { case (name, value) => s"$name=$value" }.mkString("&")}")
    }

  /**
   * Set request URL query string.
   *
   * @param queryString
   *   URL query string
   * @return
   *   HTTP message context
   */
  def query(queryString: String): HttpContext[TransportContext] = {
    val entries = queryString.replaceFirst("^\\?(.*)$", "$1")
    val parameters = entries.split("&").flatMap(_.split("=", 2) match {
      case Array(name, value) if name.nonEmpty => Some((name, value))
      case Array(name) if name.nonEmpty => Some((name, ""))
      case _ => None
    }).toSeq
    copy(parameters = parameters)
  }

  /**
   * First URL query parameter value.
   *
   * @param name
   *   query parameter name
   * @return
   *   first query parameter value
   */
  def parameter(name: String): Option[String] =
    parameters.find(_._1 == name).map(_._2)

  /**
   * URL query parameter values.
   *
   * @param name
   *   query parameter name
   * @return
   *   query parameter values
   */
  def parameters(name: String): Seq[String] =
    parameters.filter(_._1 == name).map(_._2)

  /**
   * Add URL query parameter.
   *
   * @param name
   *   parameter name
   * @param value
   *   parameter value
   * @return
   *   HTTP message context
   */
  def parameter(name: String, value: String): HttpContext[TransportContext] =
    parameter(name, value, replace = false)

  /**
   * Add or replace URL query parameter.
   *
   * @param name
   *   query parameter name
   * @param value
   *   query parameter value
   * @param replace
   *   replace all existing query parameters with the specified name
   * @return
   *   HTTP message context
   */
  def parameter(name: String, value: String, replace: Boolean): HttpContext[TransportContext] =
    copy(parameters = updateEntries(parameters, Seq(name -> value), replace))

  /**
   * Add URL query parameters.
   *
   * @param entries
   *   query parameter names and values
   * @return
   *   HTTP message context
   */
  def parameters(entries: (String, String)*): HttpContext[TransportContext] =
    parameters(entries, replace = false)

  /**
   * Add or replace URL query parameters.
   *
   * @param entries
   *   query parameter names and values
   * @param replace
   *   replace all existing query parameters with specified names
   * @return
   *   HTTP message context
   */
  def parameters(entries: Iterable[(String, String)], replace: Boolean): HttpContext[TransportContext] =
    copy(parameters = updateEntries(parameters, entries, replace))

  /**
   * Set request URL fragment.
   *
   * @param fragment
   *   URL fragment
   * @return
   *   HTTP message context
   */
  def fragment(fragment: String): HttpContext[TransportContext] =
    copy(fragment = Some(fragment))

  /**
   * Set request method.
   *
   * @param method
   *   request method
   * @return
   *   HTTP message context
   */
  def method(method: HttpMethod): HttpContext[TransportContext] =
    copy(method = Some(method))

  /**
   * First header value.
   *
   * @param name
   *   header name
   * @return
   *   first header value
   */
  def header(name: String): Option[String] =
    headers.find(_._1 == name).map(_._2)

  /**
   * Add message headers.
   *
   * @param entries
   *   header names and values
   * @return
   *   HTTP message context
   */
  def headers(entries: (String, String)*): HttpContext[TransportContext] =
    headers(entries, replace = false)

  /**
   * Add message header.
   *
   * @param name
   *   header name
   * @param value
   *   header value
   * @return
   *   HTTP message context
   */
  def header(name: String, value: String): HttpContext[TransportContext] =
    header(name, value, replace = false)

  /**
   * Add or replace message header.
   *
   * @param name
   *   header name
   * @param value
   *   header value
   * @param replace
   *   replace all existing headers with the specified name
   * @return
   *   HTTP message context
   */
  def header(name: String, value: String, replace: Boolean): HttpContext[TransportContext] =
    copy(headers = updateEntries(headers, Seq(name -> value), replace))

  /**
   * Header values.
   *
   * @param name
   *   header name
   * @return
   *   header values
   */
  def headers(name: String): Seq[String] =
    headers.filter(_._1 == name).map(_._2)

  /**
   * Add or replace message headers.
   *
   * @param entries
   *   header names and values
   * @param replace
   *   replace all existing headers with specified names
   * @return
   *   HTTP message context
   */
  def headers(entries: Iterable[(String, String)], replace: Boolean): HttpContext[TransportContext] =
    copy(headers = updateEntries(headers, entries, replace))

  /**
   * Set response status code.
   *
   * @param statusCode
   *   status code
   * @return
   *   HTTP message context
   */
  def statusCode(statusCode: Int): HttpContext[TransportContext] =
    copy(statusCode = Some(statusCode))

  /** `Content-Type` header value. */
  def contentType: Option[String] =
    header(headerContentType)

  /** `Content-Length` header value. */
  def contentLength: Option[String] =
    header(headerContentLength)

  /**
   * Cookie value.
   *
   * @param name
   *   cookie name
   * @return
   *   cookie value
   */
  def cookie(name: String): Option[String] =
    cookies.get(name)

  /** Cookie names and values. */
  def cookies: Map[String, String] =
    headers(headerCookie).flatMap { header =>
      header.split(";").flatMap { cookie =>
        cookie.split("=", 2).map(_.trim) match {
          case Array(name, value) => Some(name -> value)
          case _ => None
        }
      }
    }.toMap

  /**
   * Set request cookies.
   *
   * @param entries
   *   cookie names and values
   * @return
   *   HTTP message context
   */
  def cookies(entries: (String, String)*): HttpContext[TransportContext] = {
    val headerValue = entries.map { case (name, value) => s"$name=$value" }.mkString("; ")
    header(headerCookie, headerValue, replace = true)
  }

  /**
   * Set-Cookie value.
   *
   * @param name
   *   set cookie name
   * @return
   *   set cookie value
   */
  def setCookie(name: String): Option[SetCookie] =
    setCookies.get(name)

  /** Set-Cookie names and values. */
  def setCookies: Map[String, SetCookie] =
    headers(headerSetCookie).flatMap { header =>
      header.split(";") match {
        case Array(cookie, attributes*) => cookie.split("=", 2).map(_.trim) match {
          case Array(name, value) => Some(name -> SetCookie(value, attributes))
          case _ => None
        }
        case _ => None
      }
    }.toMap

  /**
   * Set response cookies.
   *
   * @param entries
   *   set cookie names and values
   * @return
   *   HTTP message context
   */
  def setCookies(entries: (String, SetCookie)*): HttpContext[TransportContext] = {
    val headerValues = entries.map { case (name, value) =>
      val attributes = value.attributes.map(attribute => s"; $attribute").mkString
      s"$name=${value.value}$attributes"
    }
    headers(headerValues.map((headerSetCookie, _)), replace = true)
  }

  /**
   * `Authorization` header value.
   *
   * @param scheme
   *   authentication scheme
   * @return
   *   authentication credentials
   */
  def authorization(scheme: String): Option[String] =
    authorizationFromHeader(headerAuthorization, scheme)

  /**
   * Set `Authorization` header value.
   *
   * @param scheme
   *   authentication scheme
   * @param credentials
   *   authentication credentials
   * @return
   *   HTTP message context
   */
  def authorization(scheme: String, credentials: String): HttpContext[TransportContext] =
    authorizationToHeader(headerAuthorization, scheme, credentials)

  /**
   * `Proxy-Authorization` header value.
   *
   * @param scheme
   *   authentication scheme
   * @return
   *   authentication credentials
   */
  def proxyAuthorization(scheme: String): Option[String] =
    authorizationFromHeader(headerProxyAuthorization, scheme)

  /**
   * Set `Proxy-Authorization` header value.
   *
   * @param scheme
   *   authentication scheme
   * @param credentials
   *   authentication credentials
   * @return
   *   HTTP message context
   */
  def proxyAuthorization(scheme: String, credentials: String): HttpContext[TransportContext] =
    authorizationToHeader(headerProxyAuthorization, scheme, credentials)

  private def updateEntries(
    originalEntries: Iterable[(String, String)],
    entries: Iterable[(String, String)],
    replace: Boolean,
  ): Seq[(String, String)] = {
    val entryNames = entries.map { case (name, _) =>
      name
    }.toSet
    val retainedEntries = if (replace) {
      originalEntries.filter { case (name, _) => !entryNames.contains(name) }
    } else originalEntries
    retainedEntries.toSeq ++ entries
  }

  private def authorizationFromHeader(header: String, scheme: String): Option[String] =
    headers(header).find(_.startsWith(s"$scheme ")).flatMap(_.trim.split("\\s+") match {
      case Array(_, value) => Some(value)
      case _ => None
    })

  private def authorizationToHeader(
    headerName: String,
    scheme: String,
    credentials: String,
  ): HttpContext[TransportContext] =
    header(headerName, s"$scheme $credentials", replace = true)

  private[automorph] def overrideUrl(url: URI): URI = {
    val base = HttpContext().url(url)
    val scheme = this.scheme.map(base.scheme).getOrElse(base)
    val authority = this.authority.map(scheme.authority).getOrElse(scheme)
    val path = this.path.map(authority.path).getOrElse(authority)
    val fragment = this.fragment.map(path.fragment).getOrElse(path)
    val query = fragment.parameters(parameters*)
    query.url.getOrElse(url)
  }
}

object HttpContext {

  /**
   * Set-Cookie value.
   *
   * @param value cookie value
   * @param attributes cookie attributes
   */
  final case class SetCookie(value: String, attributes: Seq[String] = Seq.empty)

  private val exceptionToStatusCode: Map[Class[?], Int] = Map[Class[?], Int](
    classOf[InvalidRequest] -> 400,
    classOf[IllegalArgumentException] -> 400,
    classOf[FunctionNotFound] -> 501,
    classOf[ServerError] -> 500,
    classOf[IOException] -> 500,
  ).withDefaultValue(500)

  /**
   * Maps an exception to a corresponding default HTTP status code.
   *
   * @param exception
   *   exception class
   * @return
   *   HTTP status code
   */
  def toStatusCode(exception: Throwable): Int =
    exceptionToStatusCode(exception.getClass)
}
