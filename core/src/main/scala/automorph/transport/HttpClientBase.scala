package automorph.transport

import automorph.spi.{AsyncEffectSystem, EffectSystem}
import automorph.util.Extensions.EffectOps
import java.net.URI
import java.util.concurrent.CompletableFuture
import scala.util.Try

private[automorph] object HttpClientBase {

  def overrideUrl[TransportContext](url: URI, context: HttpContext[TransportContext]): URI = {
    val base = HttpContext[TransportContext]().url(url)
    val scheme = context.scheme.map(base.scheme).getOrElse(base)
    val authority = context.authority.map(scheme.authority).getOrElse(scheme)
    val path = context.path.map(authority.path).getOrElse(authority)
    val fragment = context.fragment.map(path.fragment).getOrElse(path)
    val query = fragment.parameters(context.parameters*)
    query.url.getOrElse(url)
  }

  def completableEffect[T, Effect[_]](
    future: => CompletableFuture[T],
    asyncSystem: AsyncEffectSystem[Effect],
  ): Effect[T] = {
    implicit val effectSystem: EffectSystem[Effect] = asyncSystem
    asyncSystem.completable[T].flatMap { completable =>
      Try(future).fold(
        exception => completable.fail(exception).runAsync,
        value => {
          value.handle { case (result, error) =>
            Option(result)
              .map(value => completable.succeed(value).runAsync)
              .getOrElse(completable.fail(
                Option(error).getOrElse(new IllegalStateException("Missing completable future result"))
              ).runAsync)
          }
          ()
        },
      )
      completable.effect
    }
  }
}
