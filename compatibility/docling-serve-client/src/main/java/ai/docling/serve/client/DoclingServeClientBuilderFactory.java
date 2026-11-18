package ai.docling.serve.client;

import ai.docling.serve.client.DoclingServeClient.DoclingServeClientBuilder;

/**
 * A factory class for creating instances of {@link DoclingServeClientBuilder}, using Jackson 2 on Java 8.
 *
 * @deprecated Use {@link ai.docling.serve.api.DoclingServeApi#builder()} for the options shared by every
 *             implementation, or {@link DoclingServeClient#builder()} for client-specific settings such as the
 *             HTTP client. To customize the JSON mapper, use {@link DoclingServeJackson2Client#builder()}.
 */
@Deprecated(since = "0.7.0", forRemoval = true)
public final class DoclingServeClientBuilderFactory {
  private DoclingServeClientBuilderFactory() {
  }

  /**
   * Creates a builder for the Jackson 2 client using the supplied classloader.
   *
   * @param <C>         the type of {@link DoclingServeClient} to be created by the builder
   * @param <B>         the type of {@link DoclingServeClientBuilder} to be returned
   * @param classLoader the {@link ClassLoader} used to check for Jackson's presence
   * @return a compatible {@link DoclingServeClientBuilder} instance
   * @throws IllegalStateException if Jackson 2 is unavailable
   * @deprecated Use {@link DoclingServeClient#builder()} instead.
   */
  @Deprecated(since = "0.7.0", forRemoval = true)
  @SuppressWarnings("unchecked")
  public static <C extends DoclingServeClient, B extends DoclingServeClientBuilder<C, B>> B newBuilder(ClassLoader classLoader) {
    return (B) DoclingServeClient.builderFor(classLoader);
  }

  /**
   * Creates a builder for the Jackson 2 client using the context classloader.
   *
   * @param <C> the type of {@link DoclingServeClient} to be created by the builder
   * @param <B> the type of {@link DoclingServeClientBuilder} to be returned
   * @return a compatible {@link DoclingServeClientBuilder} instance
   * @throws IllegalStateException if Jackson 2 is unavailable
   * @deprecated Use {@link DoclingServeClient#builder()} instead.
   */
  @Deprecated(since = "0.7.0", forRemoval = true)
  public static <C extends DoclingServeClient, B extends DoclingServeClientBuilder<C, B>> B newBuilder() {
    return newBuilder(Thread.currentThread().getContextClassLoader());
  }
}
