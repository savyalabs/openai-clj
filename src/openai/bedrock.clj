(ns openai.bedrock
  "Blocking Amazon Bedrock client backed by the optional OpenAI Java transport.

  Add the `:bedrock` alias when requiring this namespace. AWS credentials may
  be supplied explicitly or discovered by the upstream AWS credential chain."
  (:import (com.openai.bedrock BedrockEndpoint)
           (com.openai.client OpenAIClient)
           (com.openai.client.okhttp BedrockOpenAIOkHttpClient
                                      BedrockOpenAIOkHttpClient$Builder)
           (java.time Duration)
           (java.util.function Supplier)))

(set! *warn-on-reflection* true)

(defn- endpoint ^BedrockEndpoint [endpoint]
  (BedrockEndpoint/valueOf (.toUpperCase (name endpoint))))

(defn- token-provider ^Supplier [provider]
  (if (fn? provider)
    (reify Supplier
      (get [_] (provider)))
    provider))

(defn- configure-builder ^BedrockOpenAIOkHttpClient$Builder
  [^BedrockOpenAIOkHttpClient$Builder builder
   {:keys [endpoint aws-region base-url api-key bedrock-token-provider
           aws-access-key-id aws-secret-access-key aws-session-token aws-profile
           aws-credentials-provider skip-auth timeout-ms max-retries]}]
  (when endpoint (.endpoint builder (openai.bedrock/endpoint endpoint)))
  (when aws-region (.awsRegion builder ^String aws-region))
  (when base-url (.baseUrl builder ^String base-url))
  (when api-key (.apiKey builder ^String api-key))
  (when bedrock-token-provider
    (.bedrockTokenProvider builder (token-provider bedrock-token-provider)))
  (when aws-access-key-id (.awsAccessKeyId builder ^String aws-access-key-id))
  (when aws-secret-access-key
    (.awsSecretAccessKey builder ^String aws-secret-access-key))
  (when aws-session-token (.awsSessionToken builder ^String aws-session-token))
  (when aws-profile (.awsProfile builder ^String aws-profile))
  (when aws-credentials-provider (.awsCredentialsProvider builder aws-credentials-provider))
  (when (some? skip-auth) (.skipAuth builder (boolean skip-auth)))
  (when timeout-ms (.timeout builder (Duration/ofMillis (long timeout-ms))))
  (when max-retries (.maxRetries builder (int max-retries)))
  builder)

(defn client
  "Create a blocking Bedrock `OpenAIClient`.

  With no arguments, credentials and configuration are read from the
  environment by the upstream client. The option map supports `:endpoint`
  (`:mantle` or `:runtime`), `:aws-region`, `:base-url`, `:api-key`,
  `:bedrock-token-provider`, `:aws-access-key-id`, `:aws-secret-access-key`,
  `:aws-session-token`, `:aws-profile`, `:aws-credentials-provider`,
  `:skip-auth`, `:timeout-ms`, and `:max-retries`. A token provider may be a
  zero-argument Clojure function or a `java.util.function.Supplier`."
  (^OpenAIClient [] (BedrockOpenAIOkHttpClient/fromEnv))
  (^OpenAIClient [opts]
   (-> (BedrockOpenAIOkHttpClient/builder)
       (configure-builder opts)
       (.build))))
